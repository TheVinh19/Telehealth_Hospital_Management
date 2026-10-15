package com.telehealth.contracts.events;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class PaymentSucceededEvent {
    private UUID appointmentId;
    private UUID paymentOrderId;
    private UUID patientId;
    private String paymentCode;
    private String providerTransactionId;
    private Long amountVnd;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant paidAt;
}
