package com.telehealth.appointment.repository;

import com.telehealth.appointment.entity.DoctorEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorRepository extends JpaRepository<DoctorEntity, UUID> {
    Optional<DoctorEntity> findByUserId(UUID userId);
    List<DoctorEntity> findByActiveTrue();
    List<DoctorEntity> findBySpecialtyIdAndActiveTrue(UUID specialtyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DoctorEntity d WHERE d.id = :id")
    Optional<DoctorEntity> findByIdWithLock(@Param("id") UUID id);
}
