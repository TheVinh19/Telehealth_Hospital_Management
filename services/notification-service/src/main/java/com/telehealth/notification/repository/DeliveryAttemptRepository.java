package com.telehealth.notification.repository;

import com.telehealth.notification.entity.DeliveryAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttemptEntity, Long> {
    List<DeliveryAttemptEntity> findByJobIdOrderByAttemptNumberAsc(UUID jobId);
}
