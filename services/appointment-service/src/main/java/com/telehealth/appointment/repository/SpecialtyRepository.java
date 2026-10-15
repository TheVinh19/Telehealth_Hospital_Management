package com.telehealth.appointment.repository;

import com.telehealth.appointment.entity.SpecialtyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpecialtyRepository extends JpaRepository<SpecialtyEntity, UUID> {
    Optional<SpecialtyEntity> findByNameIgnoreCase(String name);
    List<SpecialtyEntity> findByActiveTrue();
}
