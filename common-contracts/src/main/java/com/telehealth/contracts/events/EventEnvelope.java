package com.telehealth.contracts.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope<T> {
    @Builder.Default
    private UUID eventId = UUID.randomUUID();
    private String eventType;
    private UUID aggregateId;
    @Builder.Default
    private Integer aggregateVersion = 1;
    @Builder.Default
    private Integer schemaVersion = 1;
    private UUID correlationId;
    @Builder.Default
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant occurredAt = Instant.now();
    private T payload;
}
