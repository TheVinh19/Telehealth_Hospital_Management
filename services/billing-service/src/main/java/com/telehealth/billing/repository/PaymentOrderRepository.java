package com.telehealth.billing.repository;

import com.telehealth.billing.entity.PaymentOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrderEntity, UUID> {
    Optional<PaymentOrderEntity> findByAppointmentId(UUID appointmentId);
    Optional<PaymentOrderEntity> findByPaymentCode(String paymentCode);
}
