package com.telehealth.billing.repository;

import com.telehealth.billing.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findByPublishedAtIsNullAndNextAttemptAtLessThanEqual(Instant now);
}
