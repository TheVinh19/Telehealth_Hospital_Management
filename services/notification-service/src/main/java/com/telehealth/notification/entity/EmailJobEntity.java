package com.telehealth.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_jobs", uniqueConstraints = {
        @UniqueConstraint(name = "uq_event_recipient_template", columnNames = {"event_id", "recipient", "template"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailJobEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String template; // APPOINTMENT_CONFIRMED, APPOINTMENT_RESCHEDULED

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Builder.Default
    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, PROCESSING, SENT, FAILED

    @Builder.Default
    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
