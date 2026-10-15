package com.telehealth.identity.controller;

import com.telehealth.contracts.dto.ApiResponse;
import com.telehealth.identity.dto.CreateDoctorRequest;
import com.telehealth.identity.dto.UserResponse;
import com.telehealth.identity.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AuthService authService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        return ResponseEntity.ok(ApiResponse.ok(authService.getAllUsers()));
    }

    @PostMapping("/doctor")
    public ResponseEntity<ApiResponse<UserResponse>> createDoctor(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @Valid @RequestBody CreateDoctorRequest request) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        UserResponse doctor = authService.createDoctor(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo tài khoản bác sĩ thành công", doctor));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> deactivateDoctor(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                "Đã vô hiệu hóa tài khoản bác sĩ",
                authService.deactivateDoctor(id)));
    }
}
