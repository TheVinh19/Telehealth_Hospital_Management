package com.telehealth.realtime.service;

import com.telehealth.contracts.crypto.AesGcmCrypto;
import com.telehealth.realtime.entity.ChatMessageEntity;
import com.telehealth.realtime.entity.RoomEntity;
import com.telehealth.realtime.repository.ChatMessageRepository;
import com.telehealth.realtime.repository.RoomRepository;
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
public class RealtimeDataInitializer implements ApplicationRunner {

    private final RoomRepository roomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RealtimeCryptoService cryptoService;

    public static final UUID SAMPLE_APPT_1_ID = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
    public static final UUID PATIENT_1_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID DOCTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Override
    public void run(ApplicationArguments args) {
        seedSampleRoomAndChat();
    }

    private void seedSampleRoomAndChat() {
        RoomEntity room = roomRepository.findByAppointmentId(SAMPLE_APPT_1_ID)
                .orElseGet(() -> roomRepository.save(RoomEntity.builder()
                        .id(SAMPLE_APPT_1_ID)
                        .appointmentId(SAMPLE_APPT_1_ID)
                        .patientId(PATIENT_1_ID)
                        .doctorUserId(DOCTOR_ID)
                        .doctorId(DOCTOR_ID)
                        .scheduledAt(Instant.now().plus(1, ChronoUnit.DAYS))
                        .state("OPEN")
                        .admissionVersion(1)
                        .build()));

        if (chatMessageRepository.findByRoomIdOrderByIdAsc(room.getId()).isEmpty()) {
            // Message 1 from patient
            String text1 = "Chào bác sĩ Ánh, dạo gần đây tôi hay bị đau thắt ngực khi gắng sức hoặc leo cầu thang.";
            AesGcmCrypto.EncryptedData enc1 = cryptoService.encryptText(text1);
            chatMessageRepository.save(ChatMessageEntity.builder()
                    .roomId(room.getId())
                    .clientMessageId(UUID.randomUUID())
                    .senderId(PATIENT_1_ID)
                    .bodyCiphertext(enc1.ciphertext())
                    .keyId("v1")
                    .nonce(enc1.nonce())
                    .createdAt(Instant.now().minus(30, ChronoUnit.MINUTES))
                    .build());

            // Message 2 from doctor
            String text2 = "Chào anh Minh, bác sĩ đã nhận được biểu mẫu tiền khám và tiền sử tăng huyết áp của anh rồi nhé.";
            AesGcmCrypto.EncryptedData enc2 = cryptoService.encryptText(text2);
            chatMessageRepository.save(ChatMessageEntity.builder()
                    .roomId(room.getId())
                    .clientMessageId(UUID.randomUUID())
                    .senderId(DOCTOR_ID)
                    .bodyCiphertext(enc2.ciphertext())
                    .keyId("v1")
                    .nonce(enc2.nonce())
                    .createdAt(Instant.now().minus(28, ChronoUnit.MINUTES))
                    .build());

            log.info("Seeded demo room and chat messages for appointment {}", SAMPLE_APPT_1_ID);
        }
    }
}
