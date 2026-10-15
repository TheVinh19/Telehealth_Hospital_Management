package com.telehealth.realtime.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomEntity {

    @Id
    private UUID id;

    @Column(name = "appointment_id", nullable = false, unique = true)
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_user_id", nullable = false)
    private UUID doctorUserId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Builder.Default
    @Column(name = "admission_version", nullable = false)
    private Integer admissionVersion = 1;

    @Builder.Default
    @Column(nullable = false)
    private String state = "WAITING"; // WAITING, OPEN, CLOSED

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
