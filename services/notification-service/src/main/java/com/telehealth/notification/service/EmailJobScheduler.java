package com.telehealth.notification.service;

import com.telehealth.notification.entity.EmailJobEntity;
import com.telehealth.notification.repository.EmailJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailJobScheduler {

    private final EmailJobRepository emailJobRepository;
    private final EmailSenderService emailSenderService;

    @Scheduled(fixedDelay = 15000)
    public void processPendingJobs() {
        List<EmailJobEntity> pendingJobs = emailJobRepository
                .findByStatusInAndNextAttemptAtLessThanEqual(List.of("PENDING", "FAILED"), Instant.now());

        for (EmailJobEntity job : pendingJobs) {
            try {
                emailSenderService.processJob(job.getId());
            } catch (Exception e) {
                log.error("Error processing email job {}", job.getId(), e);
            }
        }
    }
}
