package com.telehealth.notification.repository;

import com.telehealth.notification.entity.EmailJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailJobRepository extends JpaRepository<EmailJobEntity, UUID> {
    Optional<EmailJobEntity> findByEventIdAndRecipientAndTemplate(UUID eventId, String recipient, String template);
    List<EmailJobEntity> findByStatusInAndNextAttemptAtLessThanEqual(List<String> statuses, Instant now);
    List<EmailJobEntity> findTop50ByOrderByCreatedAtDesc();
}
