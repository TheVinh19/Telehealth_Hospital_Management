package com.telehealth.appointment.service;

import com.telehealth.appointment.dto.*;
import com.telehealth.appointment.entity.DoctorEntity;
import com.telehealth.appointment.entity.SlotClaimEntity;
import com.telehealth.appointment.entity.SlotEntity;
import com.telehealth.appointment.entity.SpecialtyEntity;
import com.telehealth.appointment.repository.AppointmentRepository;
import com.telehealth.appointment.repository.DoctorRepository;
import com.telehealth.appointment.repository.SlotClaimRepository;
import com.telehealth.appointment.repository.SlotRepository;
import com.telehealth.appointment.repository.SpecialtyRepository;
import com.telehealth.contracts.enums.AppointmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final SpecialtyRepository specialtyRepository;
    private final DoctorRepository doctorRepository;
    private final SlotRepository slotRepository;
    private final SlotClaimRepository slotClaimRepository;
    private final AppointmentRepository appointmentRepository;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<AppointmentStatus> ACTIVE_APPOINTMENT_STATUSES = Set.of(
            AppointmentStatus.PENDING_PAYMENT,
            AppointmentStatus.CONFIRMED,
            AppointmentStatus.IN_PROGRESS
    );

    /*
     * The database intentionally keeps the complete specialty catalog because
     * doctors and historical appointments may already refer to those records.
     * Patients, however, can currently book only the five supported care paths.
     * Keep the patient-facing labels here instead of renaming/deleting persisted
     * specialties, so existing links and the admin catalog remain intact.
     */
    private static final List<PatientSpecialty> PATIENT_SPECIALTIES = List.of(
            new PatientSpecialty("Nội tổng quát", "Nội khoa tổng quát"),
            new PatientSpecialty("Tim mạch", "Nội tim mạch"),
            new PatientSpecialty("Nhi khoa", "Nhi khoa"),
            new PatientSpecialty("Da liễu", "Da liễu"),
            new PatientSpecialty("Thần kinh", "Thần kinh")
    );

    @Transactional(readOnly = true)
    public List<SpecialtyDto> getPatientSpecialties() {
        List<SpecialtyEntity> activeSpecialties = specialtyRepository.findByActiveTrue();

        return PATIENT_SPECIALTIES.stream()
                .map(definition -> activeSpecialties.stream()
                        .filter(specialty -> specialty.getName().equalsIgnoreCase(definition.storedName()))
                        .findFirst()
                        .map(specialty -> SpecialtyDto.builder()
                                .id(specialty.getId())
                                .name(definition.displayName())
                                .active(true)
                                .build())
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpecialtyDto> getAdminSpecialties() {
        return specialtyRepository.findByActiveTrue().stream()
                .map(s -> SpecialtyDto.builder().id(s.getId()).name(s.getName()).active(s.isActive()).build())
                .toList();
    }

    private record PatientSpecialty(String storedName, String displayName) {
    }

    @Transactional
    public SpecialtyDto createSpecialty(CreateSpecialtyRequest request) {
        String name = request.getName().trim();
        if (specialtyRepository.findByNameIgnoreCase(name).isPresent()) {
            throw new IllegalArgumentException("Chuyên khoa đã tồn tại: " + name);
        }
        SpecialtyEntity entity = SpecialtyEntity.builder()
                .id(UUID.randomUUID())
                .name(name)
                .active(true)
                .build();
        entity = specialtyRepository.save(entity);
        return SpecialtyDto.builder().id(entity.getId()).name(entity.getName()).active(entity.isActive()).build();
    }

    @Transactional(readOnly = true)
    public List<DoctorDto> getDoctors(UUID specialtyId) {
        List<DoctorEntity> list = specialtyId != null
                ? doctorRepository.findBySpecialtyIdAndActiveTrue(specialtyId)
                : doctorRepository.findByActiveTrue();

        return list.stream().map(this::mapDoctor).toList();
    }

    @Transactional(readOnly = true)
    public DoctorDto getDoctorById(UUID id) {
        DoctorEntity doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bác sĩ"));
        return mapDoctor(doctor);
    }

    @Transactional
    public DoctorDto createDoctor(CreateDoctorProfileRequest request) {
        SpecialtyEntity specialty = specialtyRepository.findById(request.getSpecialtyId())
                .orElseThrow(() -> new IllegalArgumentException("Chuyên khoa không tồn tại"));

        Optional<DoctorEntity> existingDoctor = doctorRepository.findByUserId(request.getUserId());
        if (existingDoctor.isPresent()) {
            DoctorEntity doctor = existingDoctor.get();
            if (doctor.isActive()) {
                throw new IllegalArgumentException("Bác sĩ với userId này đã có hồ sơ");
            }

            doctor.setSpecialtyId(specialty.getId());
            doctor.setDisplayName(request.getDisplayName().trim());
            doctor.setBio(request.getBio());
            doctor.setActive(true);
            return mapDoctor(doctorRepository.save(doctor));
        }

        DoctorEntity doctor = DoctorEntity.builder()
                .id(UUID.randomUUID())
                .userId(request.getUserId())
                .specialtyId(specialty.getId())
                .displayName(request.getDisplayName().trim())
                .bio(request.getBio())
                .active(true)
                .build();

        doctor = doctorRepository.save(doctor);
        return mapDoctor(doctor);
    }

    @Transactional
    public List<SlotDto> getSlots(UUID doctorId, String dateString) {
        DoctorEntity doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bác sĩ"));

        List<SlotEntity> slots;
        if (dateString != null && !dateString.isBlank()) {
            LocalDate date = LocalDate.parse(dateString);
            Instant start = date.atStartOfDay(VN_ZONE).toInstant();
            Instant end = date.plusDays(1).atStartOfDay(VN_ZONE).toInstant();
            slots = slotRepository.findByDoctorIdAndStartsAtBetweenAndEnabledTrueOrderByStartsAtAsc(doctorId, start, end);
        } else {
            slots = slotRepository.findByDoctorIdAndEnabledTrueOrderByStartsAtAsc(doctorId);
        }

        Instant now = Instant.now();
        return slots.stream().map(slot -> {
            Optional<SlotClaimEntity> claimOpt = slotClaimRepository.findById(slot.getId());
            boolean available = true;
            if (claimOpt.isPresent()) {
                SlotClaimEntity claim = claimOpt.get();
                if (claim.getExpiresAt() != null && now.isAfter(claim.getExpiresAt())) {
                    // Expired claim can be cleared
                    slotClaimRepository.delete(claim);
                    available = true;
                } else {
                    available = false;
                }
            }
            return SlotDto.builder()
                    .id(slot.getId())
                    .doctorId(doctor.getId())
                    .doctorName(doctor.getDisplayName())
                    .startsAt(slot.getStartsAt())
                    .available(available)
                    .enabled(slot.isEnabled())
                    .build();
        }).toList();
    }

    @Transactional
    public SlotDto createSlot(CreateSlotRequest request) {
        DoctorEntity doctor = doctorRepository.findByIdWithLock(request.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bác sĩ"));

        if (!doctor.isActive()) {
            throw new IllegalStateException("Bác sĩ đã bị vô hiệu hóa");
        }

        if (slotRepository.existsByDoctorIdAndStartsAt(request.getDoctorId(), request.getStartsAt())) {
            throw new IllegalArgumentException("Ca khám vào thời điểm này đã tồn tại cho bác sĩ");
        }

        SlotEntity slot = SlotEntity.builder()
                .id(UUID.randomUUID())
                .doctorId(doctor.getId())
                .startsAt(request.getStartsAt())
                .enabled(true)
                .build();

        slot = slotRepository.save(slot);
        return SlotDto.builder()
                .id(slot.getId())
                .doctorId(doctor.getId())
                .doctorName(doctor.getDisplayName())
                .startsAt(slot.getStartsAt())
                .available(true)
                .enabled(slot.isEnabled())
                .build();
    }

    /**
     * Removes a slot from the bookable catalog without deleting historical records.
     * A slot with an active appointment cannot be removed.
     */
    @Transactional
    public void deleteSlot(UUID slotId) {
        SlotEntity slot = slotRepository.findByIdWithLock(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ca khám"));

        if (appointmentRepository.existsBySlotIdAndStatusIn(slotId, ACTIVE_APPOINTMENT_STATUSES)) {
            throw new IllegalStateException("Không thể xóa ca khám đang được giữ, đã xác nhận hoặc đang diễn ra");
        }

        slot.setEnabled(false);
        slotRepository.save(slot);
    }

    /**
     * Deactivates a doctor and all of their slots while preserving completed and
     * expired appointment history. Doctors with active appointments must be
     * resolved before they can be removed from the catalog.
     */
    @Transactional
    public void deleteDoctor(UUID doctorId) {
        DoctorEntity doctor = doctorRepository.findByIdWithLock(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bác sĩ"));

        if (appointmentRepository.existsByDoctorIdAndStatusIn(doctorId, ACTIVE_APPOINTMENT_STATUSES)) {
            throw new IllegalStateException("Không thể xóa bác sĩ đang có lịch chờ thanh toán, đã xác nhận hoặc đang diễn ra");
        }

        doctor.setActive(false);
        doctorRepository.save(doctor);

        List<SlotEntity> slots = slotRepository.findByDoctorId(doctorId);
        slots.forEach(slot -> slot.setEnabled(false));
        slotRepository.saveAll(slots);
    }

    private DoctorDto mapDoctor(DoctorEntity doctor) {
        String specialtyName = specialtyRepository.findById(doctor.getSpecialtyId())
                .map(SpecialtyEntity::getName)
                .orElse("Đa khoa");

        return DoctorDto.builder()
                .id(doctor.getId())
                .userId(doctor.getUserId())
                .specialtyId(doctor.getSpecialtyId())
                .specialtyName(specialtyName)
                .displayName(doctor.getDisplayName())
                .bio(doctor.getBio())
                .active(doctor.isActive())
                .build();
    }
}
