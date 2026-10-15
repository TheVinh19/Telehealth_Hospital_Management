package com.telehealth.appointment.controller;

import com.telehealth.appointment.dto.CreateSpecialtyRequest;
import com.telehealth.appointment.dto.SpecialtyDto;
import com.telehealth.appointment.service.CatalogService;
import com.telehealth.contracts.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SpecialtyController {

    private final CatalogService catalogService;

    @GetMapping("/api/v1/specialties")
    public ResponseEntity<ApiResponse<List<SpecialtyDto>>> getSpecialties() {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.getPatientSpecialties()));
    }

    @GetMapping("/api/v1/admin/specialties")
    public ResponseEntity<ApiResponse<List<SpecialtyDto>>> getAdminSpecialties(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        return ResponseEntity.ok(ApiResponse.ok(catalogService.getAdminSpecialties()));
    }

    @PostMapping("/api/v1/admin/specialties")
    public ResponseEntity<ApiResponse<SpecialtyDto>> createSpecialty(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @Valid @RequestBody CreateSpecialtyRequest request) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        SpecialtyDto dto = catalogService.createSpecialty(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo chuyên khoa thành công", dto));
    }
}
