package com.telehealth.clinical.controller;

import com.telehealth.clinical.dto.IntakePayload;
import com.telehealth.clinical.dto.IntakeResponse;
import com.telehealth.clinical.service.ClinicalService;
import com.telehealth.contracts.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/intakes")
@RequiredArgsConstructor
public class IntakeController {

    private final ClinicalService clinicalService;

    @PostMapping
    public ResponseEntity<ApiResponse<IntakeResponse>> saveIntake(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @Valid @RequestBody IntakePayload payload) {

        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Chưa đăng nhập"));
        }

        UUID patientId = UUID.fromString(userIdHeader);
        IntakeResponse response = clinicalService.saveIntake(patientId, payload);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Lưu biểu mẫu trước khám thành công", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IntakeResponse>> getIntake(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {

        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Chưa đăng nhập"));
        }

        UUID actorId = UUID.fromString(userIdHeader);
        IntakeResponse response = clinicalService.getIntake(id, actorId, roleHeader);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<IntakeResponse>> submitIntake(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {

        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Chưa đăng nhập"));
        }

        UUID patientId = UUID.fromString(userIdHeader);
        IntakeResponse response = clinicalService.submitIntake(id, patientId);
        return ResponseEntity.ok(ApiResponse.ok("Chốt biểu mẫu trước khám thành công", response));
    }
}
