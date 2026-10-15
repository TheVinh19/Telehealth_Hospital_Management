package com.telehealth.notification;

import com.telehealth.notification.entity.EmailJobEntity;
import com.telehealth.notification.repository.DeliveryAttemptRepository;
import com.telehealth.notification.repository.EmailJobRepository;
import com.telehealth.notification.service.EmailRecipientResolver;
import com.telehealth.notification.service.EmailRenderer;
import com.telehealth.notification.service.EmailSenderService;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpConfigurationTests {
    @Test
    void onlyTheTwoDedicatedDemoPatientsUseTheConfiguredInbox() {
        var resolver = new EmailRecipientResolver(" ngthevinhvjp@gmail.com ");
        for (String id : new String[]{"33333333-3333-3333-3333-333333333333", "44444444-4444-4444-4444-444444444444"}) {
            assertEquals("ngthevinhvjp@gmail.com", resolver.resolve(UUID.fromString(id), "patient@demo.com"));
        }
        assertEquals("real@example.com", resolver.resolve(UUID.randomUUID(), "real@example.com"));
        assertEquals("another@demo.com", resolver.resolve(UUID.randomUUID(), "another@demo.com"));
        assertEquals("original@example.com", resolver.resolve(null, "original@example.com"));
    }

    @Test
    void blankOverrideKeepsDemoLoginEmailAsTheRecipient() {
        var resolver = new EmailRecipientResolver("");
        assertEquals("patient1@demo.com", resolver.resolve(
                UUID.fromString("33333333-3333-3333-3333-333333333333"), "patient1@demo.com"));
    }

    @Test
    void missingSmtpPasswordDoesNotContactTheProviderOrPretendTheEmailWasSent() {
        var mailer = mock(JavaMailSender.class);
        var jobs = mock(EmailJobRepository.class);
        var attempts = mock(DeliveryAttemptRepository.class);
        var renderer = mock(EmailRenderer.class);
        var sender = new EmailSenderService(mailer, jobs, attempts, renderer);
        ReflectionTestUtils.setField(sender, "smtpAuth", true);
        ReflectionTestUtils.setField(sender, "smtpUsername", "sender@gmail.com");
        ReflectionTestUtils.setField(sender, "smtpPassword", "");
        var job = EmailJobEntity.builder().id(UUID.randomUUID()).eventId(UUID.randomUUID())
                .recipient("recipient@gmail.com").template("APPOINTMENT_CONFIRMED").payload("{}").build();
        when(jobs.findByIdWithLock(job.getId())).thenReturn(Optional.of(job));
        when(renderer.renderConfirmed("{}")).thenReturn(new EmailRenderer.RenderedEmail("Subject", "Body"));
        assertFalse(sender.processJob(job.getId()));
        assertEquals("FAILED", job.getStatus());
        assertNull(job.getSentAt());
        assertTrue(job.getNextAttemptAt().isAfter(Instant.now()));
        assertTrue(job.getNextAttemptAt().isBefore(Instant.now().plusSeconds(40)));
        verifyNoInteractions(mailer);
        verify(attempts).save(argThat(attempt -> !attempt.isSuccess() && attempt.getErrorCode().contains("SMTP")));
    }
}
