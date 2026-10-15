package com.telehealth.clinical.controller;

import com.telehealth.clinical.dto.AuditLogDto;
import com.telehealth.clinical.service.AuditService;
import com.telehealth.contracts.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLogDto>>> getAuditLogs(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {

        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }

        return ResponseEntity.ok(ApiResponse.ok(auditService.getRecentLogs()));
    }
}
