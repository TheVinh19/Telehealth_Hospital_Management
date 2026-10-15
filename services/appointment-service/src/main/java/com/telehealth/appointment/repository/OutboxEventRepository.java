package com.telehealth.appointment.repository;

import com.telehealth.appointment.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findByPublishedAtIsNullAndNextAttemptAtLessThanEqual(Instant now);

    List<OutboxEventEntity> findTop20ByEventTypeAndPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByOccurredAtAsc(String eventType, Instant now);

    List<OutboxEventEntity> findTop20ByEventTypeInAndPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByOccurredAtAsc(List<String> eventTypes, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEventEntity e WHERE e.eventId = :id")
    Optional<OutboxEventEntity> findByEventIdWithLock(@Param("id") UUID id);

    boolean existsByEventTypeAndAggregateId(String eventType, UUID aggregateId);
}
