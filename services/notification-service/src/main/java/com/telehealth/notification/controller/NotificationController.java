package com.telehealth.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telehealth.contracts.dto.ApiResponse;
import com.telehealth.contracts.events.AppointmentConfirmedEvent;
import com.telehealth.contracts.events.AppointmentRescheduledEvent;
import com.telehealth.notification.entity.EmailJobEntity;
import com.telehealth.notification.repository.EmailJobRepository;
import com.telehealth.notification.service.EmailSenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final EmailSenderService emailSenderService;
    private final EmailJobRepository emailJobRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/api/v1/admin/notifications/jobs")
    public ResponseEntity<ApiResponse<List<EmailJobEntity>>> getEmailJobs(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {

        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }

        return ResponseEntity.ok(ApiResponse.ok(emailJobRepository.findTop50ByOrderByCreatedAtDesc()));
    }

    @PostMapping("/api/v1/internal/notifications/confirmed")
    public ResponseEntity<ApiResponse<Void>> queueConfirmedNotification(
            @RequestBody AppointmentConfirmedEvent event) {
        try {
            String recipient = event.getPatientEmail() != null ? event.getPatientEmail() : "patient@demo.com";
            String payloadJson = objectMapper.writeValueAsString(event);
            EmailJobEntity job = emailSenderService.queueEmail(
                    event.getAppointmentId(),
                    recipient,
                    "APPOINTMENT_CONFIRMED",
                    payloadJson
            );
            // Process immediately
            emailSenderService.processJob(job.getId());
            return ResponseEntity.ok(ApiResponse.ok("Đã xếp hàng gửi email xác nhận", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Lỗi xếp hàng email: " + e.getMessage()));
        }
    }

    @PostMapping("/api/v1/internal/notifications/rescheduled")
    public ResponseEntity<ApiResponse<Void>> queueRescheduledNotification(
            @RequestBody AppointmentRescheduledEvent event) {
        try {
            String recipient = event.getPatientEmail() != null ? event.getPatientEmail() : "patient@demo.com";
            String payloadJson = objectMapper.writeValueAsString(event);
            EmailJobEntity job = emailSenderService.queueEmail(
                    UUID.randomUUID(), // unique per reschedule
                    recipient,
                    "APPOINTMENT_RESCHEDULED",
                    payloadJson
            );
            // Process immediately
            emailSenderService.processJob(job.getId());
            return ResponseEntity.ok(ApiResponse.ok("Đã xếp hàng gửi email đổi ca", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Lỗi xếp hàng email: " + e.getMessage()));
        }
    }
}
