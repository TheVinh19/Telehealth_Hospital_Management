package com.telehealth.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDoctorProfileRequest {
    @NotNull(message = "userId không được để trống")
    private UUID userId;

    @NotNull(message = "specialtyId không được để trống")
    private UUID specialtyId;

    @NotBlank(message = "displayName không được để trống")
    private String displayName;

    private String bio;
}
