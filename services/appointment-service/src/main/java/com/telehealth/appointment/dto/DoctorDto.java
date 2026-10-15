package com.telehealth.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorDto {
    private UUID id;
    private UUID userId;
    private UUID specialtyId;
    private String specialtyName;
    private String displayName;
    private String bio;
    private boolean active;
}
