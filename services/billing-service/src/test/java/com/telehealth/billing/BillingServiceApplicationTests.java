package com.telehealth.billing;

import com.telehealth.billing.dto.CreateOrderRequest;
import com.telehealth.billing.dto.MockPayRequest;
import com.telehealth.billing.dto.PaymentOrderResponse;
import com.telehealth.billing.entity.PaymentOrderEntity;
import com.telehealth.billing.repository.PaymentOrderRepository;
import com.telehealth.billing.service.BillingService;
import com.telehealth.contracts.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BillingServiceApplicationTests {

    @Autowired
    private BillingService billingService;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Test
    void testCreateAndMockPayOrder() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        PaymentOrderResponse order = billingService.getOrCreateOrder(CreateOrderRequest.builder()
                .appointmentId(appointmentId)
                .patientId(patientId)
                .amountVnd(100000L)
                .build());

        assertNotNull(order.getId());
        assertEquals(PaymentStatus.PENDING, order.getStatus());
        assertTrue(order.getPaymentCode().startsWith("PAY-"));
        assertTrue(order.getRemainingSeconds() <= 420);

        // Execute mock payment
        PaymentOrderResponse paid = billingService.mockPay(MockPayRequest.builder()
                .paymentCode(order.getPaymentCode())
                .build());

        assertEquals(PaymentStatus.PAID, paid.getStatus());
        assertNotNull(paid.getPaidAt());

        // Idempotency: calling mockPay again returns PAID without error
        PaymentOrderResponse repeatedPay = billingService.mockPay(MockPayRequest.builder()
                .paymentCode(order.getPaymentCode())
                .build());

        assertEquals(PaymentStatus.PAID, repeatedPay.getStatus());
        assertEquals(paid.getId(), repeatedPay.getId());
    }

    @Test
    void testLatePaymentFailsForExpiredOrder() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        // Expired order created in the past
        Instant pastExpiry = Instant.now().minus(5, ChronoUnit.MINUTES);
        PaymentOrderEntity order = PaymentOrderEntity.builder()
                .id(UUID.randomUUID())
                .appointmentId(appointmentId)
                .patientId(patientId)
                .amountVnd(100000L)
                .currency("VND")
                .provider("MOCK_PAYMENT")
                .paymentCode("PAY-EXPIRED-" + UUID.randomUUID().toString().substring(0, 6))
                .status(PaymentStatus.PENDING)
                .createdAt(Instant.now().minus(15, ChronoUnit.MINUTES))
                .expiresAt(pastExpiry)
                .build();

        paymentOrderRepository.save(order);

        // Attempting to mock-pay an expired order -> MUST FAIL
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                billingService.mockPay(MockPayRequest.builder()
                        .paymentCode(order.getPaymentCode())
                        .build())
        );
        assertTrue(ex.getMessage().contains("đã hết hạn 7 phút"));

        // Verify status changed to EXPIRED
        PaymentOrderEntity updated = paymentOrderRepository.findById(order.getId()).orElseThrow();
        assertEquals(PaymentStatus.EXPIRED, updated.getStatus());
    }
}
