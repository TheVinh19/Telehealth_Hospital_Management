package com.telehealth.identity.controller;

import com.telehealth.contracts.dto.ApiResponse;
import com.telehealth.contracts.enums.Role;
import com.telehealth.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InternalNotificationContactController {
    private final AuthService authService;
    @Value("${services.internal-token:telehealth-local-internal-token-2026}")
    private String serviceToken;

    public record Contact(UUID id, String email, String displayName) {}

    @GetMapping("/api/v1/internal/users/{id}/notification-contact")
    public ResponseEntity<ApiResponse<Contact>> getContact(@PathVariable UUID id,
            @RequestHeader(value = "X-Service-Token", required = false) String suppliedToken) {
        if (serviceToken.isBlank() || suppliedToken == null || !MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Không có quyền đọc liên hệ."));
        }
        var user = authService.getUserById(id);
        if (user.getRole() != Role.PATIENT) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Người nhận phải là bệnh nhân."));
        }
        return ResponseEntity.ok(ApiResponse.ok(new Contact(user.getId(), user.getEmail(), user.getDisplayName())));
    }
}
