package com.telehealth.clinical.repository;

import com.telehealth.clinical.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findTop100ByOrderByCreatedAtDesc();
    List<AuditLogEntity> findByResourceIdOrderByCreatedAtDesc(UUID resourceId);
}
