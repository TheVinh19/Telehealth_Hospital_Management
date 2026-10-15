package com.telehealth.appointment.repository;

import com.telehealth.appointment.entity.AppointmentEntity;
import com.telehealth.contracts.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<AppointmentEntity, UUID> {

    List<AppointmentEntity> findByDemoTrueOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AppointmentEntity a WHERE a.id = :id")
    Optional<AppointmentEntity> findByIdWithLock(@Param("id") UUID id);

    // Seed must not recreate a demo appointment that an admin already removed.
    @Query(value = "SELECT COUNT(*) FROM appointments WHERE id = :id", nativeQuery = true)
    long countStoredById(@Param("id") UUID id);

    List<AppointmentEntity> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    List<AppointmentEntity> findByDoctorIdOrderByCreatedAtDesc(UUID doctorId);

    Optional<AppointmentEntity> findByIntakeId(UUID intakeId);

    Optional<AppointmentEntity> findBySlotIdAndStatusNot(UUID slotId, AppointmentStatus status);

    @Query("SELECT a FROM AppointmentEntity a WHERE a.status = 'PENDING_PAYMENT' AND a.expiresAt < :now")
    List<AppointmentEntity> findExpiredPendingAppointments(@Param("now") Instant now);

    boolean existsByDoctorIdAndStatus(UUID doctorId, AppointmentStatus status);

    boolean existsBySlotIdAndStatusIn(UUID slotId, Collection<AppointmentStatus> statuses);

    boolean existsByDoctorIdAndStatusIn(UUID doctorId, Collection<AppointmentStatus> statuses);
}
