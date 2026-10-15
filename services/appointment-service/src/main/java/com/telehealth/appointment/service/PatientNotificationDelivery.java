package com.telehealth.appointment.service;

import com.telehealth.appointment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientNotificationDelivery {
    // Separate types deliberately avoid sending historical, unpublished demo events.
    public static final String CONFIRMED = "AppointmentConfirmedEmailRequested";
    public static final String RESCHEDULED = "AppointmentRescheduledEmailRequested";
    public static final List<String> TYPES = List.of(CONFIRMED, RESCHEDULED);
    private final OutboxEventRepository outbox;
    private final PatientNotificationClient client;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(UUID eventId) {
        var event = outbox.findByEventIdWithLock(eventId).orElse(null);
        if (event == null || event.getPublishedAt() != null || !TYPES.contains(event.getEventType())) return;
        event.setAttempts(event.getAttempts() + 1);
        try {
            client.deliver(event);
            event.setPublishedAt(Instant.now());
        } catch (Exception exception) {
            event.setNextAttemptAt(Instant.now().plusSeconds(Math.min(60, 1L << Math.min(event.getAttempts(), 6))));
            log.warn("Email request {} will be retried ({})", eventId, exception.getClass().getSimpleName());
        }
        outbox.save(event);
    }
}
