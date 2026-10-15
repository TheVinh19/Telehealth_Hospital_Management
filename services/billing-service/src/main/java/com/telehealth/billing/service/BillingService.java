package com.telehealth.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telehealth.billing.dto.*;
import com.telehealth.billing.entity.OutboxEventEntity;
import com.telehealth.billing.entity.PaymentOrderEntity;
import com.telehealth.billing.repository.OutboxEventRepository;
import com.telehealth.billing.repository.PaymentOrderRepository;
import com.telehealth.contracts.enums.PaymentStatus;
import com.telehealth.contracts.events.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${services.appointment.url:http://localhost:8082}")
    private String appointmentServiceUrl;

    @Transactional
    public PaymentOrderResponse getOrCreateOrder(CreateOrderRequest request) {
        Optional<PaymentOrderEntity> existing = paymentOrderRepository.findByAppointmentId(request.getAppointmentId());
        if (existing.isPresent()) {
            return mapToResponse(existing.get());
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = request.getExpiresAt() != null
                ? request.getExpiresAt()
                : now.plus(7, ChronoUnit.MINUTES);

        String paymentCode = "PAY-" + request.getAppointmentId().toString().substring(0, 8).toUpperCase();

        PaymentOrderEntity order = PaymentOrderEntity.builder()
                .id(UUID.randomUUID())
                .appointmentId(request.getAppointmentId())
                .patientId(request.getPatientId())
                .amountVnd(request.getAmountVnd() != null ? request.getAmountVnd() : 100000L)
                .currency("VND")
                .provider("MOCK_PAYMENT")
                .paymentCode(paymentCode)
                .status(PaymentStatus.PENDING)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        order = paymentOrderRepository.save(order);
        log.info("Created payment order {} with code {} for appointment {}", order.getId(), paymentCode, request.getAppointmentId());
        return mapToResponse(order);
    }

    @Transactional
    public PaymentOrderResponse getPaymentByAppointmentId(UUID appointmentId) {
        PaymentOrderEntity order = paymentOrderRepository.findByAppointmentId(appointmentId)
                .orElseGet(() -> {
                    // Create default order if appointment exists
                    CreateOrderRequest req = CreateOrderRequest.builder()
                            .appointmentId(appointmentId)
                            .patientId(UUID.randomUUID())
                            .amountVnd(100000L)
                            .expiresAt(Instant.now().plus(7, ChronoUnit.MINUTES))
                            .build();
                    return paymentOrderRepository.save(PaymentOrderEntity.builder()
                            .id(UUID.randomUUID())
                            .appointmentId(appointmentId)
                            .patientId(req.getPatientId())
                            .amountVnd(100000L)
                            .currency("VND")
                            .provider("MOCK_PAYMENT")
                            .paymentCode("PAY-" + appointmentId.toString().substring(0, 8).toUpperCase())
                            .status(PaymentStatus.PENDING)
                            .createdAt(Instant.now())
                            .expiresAt(req.getExpiresAt())
                            .build());
                });

        checkAndExpire(order);
        return mapToResponse(order);
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public PaymentOrderResponse mockPay(MockPayRequest request) {
        PaymentOrderEntity order = paymentOrderRepository.findByPaymentCode(request.getPaymentCode().trim())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn thanh toán với mã: " + request.getPaymentCode()));

        // Idempotency: If already paid, return immediately without re-triggering events
        if (order.getStatus() == PaymentStatus.PAID) {
            log.info("Order {} is already PAID (idempotent request)", order.getId());
            return mapToResponse(order);
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        if (now.isAfter(order.getExpiresAt())) {
            order.setStatus(PaymentStatus.EXPIRED);
            paymentOrderRepository.save(order);
            throw new IllegalStateException("Đơn thanh toán đã hết hạn 7 phút. Vui lòng đặt lại ca khám.");
        }

        order.setStatus(PaymentStatus.PAID);
        order.setPaidAt(now);
        order.setProviderTransactionId("MOCK-TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order = paymentOrderRepository.save(order);

        // 1. Write OutboxEvent
        saveOutboxEvent(order);

        // 2. Notify appointment-service internally
        notifyAppointmentConfirmed(order);

        log.info("Order {} successfully PAID with txn {}", order.getId(), order.getProviderTransactionId());
        return mapToResponse(order);
    }

    @Transactional(readOnly = true)
    public List<PaymentOrderResponse> getAllOrders() {
        return paymentOrderRepository.findAll().stream().map(this::mapToResponse).toList();
    }

    private void checkAndExpire(PaymentOrderEntity order) {
        if (order.getStatus() == PaymentStatus.PENDING && Instant.now().isAfter(order.getExpiresAt())) {
            order.setStatus(PaymentStatus.EXPIRED);
            paymentOrderRepository.save(order);
        }
    }

    private PaymentOrderResponse mapToResponse(PaymentOrderEntity order) {
        long remaining = 0;
        if (order.getStatus() == PaymentStatus.PENDING) {
            remaining = Math.max(0, Duration.between(Instant.now(), order.getExpiresAt()).toSeconds());
        }

        String qrPayload = "telehealth://payment?code=" + order.getPaymentCode() + "&amount=" + order.getAmountVnd();

        return PaymentOrderResponse.builder()
                .id(order.getId())
                .appointmentId(order.getAppointmentId())
                .amountVnd(order.getAmountVnd())
                .currency(order.getCurrency())
                .provider(order.getProvider())
                .paymentCode(order.getPaymentCode())
                .qrPayload(qrPayload)
                .status(order.getStatus())
                .remainingSeconds(remaining)
                .expiresAt(order.getExpiresAt())
                .paidAt(order.getPaidAt())
                .build();
    }

    private void saveOutboxEvent(PaymentOrderEntity order) {
        try {
            PaymentSucceededEvent payload = PaymentSucceededEvent.builder()
                    .appointmentId(order.getAppointmentId())
                    .paymentOrderId(order.getId())
                    .patientId(order.getPatientId())
                    .paymentCode(order.getPaymentCode())
                    .providerTransactionId(order.getProviderTransactionId())
                    .amountVnd(order.getAmountVnd())
                    .paidAt(order.getPaidAt())
                    .build();

            OutboxEventEntity event = OutboxEventEntity.builder()
                    .eventId(UUID.randomUUID())
                    .eventType("PaymentSucceeded")
                    .aggregateId(order.getId())
                    .aggregateVersion(1)
                    .payload(objectMapper.writeValueAsString(payload))
                    .occurredAt(Instant.now())
                    .nextAttemptAt(Instant.now())
                    .build();

            outboxEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to save PaymentSucceeded outbox event", e);
        }
    }

    private void notifyAppointmentConfirmed(PaymentOrderEntity order) {
        try {
            RestClient restClient = RestClient.create();
            Map<String, Object> body = Map.of(
                    "amountVnd", order.getAmountVnd(),
                    "paymentCode", order.getPaymentCode()
            );

            restClient.post()
                    .uri(appointmentServiceUrl + "/api/v1/internal/appointments/" + order.getAppointmentId() + "/confirm")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Notified appointment-service of confirmation for appointment {}", order.getAppointmentId());
        } catch (Exception e) {
            log.warn("Internal sync call to appointment-service failed (outbox will still deliver event): {}", e.getMessage());
        }
    }
}
