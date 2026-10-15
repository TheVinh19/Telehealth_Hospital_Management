package com.telehealth.billing.entity;

import com.telehealth.contracts.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderEntity {

    @Id
    private UUID id;

    @Column(name = "appointment_id", nullable = false, unique = true)
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Builder.Default
    @Column(name = "amount_vnd", nullable = false)
    private Long amountVnd = 100000L;

    @Builder.Default
    @Column(nullable = false)
    private String currency = "VND";

    @Builder.Default
    @Column(nullable = false)
    private String provider = "MOCK_PAYMENT";

    @Column(name = "payment_code", nullable = false, unique = true)
    private String paymentCode;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;
}
