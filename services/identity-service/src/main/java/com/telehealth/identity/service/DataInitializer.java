package com.telehealth.identity.service;

import com.telehealth.contracts.enums.Role;
import com.telehealth.identity.entity.UserEntity;
import com.telehealth.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public static final UUID ADMIN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID DOCTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID DOCTOR_2_ID = UUID.fromString("22222222-2222-2222-2222-222222222223");
    public static final UUID PATIENT_1_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID PATIENT_2_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Override
    public void run(ApplicationArguments args) {
        seedUser(ADMIN_ID, "admin@demo.com", "admin123", Role.ADMIN, "Quản Trị Viên");
        seedUser(DOCTOR_ID, "doctor.anh@demo.com", "doctor123", Role.DOCTOR, "BS.CKII Nguyễn Thị Ánh");
        seedUser(DOCTOR_2_ID, "doctor.binh@demo.com", "doctor123", Role.DOCTOR, "BS.CKI Trần Văn Bình");
        seedUser(PATIENT_1_ID, "patient1@demo.com", "patient123", Role.PATIENT, "Trần Quốc Minh");
        seedUser(PATIENT_2_ID, "patient2@demo.com", "patient123", Role.PATIENT, "Lê Thu Hương");
    }

    private void seedUser(UUID id, String email, String password, Role role, String displayName) {
        userRepository.findByEmailIgnoreCase(email).ifPresentOrElse(user -> {
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setDisplayName(displayName);
            userRepository.save(user);
            log.info("Updated existing demo user password: {} ({})", email, role);
        }, () -> {
            UserEntity user = UserEntity.builder()
                    .id(id)
                    .email(email.toLowerCase())
                    .passwordHash(passwordEncoder.encode(password))
                    .role(role)
                    .displayName(displayName)
                    .status("ACTIVE")
                    .build();
            userRepository.save(user);
            log.info("Seeded demo user: {} ({})", email, role);
        });
    }
}
