package com.telehealth.appointment.controller;

import com.telehealth.appointment.dto.RoomAppointmentContext;
import com.telehealth.appointment.service.AppointmentService;
import com.telehealth.contracts.dto.ApiResponse;
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
public class InternalRoomContextController {
    private final AppointmentService appointmentService;

    @Value("${services.internal-token:telehealth-local-internal-token-2026}")
    private String serviceToken;

    @GetMapping("/api/v1/internal/appointments/{id}/room-context")
    public ResponseEntity<ApiResponse<RoomAppointmentContext>> getContext(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Service-Token", required = false) String suppliedToken) {
        if (serviceToken.isBlank() || suppliedToken == null || !MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Service authentication required"));
        }
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.getRoomContext(id)));
    }
}
