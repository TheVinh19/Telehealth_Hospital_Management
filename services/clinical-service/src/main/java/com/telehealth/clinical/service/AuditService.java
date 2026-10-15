package com.telehealth.clinical.service;

import com.telehealth.clinical.dto.AuditLogDto;
import com.telehealth.clinical.entity.AuditLogEntity;
import com.telehealth.clinical.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String action, String resourceType, UUID resourceId, String result) {
        try {
            AuditLogEntity logEntity = AuditLogEntity.builder()
                    .actorId(actorId)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .result(result)
                    .correlationId(UUID.randomUUID())
                    .build();
            auditLogRepository.save(logEntity);
            log.info("Audit: actor={} action={} resource={}:{} result={}", actorId, action, resourceType, resourceId, result);
        } catch (Exception e) {
            log.error("Failed to record audit log", e);
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> getRecentLogs() {
        return auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(entity -> AuditLogDto.builder()
                        .id(entity.getId())
                        .actorId(entity.getActorId())
                        .action(entity.getAction())
                        .resourceType(entity.getResourceType())
                        .resourceId(entity.getResourceId())
                        .result(entity.getResult())
                        .createdAt(entity.getCreatedAt())
                        .build())
                .toList();
    }
}
