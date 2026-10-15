package com.telehealth.clinical.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "encounters")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncounterEntity {

    @Id
    private UUID id;

    @Column(name = "appointment_id", nullable = false, unique = true)
    private UUID appointmentId;

    @Column(name = "intake_id", nullable = false)
    private UUID intakeId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Builder.Default
    @Column(nullable = false)
    private String state = "DRAFT"; // DRAFT, PUBLISHED

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}
