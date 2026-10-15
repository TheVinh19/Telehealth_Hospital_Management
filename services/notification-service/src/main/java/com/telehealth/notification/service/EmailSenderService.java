package com.telehealth.notification.service;

import com.telehealth.notification.entity.DeliveryAttemptEntity;
import com.telehealth.notification.entity.EmailJobEntity;
import com.telehealth.notification.repository.DeliveryAttemptRepository;
import com.telehealth.notification.repository.EmailJobRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSenderService {

    private final JavaMailSender mailSender;
    private final EmailJobRepository emailJobRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final EmailRenderer emailRenderer;

    @Value("${spring.mail.from:no-reply@telehealth.local}")
    private String mailFrom;

    @Transactional
    public EmailJobEntity queueEmail(UUID eventId, String recipient, String template, String payload) {
        // Idempotency: Deduplicate on (eventId, recipient, template)
        Optional<EmailJobEntity> existing = emailJobRepository
                .findByEventIdAndRecipientAndTemplate(eventId, recipient, template);
        if (existing.isPresent()) {
            log.info("Email job already queued for event {} recipient {}", eventId, recipient);
            return existing.get();
        }

        EmailJobEntity job = EmailJobEntity.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .recipient(recipient)
                .template(template)
                .payload(payload)
                .status("PENDING")
                .attempts(0)
                .nextAttemptAt(Instant.now())
                .build();

        return emailJobRepository.save(job);
    }

    @Transactional
    public boolean processJob(UUID jobId) {
        EmailJobEntity job = emailJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy email job"));

        if ("SENT".equalsIgnoreCase(job.getStatus())) {
            return true;
        }

        int currentAttempt = job.getAttempts() + 1;
        job.setAttempts(currentAttempt);
        job.setStatus("PROCESSING");

        EmailRenderer.RenderedEmail rendered;
        if ("APPOINTMENT_RESCHEDULED".equalsIgnoreCase(job.getTemplate())) {
            rendered = emailRenderer.renderRescheduled(job.getPayload());
        } else {
            rendered = emailRenderer.renderConfirmed(job.getPayload());
        }

        boolean success = false;
        String errorCode = null;
        String messageId = null;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(job.getRecipient());
            helper.setSubject(rendered.subject());
            helper.setText(rendered.htmlContent(), true);

            mailSender.send(message);

            success = true;
            messageId = "MSG-" + UUID.randomUUID();
            job.setStatus("SENT");
            job.setSentAt(Instant.now());
            log.info("Email successfully sent to {} for job {}", job.getRecipient(), job.getId());

        } catch (Exception e) {
            success = false;
            errorCode = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            job.setStatus("FAILED");
            // Exponential backoff retry: 1m, 2m, 4m, 8m...
            long delayMinutes = (long) Math.pow(2, Math.min(currentAttempt, 6));
            job.setNextAttemptAt(Instant.now().plus(delayMinutes, ChronoUnit.MINUTES));
            log.warn("Failed to send email to {} (attempt {}): {}", job.getRecipient(), currentAttempt, errorCode);
        }

        emailJobRepository.save(job);

        DeliveryAttemptEntity attempt = DeliveryAttemptEntity.builder()
                .jobId(job.getId())
                .attemptNumber(currentAttempt)
                .success(success)
                .providerMessageId(messageId)
                .errorCode(errorCode)
                .build();
        deliveryAttemptRepository.save(attempt);

        return success;
    }
}
