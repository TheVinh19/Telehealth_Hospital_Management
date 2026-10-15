package com.telehealth.identity.repository;

import com.telehealth.identity.entity.RefreshSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface RefreshSessionRepository extends JpaRepository<RefreshSessionEntity, UUID> {
    Optional<RefreshSessionEntity> findByTokenHash(String tokenHash);
    List<RefreshSessionEntity> findByUserId(UUID userId);
}
