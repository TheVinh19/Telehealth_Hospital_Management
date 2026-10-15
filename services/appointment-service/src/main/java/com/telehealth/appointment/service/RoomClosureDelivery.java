package com.telehealth.appointment.service;

import com.telehealth.appointment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomClosureDelivery {
    public static final String EVENT_TYPE = "AppointmentCompletedRoomClosure";
    private final OutboxEventRepository outboxRepository;
    private final RealtimeRoomClient realtimeRoomClient;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(UUID eventId) {
        var event = outboxRepository.findByEventIdWithLock(eventId).orElse(null);
        if (event == null || event.getPublishedAt() != null || !EVENT_TYPE.equals(event.getEventType())) return;
        event.setAttempts(event.getAttempts() + 1);
        try {
            realtimeRoomClient.closeCompletedRoom(event.getAggregateId());
            event.setPublishedAt(Instant.now());
        } catch (Exception exception) {
            event.setNextAttemptAt(Instant.now().plusSeconds(Math.min(30, 1L << Math.min(event.getAttempts(), 5))));
            log.warn("Room closure for appointment {} will be retried: {}", event.getAggregateId(), exception.getMessage());
        }
        outboxRepository.save(event);
    }
}
