package com.telehealth.appointment.repository;

import com.telehealth.appointment.entity.IdempotencyRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequestEntity, IdempotencyRequestEntity.IdempotencyKeyId> {
    Optional<IdempotencyRequestEntity> findByPatientIdAndRequestKey(UUID patientId, String requestKey);
}
