package com.telehealth.appointment.controller;

import com.telehealth.appointment.dto.AppointmentResponse;
import com.telehealth.appointment.service.AppointmentService;
import com.telehealth.contracts.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminAppointmentController {
    private final AppointmentService appointmentService;

    @GetMapping("/api/v1/admin/appointments/demo")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getDemoAppointments(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.getDemoAppointments()));
    }

    @DeleteMapping("/api/v1/admin/appointments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDemoAppointment(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) UUID adminId) {
        if (!"ADMIN".equalsIgnoreCase(role) || adminId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        appointmentService.deleteDemoAppointment(id, adminId);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa lịch khám demo", null));
    }
}
