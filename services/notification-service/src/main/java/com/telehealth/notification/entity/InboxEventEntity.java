package com.telehealth.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(InboxEventEntity.InboxId.class)
public class InboxEventEntity {

    @Id
    @Column(name = "consumer_name", nullable = false)
    private String consumerName;

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboxId implements Serializable {
        private String consumerName;
        private UUID eventId;
    }
}
