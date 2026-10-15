package com.telehealth.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telehealth.contracts.dto.ApiResponse;
import com.telehealth.contracts.events.AppointmentConfirmedEvent;
import com.telehealth.contracts.events.AppointmentRescheduledEvent;
import com.telehealth.notification.entity.EmailJobEntity;
import com.telehealth.notification.repository.EmailJobRepository;
import com.telehealth.notification.service.EmailSenderService;
import com.telehealth.notification.service.EmailRecipientResolver;
import com.telehealth.notification.repository.DeliveryAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final EmailSenderService emailSenderService;
    private final EmailJobRepository emailJobRepository;
    private final ObjectMapper objectMapper;
    private final EmailRecipientResolver recipientResolver;
    private final DeliveryAttemptRepository attemptRepository;
    @Value("${services.internal-token:telehealth-local-internal-token-2026}")
    private String serviceToken;
    @Value("${spring.mail.host:localhost}") private String smtpHost;
    @Value("${spring.mail.port:1025}") private int smtpPort;
    @Value("${spring.mail.from:no-reply@telehealth.local}") private String mailFrom;
    @Value("${spring.mail.properties.mail.smtp.auth:false}") private boolean smtpAuth;
    @Value("${spring.mail.username:}") private String smtpUsername;
    @Value("${spring.mail.password:}") private String smtpPassword;

    public record DeliveryConfiguration(String smtpHost, int smtpPort, String sender,
                                        boolean credentialsConfigured, String demoRecipient) {}

    @GetMapping("/api/v1/notifications/config")
    public ResponseEntity<ApiResponse<DeliveryConfiguration>> getConfiguration(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        return ResponseEntity.ok(ApiResponse.ok(new DeliveryConfiguration(smtpHost, smtpPort, mailFrom,
                !smtpAuth || (!smtpUsername.isBlank() && !smtpPassword.isBlank()), recipientResolver.getDemoRecipient())));
    }

    public record EmailLog(UUID id, String recipientEmail, String subject, String status,
                           int retryCount, Instant createdAt, Instant sentAt, String lastError) {}

    @GetMapping("/api/v1/notifications/logs")
    public ResponseEntity<ApiResponse<List<EmailLog>>> getLogs(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        var logs = emailJobRepository.findTop50ByOrderByCreatedAtDesc().stream().map(job -> {
            String subject = "APPOINTMENT_RESCHEDULED".equals(job.getTemplate())
                    ? "Xác nhận đổi ca khám thành công" : "Xác nhận thanh toán/đặt lịch thành công";
            var attempts = attemptRepository.findByJobIdOrderByAttemptNumberAsc(job.getId());
            String error = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1).getErrorCode();
            return new EmailLog(job.getId(), job.getRecipient(), subject, job.getStatus(),
                    Math.max(0, job.getAttempts() - 1), job.getCreatedAt(), job.getSentAt(), error);
        }).toList();
        return ResponseEntity.ok(ApiResponse.ok(logs));
    }

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
            @RequestBody AppointmentConfirmedEvent event,
            @RequestHeader(value = "X-Service-Token", required = false) String token,
            @RequestHeader("X-Event-Id") UUID eventId) {
        if (!isService(token)) return forbidden();
        if (event.getPatientEmail() == null || event.getPatientEmail().isBlank() || event.getAppointmentId() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Thiếu email bệnh nhân hoặc mã lịch hẹn"));
        }
        try {
            String recipient = recipientResolver.resolve(event.getPatientId(), event.getPatientEmail());
            String payloadJson = objectMapper.writeValueAsString(event);
            EmailJobEntity job = emailSenderService.queueEmail(
                    eventId,
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
            @RequestBody AppointmentRescheduledEvent event,
            @RequestHeader(value = "X-Service-Token", required = false) String token,
            @RequestHeader("X-Event-Id") UUID eventId) {
        if (!isService(token)) return forbidden();
        if (event.getPatientEmail() == null || event.getPatientEmail().isBlank() || event.getAppointmentId() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Thiếu email bệnh nhân hoặc mã lịch hẹn"));
        }
        try {
            String recipient = recipientResolver.resolve(event.getPatientId(), event.getPatientEmail());
            String payloadJson = objectMapper.writeValueAsString(event);
            EmailJobEntity job = emailSenderService.queueEmail(
                    eventId, // Stable across retries; a different reschedule gets a new event id.
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

    private boolean isService(String supplied) {
        return !serviceToken.isBlank() && supplied != null && MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<ApiResponse<Void>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu dịch vụ nội bộ"));
    }
}
