package com.telehealth.appointment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "specialties")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecialtyEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;
}
