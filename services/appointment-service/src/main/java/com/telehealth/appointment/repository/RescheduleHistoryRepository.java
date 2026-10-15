package com.telehealth.appointment.repository;

import com.telehealth.appointment.entity.RescheduleHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RescheduleHistoryRepository extends JpaRepository<RescheduleHistoryEntity, UUID> {
    List<RescheduleHistoryEntity> findByAppointmentIdOrderByChangedAtDesc(UUID appointmentId);
}
