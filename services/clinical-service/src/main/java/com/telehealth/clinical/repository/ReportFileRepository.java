package com.telehealth.clinical.repository;

import com.telehealth.clinical.entity.ReportFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReportFileRepository extends JpaRepository<ReportFileEntity, UUID> {
    List<ReportFileEntity> findByEncounterId(UUID encounterId);
}
