package com.telehealth.appointment.entity;

import com.telehealth.contracts.enums.AppointmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointments")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentEntity {
    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "slot_id", nullable = false)
    private UUID slotId;

    @Column(name = "intake_id", nullable = false, unique = true)
    private UUID intakeId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.PENDING_PAYMENT;

    @Builder.Default
    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd = 100000L;

    @Builder.Default
    @Column(nullable = false)
    private String currency = "VND";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean demo = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    @Builder.Default
    @Column(nullable = false)
    private Integer version = 1;
}
