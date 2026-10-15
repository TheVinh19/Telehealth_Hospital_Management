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

@SpringBootTest
class NotificationServiceApplicationTests {

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
}
