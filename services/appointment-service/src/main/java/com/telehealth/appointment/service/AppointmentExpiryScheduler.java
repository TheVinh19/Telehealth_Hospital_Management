package com.telehealth.appointment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telehealth.appointment.entity.AppointmentEntity;
import com.telehealth.appointment.entity.OutboxEventEntity;
import com.telehealth.appointment.repository.AppointmentRepository;
import com.telehealth.appointment.repository.OutboxEventRepository;
import com.telehealth.appointment.repository.SlotClaimRepository;
import com.telehealth.contracts.enums.AppointmentStatus;
import com.telehealth.contracts.events.AppointmentExpiredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentExpiryScheduler {

    private final AppointmentRepository appointmentRepository;
    private final SlotClaimRepository slotClaimRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void processExpiredAppointments() {
        Instant now = Instant.now();
        List<AppointmentEntity> expiredList = appointmentRepository.findExpiredPendingAppointments(now);
        for (AppointmentEntity app : expiredList) {
            log.info("Worker auto-expiring pending appointment {} (deadline was {})", app.getId(), app.getExpiresAt());
            app.setStatus(AppointmentStatus.EXPIRED);
            appointmentRepository.save(app);

            slotClaimRepository.deleteByAppointmentId(app.getId());

            try {
                AppointmentExpiredEvent payload = AppointmentExpiredEvent.builder()
                        .appointmentId(app.getId())
                        .slotId(app.getSlotId())
                        .doctorId(app.getDoctorId())
                        .patientId(app.getPatientId())
                        .expiredAt(now)
                        .build();

                OutboxEventEntity event = OutboxEventEntity.builder()
                        .eventId(UUID.randomUUID())
                        .eventType("AppointmentExpired")
                        .aggregateId(app.getId())
                        .aggregateVersion(app.getVersion())
                        .payload(objectMapper.writeValueAsString(payload))
                        .occurredAt(now)
                        .nextAttemptAt(now)
                        .build();
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to write AppointmentExpired event", e);
            }
        }
    }
}
