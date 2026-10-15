package com.telehealth.realtime.repository;

import com.telehealth.realtime.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<RoomEntity, UUID> {
    Optional<RoomEntity> findByAppointmentId(UUID appointmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomEntity r where r.appointmentId = :appointmentId")
    Optional<RoomEntity> findByAppointmentIdForUpdate(UUID appointmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomEntity r where r.id = :roomId")
    Optional<RoomEntity> findByIdForUpdate(UUID roomId);
}
