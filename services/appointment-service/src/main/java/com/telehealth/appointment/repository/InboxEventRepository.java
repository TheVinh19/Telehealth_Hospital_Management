package com.telehealth.appointment.repository;

import com.telehealth.appointment.entity.InboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InboxEventRepository extends JpaRepository<InboxEventEntity, InboxEventEntity.InboxId> {
    boolean existsByConsumerNameAndEventId(String consumerName, UUID eventId);
}
