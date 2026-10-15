package com.telehealth.billing.repository;

import com.telehealth.billing.entity.PaymentCallbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentCallbackRepository extends JpaRepository<PaymentCallbackEntity, UUID> {
    Optional<PaymentCallbackEntity> findByProviderAndProviderEventId(String provider, String providerEventId);
}
