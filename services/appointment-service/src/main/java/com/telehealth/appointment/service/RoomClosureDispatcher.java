package com.telehealth.appointment.service;

import com.telehealth.appointment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomClosureDispatcher {
    private final RoomClosureDelivery delivery;
    private final OutboxEventRepository outboxRepository;

    @Value("${services.realtime.close-delivery-enabled:true}")
    private boolean enabled;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCompletion(RoomClosureRequested request) {
        if (enabled) deliverSafely(request.eventId());
    }

    @Scheduled(fixedDelayString = "${services.realtime.close-retry-ms:3000}")
    public void retryPending() {
        if (!enabled) return;
        outboxRepository.findTop20ByEventTypeAndPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByOccurredAtAsc(
                RoomClosureDelivery.EVENT_TYPE, Instant.now()).forEach(event -> deliverSafely(event.getEventId()));
    }

    private void deliverSafely(java.util.UUID eventId) {
        try {
            delivery.deliver(eventId);
        } catch (Exception exception) {
            // Completion already committed; the durable event will be retried.
            log.warn("Could not deliver room closure {} yet: {}", eventId, exception.getMessage());
        }
    }
}
