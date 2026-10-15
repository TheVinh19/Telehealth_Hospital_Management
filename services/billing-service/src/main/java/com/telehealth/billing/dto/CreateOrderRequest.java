package com.telehealth.billing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    @NotNull(message = "appointmentId không được để trống")
    private UUID appointmentId;

    @NotNull(message = "patientId không được để trống")
    private UUID patientId;

    private Long amountVnd;
    private Instant expiresAt;
}
