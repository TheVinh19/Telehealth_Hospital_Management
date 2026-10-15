package com.telehealth.appointment.controller;

import com.telehealth.appointment.dto.*;
import com.telehealth.appointment.service.CatalogService;
import com.telehealth.contracts.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DoctorController {

    private final CatalogService catalogService;

    @GetMapping("/api/v1/doctors")
    public ResponseEntity<ApiResponse<List<DoctorDto>>> getDoctors(
            @RequestParam(required = false) UUID specialtyId) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.getDoctors(specialtyId)));
    }

    @GetMapping("/api/v1/doctors/{id}")
    public ResponseEntity<ApiResponse<DoctorDto>> getDoctorById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.getDoctorById(id)));
    }

    @GetMapping("/api/v1/doctors/{id}/slots")
    public ResponseEntity<ApiResponse<List<SlotDto>>> getDoctorSlots(
            @PathVariable UUID id,
            @RequestParam(required = false) String date) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.getSlots(id, date)));
    }

    @PostMapping("/api/v1/admin/doctors")
    public ResponseEntity<ApiResponse<DoctorDto>> createDoctor(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @Valid @RequestBody CreateDoctorProfileRequest request) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        DoctorDto dto = catalogService.createDoctor(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo hồ sơ bác sĩ thành công", dto));
    }

    @PostMapping("/api/v1/admin/slots")
    public ResponseEntity<ApiResponse<SlotDto>> createSlot(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @Valid @RequestBody CreateSlotRequest request) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        SlotDto dto = catalogService.createSlot(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo ca khám thành công", dto));
    }

    @DeleteMapping("/api/v1/admin/slots/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSlot(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        catalogService.deleteSlot(id);
        return ResponseEntity.ok(ApiResponse.ok("Xóa ca khám thành công", null));
    }

    @DeleteMapping("/api/v1/admin/doctors/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDoctor(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        catalogService.deleteDoctor(id);
        return ResponseEntity.ok(ApiResponse.ok("Xóa bác sĩ thành công", null));
    }
}
