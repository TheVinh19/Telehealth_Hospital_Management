package com.telehealth.appointment.repository;

import com.telehealth.appointment.entity.SlotClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SlotClaimRepository extends JpaRepository<SlotClaimEntity, UUID> {
    Optional<SlotClaimEntity> findByAppointmentId(UUID appointmentId);
    void deleteByAppointmentId(UUID appointmentId);
}
