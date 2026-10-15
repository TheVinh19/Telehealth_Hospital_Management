package com.telehealth.appointment.repository;

import com.telehealth.appointment.entity.SlotEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SlotRepository extends JpaRepository<SlotEntity, UUID> {

    List<SlotEntity> findByDoctorId(UUID doctorId);

    List<SlotEntity> findByDoctorIdAndEnabledTrueOrderByStartsAtAsc(UUID doctorId);

    List<SlotEntity> findByDoctorIdAndStartsAtBetweenAndEnabledTrueOrderByStartsAtAsc(
            UUID doctorId, Instant start, Instant end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SlotEntity s WHERE s.id = :id")
    Optional<SlotEntity> findByIdWithLock(@Param("id") UUID id);

    boolean existsByDoctorIdAndStartsAt(UUID doctorId, Instant startsAt);
}
