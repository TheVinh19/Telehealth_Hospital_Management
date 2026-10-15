package com.telehealth.appointment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reschedule_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleHistoryEntity {
    @Id
    private UUID id;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "old_slot_id", nullable = false)
    private UUID oldSlotId;

    @Column(name = "new_slot_id", nullable = false)
    private UUID newSlotId;

    @Column(name = "old_starts_at", nullable = false)
    private Instant oldStartsAt;

    @Column(name = "new_starts_at", nullable = false)
    private Instant newStartsAt;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "appointment_version", nullable = false)
    private Integer appointmentVersion;
}
