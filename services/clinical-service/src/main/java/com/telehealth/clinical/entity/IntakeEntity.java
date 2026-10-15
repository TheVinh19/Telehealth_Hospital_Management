package com.telehealth.clinical.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "intakes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntakeEntity {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Builder.Default
    @Column(nullable = false)
    private String state = "DRAFT"; // DRAFT, SUBMITTED

    @Column(name = "payload_ciphertext", nullable = false, columnDefinition = "BYTEA")
    private byte[] payloadCiphertext;

    @Column(nullable = false)
    private byte[] nonce;

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Builder.Default
    @Column(name = "payload_version", nullable = false)
    private Integer payloadVersion = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;
}
