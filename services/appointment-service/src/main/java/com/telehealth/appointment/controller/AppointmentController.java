package com.telehealth.appointment.controller;

import com.telehealth.appointment.dto.AppointmentResponse;
import com.telehealth.appointment.dto.BookAppointmentRequest;
import com.telehealth.appointment.dto.RescheduleRequest;
import com.telehealth.appointment.service.AppointmentService;
import com.telehealth.contracts.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping("/api/v1/appointments")
    public ResponseEntity<ApiResponse<AppointmentResponse>> bookAppointment(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BookAppointmentRequest request) {

        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Chưa đăng nhập"));
        }

        UUID patientId = UUID.fromString(userIdHeader);
        AppointmentResponse response = appointmentService.holdAppointment(patientId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Giữ ca khám thành công trong 7 phút", response));
    }

    @GetMapping("/api/v1/appointments")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAppointments(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {

        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Chưa đăng nhập"));
        }

        UUID userId = UUID.fromString(userIdHeader);
        List<AppointmentResponse> list;
        if ("DOCTOR".equalsIgnoreCase(roleHeader)) {
            list = appointmentService.getAppointmentsForDoctor(userId);
        } else {
            list = appointmentService.getAppointmentsForPatient(userId);
        }

        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @GetMapping("/api/v1/appointments/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointmentById(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {

        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Chưa đăng nhập"));
        }

        UUID userId = UUID.fromString(userIdHeader);
        AppointmentResponse response = appointmentService.getAppointmentById(id, userId, roleHeader);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/api/v1/appointments/{id}/reschedule")
    public ResponseEntity<ApiResponse<AppointmentResponse>> reschedule(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @Valid @RequestBody RescheduleRequest request) {

        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Chưa đăng nhập"));
        }

        UUID patientId = UUID.fromString(userIdHeader);
        AppointmentResponse response = appointmentService.reschedule(id, patientId, request.getNewSlotId());
        return ResponseEntity.ok(ApiResponse.ok("Đổi ca khám thành công", response));
    }

    @PostMapping("/api/v1/appointments/{id}/start")
    public ResponseEntity<ApiResponse<AppointmentResponse>> startConsultation(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {

        if (userIdHeader == null || userIdHeader.isBlank() || !"DOCTOR".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền bác sĩ"));
        }

        UUID doctorUserId = UUID.fromString(userIdHeader);
        AppointmentResponse response = appointmentService.startConsultation(id, doctorUserId);
        return ResponseEntity.ok(ApiResponse.ok("Bắt đầu ca khám thành công", response));
    }

    @PostMapping("/api/v1/appointments/{id}/complete")
    public ResponseEntity<ApiResponse<AppointmentResponse>> completeConsultation(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {

        if (userIdHeader == null || userIdHeader.isBlank() || !"DOCTOR".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền bác sĩ"));
        }

        UUID doctorUserId = UUID.fromString(userIdHeader);
        AppointmentResponse response = appointmentService.completeConsultation(id, doctorUserId);
        return ResponseEntity.ok(ApiResponse.ok("Hoàn tất ca khám thành công", response));
    }

    @PostMapping("/api/v1/internal/appointments/{id}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmAppointment(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        Long amount = body != null && body.containsKey("amountVnd") ? ((Number) body.get("amountVnd")).longValue() : 100000L;
        String paymentCode = body != null && body.containsKey("paymentCode") ? (String) body.get("paymentCode") : null;
        appointmentService.confirmAppointment(id, amount, paymentCode);
        return ResponseEntity.ok(ApiResponse.ok("Xác nhận lịch khám thành công", null));
    }
}
