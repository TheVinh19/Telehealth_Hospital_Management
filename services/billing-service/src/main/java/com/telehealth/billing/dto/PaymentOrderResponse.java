package com.telehealth.billing.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.telehealth.contracts.enums.PaymentStatus;
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
public class PaymentOrderResponse {
    private UUID id;
    private UUID appointmentId;
    private Long amountVnd;
    private String currency;
    private String provider;
    private String paymentCode;
    private String qrPayload;
    private PaymentStatus status;
    private long remainingSeconds;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant expiresAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant paidAt;
}
