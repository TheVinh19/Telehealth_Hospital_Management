package com.telehealth.clinical.repository;

import com.telehealth.clinical.entity.EncounterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncounterRepository extends JpaRepository<EncounterEntity, UUID> {
    Optional<EncounterEntity> findByAppointmentId(UUID appointmentId);
}
