package com.telehealth.clinical.controller;

import com.telehealth.clinical.dto.EncounterResponse;
import com.telehealth.clinical.dto.ReportFileDto;
import com.telehealth.clinical.service.ClinicalService;
import com.telehealth.contracts.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EncounterController {

    private final ClinicalService clinicalService;

    @GetMapping({
            "/api/v1/encounters/by-appointment/{appointmentId}",
            "/api/v1/encounters/{appointmentId}",
            "/api/v1/clinical/encounters/{appointmentId}"
    })
    public ResponseEntity<ApiResponse<EncounterResponse>> getEncounter(
            @PathVariable UUID appointmentId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {

        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Chưa đăng nhập"));
        }

        UUID actorId = UUID.fromString(userIdHeader);
        EncounterResponse response = clinicalService.getEncounterByAppointmentId(appointmentId, actorId, roleHeader);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping(
            value = {
                    "/api/v1/encounters/{appointmentId}/files",
                    "/api/v1/clinical/encounters/{appointmentId}/reports"
            },
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<ReportFileDto>> uploadFile(
            @PathVariable UUID appointmentId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestParam("file") MultipartFile file) {

        if (userIdHeader == null || userIdHeader.isBlank() || !"DOCTOR".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Chỉ bác sĩ mới có quyền tải lên file báo cáo"));
        }

        UUID doctorUserId = UUID.fromString(userIdHeader);
        ReportFileDto response = clinicalService.uploadReportFile(appointmentId, doctorUserId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tải lên file báo cáo thành công", response));
    }

    @PostMapping({
            "/api/v1/encounters/{appointmentId}/publish",
            "/api/v1/clinical/encounters/{appointmentId}/publish"
    })
    public ResponseEntity<ApiResponse<EncounterResponse>> publishEncounter(
            @PathVariable UUID appointmentId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {

        if (userIdHeader == null || userIdHeader.isBlank() || !"DOCTOR".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Chỉ bác sĩ mới có quyền công bố báo cáo"));
        }

        UUID doctorUserId = UUID.fromString(userIdHeader);
        EncounterResponse response = clinicalService.publishEncounter(appointmentId, doctorUserId);
        return ResponseEntity.ok(ApiResponse.ok("Công bố báo cáo khám thành công", response));
    }

    @GetMapping({
            "/api/v1/reports/{encounterId}/files/{fileId}/download",
            "/api/v1/clinical/encounters/{encounterId}/reports/{fileId}"
    })
    public ResponseEntity<byte[]> downloadReportFile(
            @PathVariable UUID encounterId,
            @PathVariable UUID fileId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {

        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID actorId = UUID.fromString(userIdHeader);
        ClinicalService.DecryptedReportFile file = clinicalService.downloadReportFile(encounterId, fileId, actorId, roleHeader);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .body(file.data());
    }
}
