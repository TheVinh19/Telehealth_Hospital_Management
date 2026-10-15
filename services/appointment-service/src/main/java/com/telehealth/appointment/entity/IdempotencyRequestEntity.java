package com.telehealth.appointment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(IdempotencyRequestEntity.IdempotencyKeyId.class)
public class IdempotencyRequestEntity {

    @Id
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Id
    @Column(name = "request_key", nullable = false)
    private String requestKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdempotencyKeyId implements Serializable {
        private UUID patientId;
        private String requestKey;
    }
}
