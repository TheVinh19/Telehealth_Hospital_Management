package com.telehealth.billing.service;

import com.telehealth.billing.entity.PaymentOrderEntity;
import com.telehealth.billing.repository.PaymentOrderRepository;
import com.telehealth.contracts.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingDataInitializer implements ApplicationRunner {

    private final PaymentOrderRepository paymentOrderRepository;

    public static final UUID SAMPLE_APPT_1_ID = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
    public static final UUID SAMPLE_APPT_2_ID = UUID.fromString("aaaaaaaa-2222-2222-2222-222222222222");
    public static final UUID PATIENT_1_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Override
    public void run(ApplicationArguments args) {
        seedSampleOrders();
    }

    private void seedSampleOrders() {
        if (paymentOrderRepository.findByAppointmentId(SAMPLE_APPT_1_ID).isEmpty()) {
            PaymentOrderEntity order1 = PaymentOrderEntity.builder()
                    .id(UUID.randomUUID())
                    .appointmentId(SAMPLE_APPT_1_ID)
                    .patientId(PATIENT_1_ID)
                    .amountVnd(100000L)
                    .currency("VND")
                    .provider("MOCK_PAYMENT")
                    .paymentCode("PAY-AA111111")
                    .status(PaymentStatus.PAID)
                    .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                    .expiresAt(Instant.now().plus(7, ChronoUnit.MINUTES))
                    .paidAt(Instant.now().minus(50, ChronoUnit.MINUTES))
                    .build();
            paymentOrderRepository.save(order1);
            log.info("Seeded demo payment order 1 for appointment {}", SAMPLE_APPT_1_ID);
        }

        if (paymentOrderRepository.findByAppointmentId(SAMPLE_APPT_2_ID).isEmpty()) {
            PaymentOrderEntity order2 = PaymentOrderEntity.builder()
                    .id(UUID.randomUUID())
                    .appointmentId(SAMPLE_APPT_2_ID)
                    .patientId(PATIENT_1_ID)
                    .amountVnd(100000L)
                    .currency("VND")
                    .provider("MOCK_PAYMENT")
                    .paymentCode("PAY-AA222222")
                    .status(PaymentStatus.PAID)
                    .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS).plus(7, ChronoUnit.MINUTES))
                    .paidAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .build();
            paymentOrderRepository.save(order2);
            log.info("Seeded demo payment order 2 for appointment {}", SAMPLE_APPT_2_ID);
        }
    }
}
