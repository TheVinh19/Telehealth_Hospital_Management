package com.telehealth.appointment.entity;

import com.telehealth.contracts.enums.ClaimKind;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slot_claims")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotClaimEntity {
    @Id
    @Column(name = "slot_id")
    private UUID slotId;

    @Column(name = "appointment_id", nullable = false, unique = true)
    private UUID appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimKind kind;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
