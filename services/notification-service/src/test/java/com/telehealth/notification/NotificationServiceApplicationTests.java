package com.telehealth.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telehealth.contracts.events.AppointmentConfirmedEvent;
import com.telehealth.contracts.events.AppointmentRescheduledEvent;
import com.telehealth.notification.entity.DeliveryAttemptEntity;
import com.telehealth.notification.entity.EmailJobEntity;
import com.telehealth.notification.repository.DeliveryAttemptRepository;
import com.telehealth.notification.repository.EmailJobRepository;
import com.telehealth.notification.service.EmailRenderer;
import com.telehealth.notification.service.EmailSenderService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationServiceApplicationTests {
    @Autowired MockMvc mvc;

    @Autowired
    private EmailSenderService emailSenderService;

    @Autowired
    private EmailRenderer emailRenderer;

    @Autowired
    private EmailJobRepository emailJobRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setupMockMail() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        Mockito.when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void testEmailRenderingDoesNotContainMedicalNotes() throws Exception {
        AppointmentConfirmedEvent event = AppointmentConfirmedEvent.builder()
                .appointmentId(UUID.randomUUID())
                .patientId(UUID.randomUUID())
                .doctorId(UUID.randomUUID())
                .doctorName("BS.CKII Nguyễn Văn Anh")
                .slotId(UUID.randomUUID())
                .startsAt(Instant.now())
                .confirmedAt(Instant.now())
                .amountVnd(100000L)
                .build();

        String payloadJson = objectMapper.writeValueAsString(event);
        EmailRenderer.RenderedEmail rendered = emailRenderer.renderConfirmed(payloadJson);

        assertNotNull(rendered.subject());
        assertTrue(rendered.subject().contains("Xác nhận"));
        assertTrue(rendered.htmlContent().contains("Nguyễn Văn Anh"));
        assertFalse(rendered.htmlContent().contains("triệu chứng"));
        assertFalse(rendered.htmlContent().contains("chẩn đoán"));
    }

    @Test
    void testEmailDeduplicationAndSuccessfulDelivery() throws Exception {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        UUID eventId = UUID.randomUUID();
        String recipient = "patient@demo.com";
        String template = "APPOINTMENT_CONFIRMED";
        String payload = "{\"appointmentId\": \"" + eventId + "\", \"doctorName\": \"BS. Lê Văn B\"}";

        // Queue job
        EmailJobEntity job1 = emailSenderService.queueEmail(eventId, recipient, template, payload);
        assertNotNull(job1.getId());

        // Queue duplicate job with same eventId and recipient
        EmailJobEntity job2 = emailSenderService.queueEmail(eventId, recipient, template, payload);
        assertEquals(job1.getId(), job2.getId(), "Duplicate email event must return existing job!");

        // Process job
        boolean success = emailSenderService.processJob(job1.getId());
        assertTrue(success);
        var mail = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(mail.capture());
        assertEquals(eventId.toString(), mail.getValue().getHeader("Resend-Idempotency-Key", null));

        // Check job state
        EmailJobEntity updatedJob = emailJobRepository.findById(job1.getId()).orElseThrow();
        assertEquals("SENT", updatedJob.getStatus());
        assertNotNull(updatedJob.getSentAt());

        // Check delivery attempt
        List<DeliveryAttemptEntity> attempts = deliveryAttemptRepository.findByJobIdOrderByAttemptNumberAsc(job1.getId());
        assertEquals(1, attempts.size());
        assertTrue(attempts.get(0).isSuccess());
    }

    @Test
    void testEmailFailureRecordsAttemptAndBackoff() {
        doThrow(new RuntimeException("SMTP connect failed")).when(mailSender).send(any(MimeMessage.class));

        UUID eventId = UUID.randomUUID();
        String recipient = "fail@demo.com";
        String template = "APPOINTMENT_CONFIRMED";
        String payload = "{\"appointmentId\": \"" + eventId + "\"}";

        EmailJobEntity job = emailSenderService.queueEmail(eventId, recipient, template, payload);

        // Process job - should fail gracefully without throwing exception out of processJob
        boolean success = emailSenderService.processJob(job.getId());
        assertFalse(success);

        // Check job status is FAILED and retry is scheduled
        EmailJobEntity updatedJob = emailJobRepository.findById(job.getId()).orElseThrow();
        assertEquals("FAILED", updatedJob.getStatus());
        assertEquals(1, updatedJob.getAttempts());
        assertTrue(updatedJob.getNextAttemptAt().isAfter(Instant.now()));

        // Check delivery attempt recorded failure
        List<DeliveryAttemptEntity> attempts = deliveryAttemptRepository.findByJobIdOrderByAttemptNumberAsc(job.getId());
        assertEquals(1, attempts.size());
        assertFalse(attempts.get(0).isSuccess());
        assertTrue(attempts.get(0).getErrorCode().contains("SMTP connect failed"));
    }

    @Test
    void internalEndpointRequiresAuthenticationAndAnActualRecipient() throws Exception {
        String payload = "{\"appointmentId\":\"" + UUID.randomUUID() + "\"}";
        mvc.perform(post("/api/v1/internal/notifications/confirmed").header("X-Event-Id", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/internal/notifications/confirmed").header("X-Event-Id", UUID.randomUUID())
                        .header("X-Service-Token", "telehealth-local-internal-token-2026")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replayingARescheduleDoesNotSendTwiceButAnotherRescheduleDoes() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(AppointmentRescheduledEvent.builder()
                .appointmentId(appointmentId).patientEmail("patient2@demo.com").doctorName("BS. Kiểm thử")
                .oldStartsAt(Instant.parse("2026-10-16T03:00:00Z"))
                .newStartsAt(Instant.parse("2026-10-17T04:00:00Z")).build());
        UUID firstEvent = UUID.randomUUID();
        for (UUID event : List.of(firstEvent, firstEvent, UUID.randomUUID())) {
            mvc.perform(post("/api/v1/internal/notifications/rescheduled").header("X-Event-Id", event)
                            .header("X-Service-Token", "telehealth-local-internal-token-2026")
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isOk());
        }
        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void logsAreAdminOnlyAndUseTheFieldsExpectedByTheWeb() throws Exception {
        emailSenderService.queueEmail(UUID.randomUUID(), "patient1@demo.com", "APPOINTMENT_CONFIRMED", "{}");
        mvc.perform(get("/api/v1/notifications/logs").header("X-User-Role", "PATIENT"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notifications/logs").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].recipientEmail").exists())
                .andExpect(jsonPath("$.data[0].subject").exists()).andExpect(jsonPath("$.data[0].retryCount").isNumber())
                .andExpect(jsonPath("$.data[0].payload").doesNotExist());
        mvc.perform(get("/api/v1/notifications/config").header("X-User-Role", "PATIENT"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notifications/config").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.credentialsConfigured").isBoolean())
                .andExpect(jsonPath("$.data.smtpPassword").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void templatesUseVietnamTimeEscapeNamesAndIncludePaymentWithoutClinicalDetails() {
        var confirmed = emailRenderer.renderConfirmed("{\"doctorName\":\"<script>test</script>\","
                + "\"startsAt\":\"2026-10-16T03:00:00Z\",\"amountVnd\":100000}");
        assertTrue(confirmed.htmlContent().contains("10:00 - Ngày 16/10/2026"));
        assertTrue(confirmed.htmlContent().contains("100.000 VNĐ"));
        assertTrue(confirmed.htmlContent().contains("&lt;script&gt;"));
        assertFalse(confirmed.htmlContent().contains("<script>"));
        var moved = emailRenderer.renderRescheduled("{\"oldStartsAt\":\"2026-10-16T03:00:00Z\","
                + "\"newStartsAt\":\"2026-10-17T04:00:00Z\"}");
        assertTrue(moved.htmlContent().contains("10:00 - Ngày 16/10/2026"));
        assertTrue(moved.htmlContent().contains("11:00 - Ngày 17/10/2026"));
    }
}
