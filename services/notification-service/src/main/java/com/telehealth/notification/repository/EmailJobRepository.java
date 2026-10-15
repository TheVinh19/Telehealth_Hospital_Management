package com.telehealth.notification.repository;

import com.telehealth.notification.entity.EmailJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailJobRepository extends JpaRepository<EmailJobEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM EmailJobEntity j WHERE j.id = :id")
    Optional<EmailJobEntity> findByIdWithLock(@Param("id") UUID id);
    Optional<EmailJobEntity> findByEventIdAndRecipientAndTemplate(UUID eventId, String recipient, String template);
    List<EmailJobEntity> findByStatusInAndNextAttemptAtLessThanEqual(List<String> statuses, Instant now);
    List<EmailJobEntity> findTop50ByOrderByCreatedAtDesc();
}
