package com.telehealth.notification.service;

import com.telehealth.notification.entity.EmailJobEntity;
import com.telehealth.notification.repository.EmailJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDataInitializer implements ApplicationRunner {

    private final EmailJobRepository emailJobRepository;

    @Override
    public void run(ApplicationArguments args) {
        seedSampleEmailJobs();
    }

    private void seedSampleEmailJobs() {
        if (emailJobRepository.count() == 0) {
            EmailJobEntity job1 = EmailJobEntity.builder()
                    .id(UUID.randomUUID())
                    .eventId(UUID.randomUUID())
                    .recipient("patient1@demo.com")
                    .template("APPOINTMENT_CONFIRMED")
                    .payload("{\"appointmentId\":\"aaaaaaaa-1111-1111-1111-111111111111\",\"doctorName\":\"BS.CKII Nguyễn Thị Ánh\",\"specialtyName\":\"Tim mạch\",\"startsAt\":\"" + Instant.now().plus(1, ChronoUnit.DAYS) + "\"}")
                    .status("SENT")
                    .attempts(1)
                    .nextAttemptAt(Instant.now())
                    .sentAt(Instant.now().minus(50, ChronoUnit.MINUTES))
                    .createdAt(Instant.now().minus(50, ChronoUnit.MINUTES))
                    .build();
            emailJobRepository.save(job1);

            EmailJobEntity job2 = EmailJobEntity.builder()
                    .id(UUID.randomUUID())
                    .eventId(UUID.randomUUID())
                    .recipient("patient1@demo.com")
                    .template("APPOINTMENT_RESCHEDULED")
                    .payload("{\"appointmentId\":\"aaaaaaaa-2222-2222-2222-222222222222\",\"doctorName\":\"BS.CKII Nguyễn Thị Ánh\",\"specialtyName\":\"Tim mạch\"}")
                    .status("SENT")
                    .attempts(1)
                    .nextAttemptAt(Instant.now())
                    .sentAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .build();
            emailJobRepository.save(job2);

            log.info("Seeded demo email notification logs");
        }
    }
}
