package com.telehealth.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts", uniqueConstraints = {
        @UniqueConstraint(name = "uq_job_attempt", columnNames = {"job_id", "attempt_number"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "error_code", columnDefinition = "TEXT")
    private String errorCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
