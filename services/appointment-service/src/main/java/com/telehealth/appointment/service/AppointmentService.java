package com.telehealth.appointment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telehealth.appointment.dto.*;
import com.telehealth.appointment.entity.*;
import com.telehealth.appointment.repository.*;
import com.telehealth.contracts.enums.AppointmentStatus;
import com.telehealth.contracts.enums.ClaimKind;
import com.telehealth.contracts.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;
    private final SlotClaimRepository slotClaimRepository;
    private final DoctorRepository doctorRepository;
    private final SpecialtyRepository specialtyRepository;
    private final RescheduleHistoryRepository rescheduleHistoryRepository;
    private final IdempotencyRequestRepository idempotencyRequestRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long HOLD_DURATION_MINUTES = 7;
    private static final long DEFAULT_AMOUNT_VND = 100000L;

    @Transactional
    public AppointmentResponse holdAppointment(UUID patientId, BookAppointmentRequest request, String idempotencyKey) {
        // 1. Idempotency check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRequestEntity> existingReq = idempotencyRequestRepository
                    .findByPatientIdAndRequestKey(patientId, idempotencyKey);
            if (existingReq.isPresent() && existingReq.get().getAppointmentId() != null) {
                AppointmentEntity existingApp = appointmentRepository.findById(existingReq.get().getAppointmentId())
                        .orElse(null);
                if (existingApp != null) {
                    checkAndExpireIfNeeded(existingApp);
                    return mapToResponse(existingApp);
                }
            }
        }

        // 2. Validate doctor
        DoctorEntity doctor = doctorRepository.findByIdWithLock(request.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Bác sĩ không tồn tại"));

        if (!doctor.isActive()) {
            throw new IllegalStateException("Bác sĩ đã bị vô hiệu hóa");
        }

        // 3. Lock Slot row with PESSIMISTIC_WRITE
        SlotEntity slot = slotRepository.findByIdWithLock(request.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Ca khám không tồn tại"));

        if (!slot.isEnabled()) {
            throw new IllegalStateException("Ca khám hiện đang tạm khóa");
        }

        if (!slot.getDoctorId().equals(doctor.getId())) {
            throw new IllegalArgumentException("Ca khám không thuộc về bác sĩ được chọn");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // 4. Check existing claim on slot
        Optional<SlotClaimEntity> existingClaim = slotClaimRepository.findById(slot.getId());
        if (existingClaim.isPresent()) {
            SlotClaimEntity claim = existingClaim.get();
            if (claim.getKind() == ClaimKind.BOOKED) {
                throw new IllegalStateException("Ca khám đã được người khác đặt trước đó");
            }
            if (claim.getKind() == ClaimKind.HOLD) {
                if (claim.getExpiresAt() != null && now.isBefore(claim.getExpiresAt())) {
                    throw new IllegalStateException("Ca khám đang được một bệnh nhân khác giữ trong thời hạn thanh toán");
                } else {
                    // Expired hold: release previous claim
                    log.info("Slot {} had expired claim from appointment {}, releasing it", slot.getId(), claim.getAppointmentId());
                    appointmentRepository.findById(claim.getAppointmentId()).ifPresent(oldApp -> {
                        if (oldApp.getStatus() == AppointmentStatus.PENDING_PAYMENT) {
                            oldApp.setStatus(AppointmentStatus.EXPIRED);
                            appointmentRepository.save(oldApp);
                        }
                    });
                    slotClaimRepository.delete(claim);
                    slotClaimRepository.flush();
                }
            }
        }

        // 5. Verify intake not used by active appointment
        Optional<AppointmentEntity> activeIntakeApp = appointmentRepository.findByIntakeId(request.getIntakeId());
        if (activeIntakeApp.isPresent() && activeIntakeApp.get().getStatus() != AppointmentStatus.EXPIRED) {
            throw new IllegalStateException("Biểu mẫu khám này đã được liên kết với một lịch hẹn khác còn hiệu lực");
        }

        // 6. Create Appointment (PENDING_PAYMENT, 7 minutes hold)
        Instant expiresAt = now.plus(HOLD_DURATION_MINUTES, ChronoUnit.MINUTES);
        UUID appointmentId = UUID.randomUUID();

        AppointmentEntity appointment = AppointmentEntity.builder()
                .id(appointmentId)
                .demo(isDemoPatient(patientId))
                .patientId(patientId)
                .doctorId(doctor.getId())
                .slotId(slot.getId())
                .intakeId(request.getIntakeId())
                .status(AppointmentStatus.PENDING_PAYMENT)
                .amountVnd(DEFAULT_AMOUNT_VND)
                .currency("VND")
                .createdAt(now)
                .expiresAt(expiresAt)
                .version(1)
                .build();

        appointment = appointmentRepository.save(appointment);

        // 7. Create SlotClaim (HOLD)
        SlotClaimEntity claim = SlotClaimEntity.builder()
                .slotId(slot.getId())
                .appointmentId(appointment.getId())
                .kind(ClaimKind.HOLD)
                .expiresAt(expiresAt)
                .build();

        slotClaimRepository.save(claim);

        // 8. Record idempotency if key provided
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IdempotencyRequestEntity idemEntity = IdempotencyRequestEntity.builder()
                    .patientId(patientId)
                    .requestKey(idempotencyKey)
                    .requestHash(request.toString())
                    .appointmentId(appointment.getId())
                    .createdAt(now)
                    .build();
            idempotencyRequestRepository.save(idemEntity);
        }

        // 9. Publish outbox event
        saveOutboxEvent("AppointmentHeld", appointment.getId(), 1,
                AppointmentHeldEvent.builder()
                        .appointmentId(appointment.getId())
                        .patientId(patientId)
                        .doctorId(doctor.getId())
                        .slotId(slot.getId())
                        .intakeId(appointment.getIntakeId())
                        .amountVnd(appointment.getAmountVnd())
                        .startsAt(slot.getStartsAt())
                        .expiresAt(expiresAt)
                        .build());

        log.info("Appointment {} created in PENDING_PAYMENT, slot {} held until {}", appointmentId, slot.getId(), expiresAt);
        return mapToResponse(appointment);
    }

    @Transactional
    public AppointmentResponse getAppointmentById(UUID appointmentId, UUID currentUserId, String role) {
        AppointmentEntity appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn"));

        checkAndExpireIfNeeded(appointment);

        // Check permission
        boolean isPatient = appointment.getPatientId().equals(currentUserId);
        boolean isDoctor = doctorRepository.findById(appointment.getDoctorId())
                .map(d -> d.getUserId().equals(currentUserId))
                .orElse(false);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);

        if (!isPatient && !isDoctor && !isAdmin) {
            throw new IllegalStateException("Bạn không có quyền truy cập lịch hẹn này");
        }

        return mapToResponse(appointment);
    }

    @Transactional(readOnly = true)
    public RoomAppointmentContext getRoomContext(UUID appointmentId) {
        AppointmentEntity appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn"));
        DoctorEntity doctor = doctorRepository.findById(appointment.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bác sĩ"));
        SlotEntity slot = slotRepository.findById(appointment.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ca khám"));
        return new RoomAppointmentContext(appointment.getId(), appointment.getPatientId(), doctor.getId(),
                doctor.getUserId(), slot.getStartsAt(), appointment.getStatus().name());
    }

    private boolean isDemoPatient(UUID patientId) {
        return UUID.fromString("33333333-3333-3333-3333-333333333333").equals(patientId)
                || UUID.fromString("44444444-4444-4444-4444-444444444444").equals(patientId);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getDemoAppointments() {
        return appointmentRepository.findByDemoTrueOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse).toList();
    }

    @Transactional
    public void deleteDemoAppointment(UUID appointmentId, UUID adminId) {
        AppointmentEntity appointment = appointmentRepository.findByIdWithLock(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch khám demo"));
        // The demo marker alone must not turn a real patient's booking into deletable test data.
        if (!appointment.isDemo() || !isDemoPatient(appointment.getPatientId())) {
            throw new IllegalStateException("Chỉ được xóa lịch khám của tài khoản demo. Lịch khám thực tế được bảo vệ.");
        }
        slotClaimRepository.deleteByAppointmentId(appointmentId);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setDeletedAt(Instant.now());
        appointment.setDeletedBy(adminId);
        appointmentRepository.save(appointment);
        log.info("Admin {} removed demo appointment {}", adminId, appointmentId);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsForPatient(UUID patientId) {
        List<AppointmentEntity> list = appointmentRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
        list.forEach(this::checkAndExpireIfNeeded);
        return list.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsForDoctor(UUID doctorUserId) {
        DoctorEntity doctor = doctorRepository.findByUserId(doctorUserId)
                .orElseThrow(() -> new IllegalArgumentException("Bác sĩ không tồn tại với tài khoản hiện tại"));

        List<AppointmentEntity> list = appointmentRepository.findByDoctorIdOrderByCreatedAtDesc(doctor.getId());
        list.forEach(this::checkAndExpireIfNeeded);
        return list.stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void confirmAppointment(UUID appointmentId, Long amountVnd, String paymentCode) {
        AppointmentEntity appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn để xác nhận"));

        if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            log.info("Appointment {} is already CONFIRMED, skipping", appointmentId);
            return;
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Lịch hẹn không ở trạng thái chờ thanh toán");
        }

        Instant now = Instant.now();
        if (now.isAfter(appointment.getExpiresAt())) {
            appointment.setStatus(AppointmentStatus.EXPIRED);
            slotClaimRepository.deleteByAppointmentId(appointmentId);
            appointmentRepository.save(appointment);
            throw new IllegalStateException("Giao dịch đã quá hạn 7 phút, lịch hẹn đã tự động giải phóng");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setConfirmedAt(now);
        appointment = appointmentRepository.save(appointment);

        // Upgrade SlotClaim from HOLD to BOOKED
        SlotClaimEntity claim = slotClaimRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy quyền giữ ca"));
        claim.setKind(ClaimKind.BOOKED);
        claim.setExpiresAt(null);
        slotClaimRepository.save(claim);

        DoctorEntity doctor = doctorRepository.findById(appointment.getDoctorId()).orElse(null);
        SlotEntity slot = slotRepository.findById(appointment.getSlotId()).orElse(null);

        saveOutboxEvent("AppointmentConfirmed", appointment.getId(), appointment.getVersion(),
                AppointmentConfirmedEvent.builder()
                        .appointmentId(appointment.getId())
                        .patientId(appointment.getPatientId())
                        .doctorId(appointment.getDoctorId())
                        .doctorName(doctor != null ? doctor.getDisplayName() : "Bác sĩ")
                        .slotId(appointment.getSlotId())
                        .startsAt(slot != null ? slot.getStartsAt() : null)
                        .confirmedAt(now)
                        .amountVnd(appointment.getAmountVnd())
                        .build());

        log.info("Appointment {} confirmed successfully", appointmentId);
    }

    @Transactional
    public AppointmentResponse reschedule(UUID appointmentId, UUID patientId, UUID newSlotId) {
        AppointmentEntity appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn"));

        if (!appointment.getPatientId().equals(patientId)) {
            throw new IllegalStateException("Chỉ bệnh nhân chủ lịch mới được phép đổi ca");
        }

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Chỉ có thể đổi ca cho lịch hẹn đã xác nhận (CONFIRMED)");
        }

        SlotEntity oldSlot = slotRepository.findById(appointment.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Ca khám cũ không tồn tại"));

        // 1. Timezone check: Must be strictly before appointment date in Asia/Ho_Chi_Minh
        LocalDate todayVn = LocalDate.now(VN_ZONE);
        LocalDate oldSlotDateVn = oldSlot.getStartsAt().atZone(VN_ZONE).toLocalDate();

        if (!todayVn.isBefore(oldSlotDateVn)) {
            throw new IllegalArgumentException("Chỉ được đổi lịch trước ngày khám theo giờ Việt Nam. Đã khóa đổi lịch từ 00:00 ngày khám.");
        }

        if (appointment.getSlotId().equals(newSlotId)) {
            throw new IllegalArgumentException("Ca khám mới phải khác ca khám hiện tại");
        }

        // 2. Lock new slot and validate
        SlotEntity newSlot = slotRepository.findByIdWithLock(newSlotId)
                .orElseThrow(() -> new IllegalArgumentException("Ca khám mới không tồn tại"));

        if (!newSlot.getDoctorId().equals(appointment.getDoctorId())) {
            throw new IllegalArgumentException("Chỉ được đổi ca của cùng bác sĩ, không được đổi bác sĩ");
        }

        if (!newSlot.isEnabled()) {
            throw new IllegalStateException("Ca khám mới hiện đang tạm khóa");
        }

        LocalDate newSlotDateVn = newSlot.getStartsAt().atZone(VN_ZONE).toLocalDate();
        if (!newSlotDateVn.isAfter(todayVn)) {
            throw new IllegalArgumentException("Ca mới phải ở ngày sau ngày hiện tại");
        }

        // 3. Check new slot availability
        Optional<SlotClaimEntity> newSlotClaim = slotClaimRepository.findById(newSlot.getId());
        if (newSlotClaim.isPresent()) {
            SlotClaimEntity nsc = newSlotClaim.get();
            if (nsc.getKind() == ClaimKind.BOOKED || (nsc.getExpiresAt() != null && Instant.now().isBefore(nsc.getExpiresAt()))) {
                throw new IllegalStateException("Ca khám mới đã có người khác giữ hoặc đặt");
            } else {
                slotClaimRepository.delete(nsc);
                slotClaimRepository.flush();
            }
        }

        // 4. Atomic swap
        slotClaimRepository.deleteByAppointmentId(appointment.getId());
        slotClaimRepository.flush();

        SlotClaimEntity newClaim = SlotClaimEntity.builder()
                .slotId(newSlot.getId())
                .appointmentId(appointment.getId())
                .kind(ClaimKind.BOOKED)
                .expiresAt(null)
                .build();
        slotClaimRepository.save(newClaim);

        appointment.setSlotId(newSlot.getId());
        appointment.setVersion(appointment.getVersion() + 1);
        appointment = appointmentRepository.save(appointment);

        // 5. Save history
        RescheduleHistoryEntity history = RescheduleHistoryEntity.builder()
                .id(UUID.randomUUID())
                .appointmentId(appointment.getId())
                .oldSlotId(oldSlot.getId())
                .newSlotId(newSlot.getId())
                .oldStartsAt(oldSlot.getStartsAt())
                .newStartsAt(newSlot.getStartsAt())
                .actorId(patientId)
                .appointmentVersion(appointment.getVersion())
                .build();
        rescheduleHistoryRepository.save(history);

        // 6. Outbox event
        DoctorEntity doctor = doctorRepository.findById(appointment.getDoctorId()).orElse(null);
        saveOutboxEvent("AppointmentRescheduled", appointment.getId(), appointment.getVersion(),
                AppointmentRescheduledEvent.builder()
                        .appointmentId(appointment.getId())
                        .patientId(patientId)
                        .doctorId(appointment.getDoctorId())
                        .doctorName(doctor != null ? doctor.getDisplayName() : "Bác sĩ")
                        .oldSlotId(oldSlot.getId())
                        .newSlotId(newSlot.getId())
                        .oldStartsAt(oldSlot.getStartsAt())
                        .newStartsAt(newSlot.getStartsAt())
                        .changedAt(Instant.now())
                        .build());

        log.info("Appointment {} successfully rescheduled to slot {}", appointmentId, newSlotId);
        return mapToResponse(appointment);
    }

    @Transactional
    public AppointmentResponse startConsultation(UUID appointmentId, UUID doctorUserId) {
        AppointmentEntity appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn"));

        DoctorEntity doctor = doctorRepository.findByIdWithLock(appointment.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Bác sĩ không tồn tại"));

        if (!doctor.getUserId().equals(doctorUserId)) {
            throw new IllegalStateException("Chỉ bác sĩ được phân công mới có thể bắt đầu ca khám");
        }

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED && appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new IllegalStateException("Lịch hẹn chưa được xác nhận thanh toán");
        }

        // Rule: Only 1 IN_PROGRESS consultation per doctor
        boolean hasOtherInProgress = appointmentRepository.existsByDoctorIdAndStatus(doctor.getId(), AppointmentStatus.IN_PROGRESS);
        if (hasOtherInProgress && appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new IllegalStateException("Bác sĩ đang có một ca khám khác đang diễn ra. Vui lòng hoàn tất ca khám trước.");
        }

        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            appointment.setStatus(AppointmentStatus.IN_PROGRESS);
            appointment.setStartedAt(Instant.now());
            appointment = appointmentRepository.save(appointment);
        }

        return mapToResponse(appointment);
    }

    @Transactional
    public AppointmentResponse completeConsultation(UUID appointmentId, UUID doctorUserId) {
        AppointmentEntity appointment = appointmentRepository.findByIdWithLock(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn"));

        DoctorEntity doctor = doctorRepository.findById(appointment.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Bác sĩ không tồn tại"));

        if (!doctor.getUserId().equals(doctorUserId)) {
            throw new IllegalStateException("Chỉ bác sĩ được phân công mới có thể hoàn tất ca khám");
        }

        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            requestRoomClosure(appointment);
            return mapToResponse(appointment);
        }

        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new IllegalStateException("Chỉ có thể hoàn tất ca khám đang diễn ra");
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setCompletedAt(Instant.now());
        appointment = appointmentRepository.saveAndFlush(appointment);

        requestRoomClosure(appointment);

        log.info("Appointment {} completed by doctor {}", appointmentId, doctor.getId());
        return mapToResponse(appointment);
    }

    private void requestRoomClosure(AppointmentEntity appointment) {
        if (outboxEventRepository.existsByEventTypeAndAggregateId(RoomClosureDelivery.EVENT_TYPE, appointment.getId())) return;
        UUID eventId = UUID.randomUUID();
        outboxEventRepository.save(OutboxEventEntity.builder()
                .eventId(eventId)
                .eventType(RoomClosureDelivery.EVENT_TYPE)
                .aggregateId(appointment.getId())
                .aggregateVersion(appointment.getVersion())
                .payload("{\"appointmentId\":\"" + appointment.getId() + "\"}")
                .occurredAt(Instant.now())
                .nextAttemptAt(Instant.now())
                .build());
        applicationEventPublisher.publishEvent(new RoomClosureRequested(eventId));
    }

    private void checkAndExpireIfNeeded(AppointmentEntity appointment) {
        if (appointment.getStatus() == AppointmentStatus.PENDING_PAYMENT) {
            if (Instant.now().isAfter(appointment.getExpiresAt())) {
                log.info("Expiring pending appointment {} due to 7-min timeout", appointment.getId());
                appointment.setStatus(AppointmentStatus.EXPIRED);
                slotClaimRepository.deleteByAppointmentId(appointment.getId());
                appointmentRepository.save(appointment);
            }
        }
    }

    public AppointmentResponse mapToResponse(AppointmentEntity appointment) {
        DoctorEntity doctor = doctorRepository.findById(appointment.getDoctorId()).orElse(null);
        String specialtyName = null;
        if (doctor != null) {
            specialtyName = specialtyRepository.findById(doctor.getSpecialtyId())
                    .map(SpecialtyEntity::getName)
                    .orElse("Đa khoa");
        }

        SlotEntity slot = slotRepository.findById(appointment.getSlotId()).orElse(null);

        long remainingSeconds = 0;
        if (appointment.getStatus() == AppointmentStatus.PENDING_PAYMENT) {
            remainingSeconds = Math.max(0, Duration.between(Instant.now(), appointment.getExpiresAt()).toSeconds());
        }

        boolean canReschedule = false;
        if (appointment.getStatus() == AppointmentStatus.CONFIRMED && slot != null) {
            LocalDate todayVn = LocalDate.now(VN_ZONE);
            LocalDate slotDateVn = slot.getStartsAt().atZone(VN_ZONE).toLocalDate();
            canReschedule = todayVn.isBefore(slotDateVn);
        }

        return AppointmentResponse.builder()
                .id(appointment.getId())
                .patientId(appointment.getPatientId())
                .doctorId(appointment.getDoctorId())
                .doctorName(doctor != null ? doctor.getDisplayName() : "Bác sĩ")
                .specialtyName(specialtyName)
                .slotId(appointment.getSlotId())
                .startsAt(slot != null ? slot.getStartsAt() : null)
                .intakeId(appointment.getIntakeId())
                .status(appointment.getStatus())
                .amountVnd(appointment.getAmountVnd())
                .currency(appointment.getCurrency())
                .createdAt(appointment.getCreatedAt())
                .expiresAt(appointment.getExpiresAt())
                .remainingSeconds(remainingSeconds)
                .confirmedAt(appointment.getConfirmedAt())
                .startedAt(appointment.getStartedAt())
                .completedAt(appointment.getCompletedAt())
                .version(appointment.getVersion())
                .paymentCode("PAY-" + appointment.getId().toString().substring(0, 8).toUpperCase())
                .canReschedule(canReschedule)
                .demo(appointment.isDemo())
                .build();
    }

    private void saveOutboxEvent(String eventType, UUID aggregateId, int version, Object payload) {
        try {
            OutboxEventEntity event = OutboxEventEntity.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .aggregateVersion(version)
                    .schemaVersion(1)
                    .payload(objectMapper.writeValueAsString(payload))
                    .occurredAt(Instant.now())
                    .nextAttemptAt(Instant.now())
                    .attempts(0)
                    .build();
            outboxEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to serialize outbox event payload", e);
        }
    }
}
