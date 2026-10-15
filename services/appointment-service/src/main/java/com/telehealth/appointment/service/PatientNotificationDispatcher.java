package com.telehealth.appointment.service;

import com.telehealth.appointment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PatientNotificationDispatcher {
    private final OutboxEventRepository outbox;
    private final PatientNotificationDelivery delivery;
    @Value("${services.notification.delivery-enabled:true}")
    private boolean enabled;

    @TransactionalEventListener
    public void afterCommit(PatientNotificationRequested request) {
        if (enabled) safelyDeliver(request.eventId());
    }

    @Scheduled(fixedDelayString = "${services.notification.retry-ms:3000}")
    public void retry() {
        if (!enabled) return;
        outbox.findTop20ByEventTypeInAndPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByOccurredAtAsc(
                PatientNotificationDelivery.TYPES, Instant.now()).forEach(event -> safelyDeliver(event.getEventId()));
    }

    private void safelyDeliver(UUID eventId) {
        try {
            delivery.deliver(eventId);
        } catch (Exception exception) {
            log.warn("Email delivery {} deferred ({})", eventId, exception.getClass().getSimpleName());
        }
    }
}
