package com.telehealth.clinical.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntakePayload {
    @NotBlank(message = "Lý do khám không được để trống")
    private String reason;

    @NotBlank(message = "Triệu chứng hiện tại không được để trống")
    private String symptoms;

    private String medicalHistory;
    private String underlyingConditions;
    private String symptomStartedAt;
    private String currentMedications;
    private String drugAllergies;
}
