package com.telehealth.clinical.service;

import com.telehealth.clinical.dto.IntakePayload;
import com.telehealth.clinical.entity.EncounterEntity;
import com.telehealth.clinical.entity.IntakeEntity;
import com.telehealth.clinical.entity.ReportFileEntity;
import com.telehealth.clinical.repository.EncounterRepository;
import com.telehealth.clinical.repository.IntakeRepository;
import com.telehealth.clinical.repository.ReportFileRepository;
import com.telehealth.contracts.crypto.AesGcmCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClinicalDataInitializer implements ApplicationRunner {

    private final IntakeRepository intakeRepository;
    private final EncounterRepository encounterRepository;
    private final ReportFileRepository reportFileRepository;
    private final ClinicalCryptoService cryptoService;
    private final ClinicalStorageService storageService;

    public static final UUID PATIENT_1_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID DOCTOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    public static final UUID SAMPLE_APPT_1_ID = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
    public static final UUID SAMPLE_APPT_2_ID = UUID.fromString("aaaaaaaa-2222-2222-2222-222222222222");

    @Override
    public void run(ApplicationArguments args) {
        seedSampleIntake1();
        seedSampleIntake2AndEncounter();
    }

    private void seedSampleIntake1() {
        if (!intakeRepository.existsById(SAMPLE_APPT_1_ID)) {
            IntakePayload payload = IntakePayload.builder()
                    .reason("Đau tức ngực khi gắng sức")
                    .symptoms("Cảm giác nghẹt và đè nặng sau xương ức khoảng 10-15 phút khi leo cầu thang hoặc đi bộ nhanh, lan lên vai trái")
                    .medicalHistory("Tiền sử gia đình có bố bị bệnh mạch vành; bản thân chưa từng phẫu thuật")
                    .underlyingConditions("Cao huyết áp 2 năm nay, đang uống thuốc hàng ngày")
                    .symptomStartedAt("Khoảng 1 tuần trở lại đây")
                    .currentMedications("Amlodipine 5mg ngày 1 viên buổi sáng")
                    .drugAllergies("Không có tiền sử dị ứng thuốc")
                    .build();

            AesGcmCrypto.EncryptedData enc = cryptoService.encryptPayload(payload);
            IntakeEntity intake = IntakeEntity.builder()
                    .id(SAMPLE_APPT_1_ID)
                    .patientId(PATIENT_1_ID)
                    .state("SUBMITTED")
                    .payloadCiphertext(enc.ciphertext())
                    .nonce(enc.nonce())
                    .keyId("v1")
                    .payloadVersion(1)
                    .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                    .submittedAt(Instant.now().minus(55, ChronoUnit.MINUTES))
                    .build();

            intakeRepository.save(intake);
            log.info("Seeded demo intake 1: {}", SAMPLE_APPT_1_ID);
        }
    }

    private void seedSampleIntake2AndEncounter() {
        if (!intakeRepository.existsById(SAMPLE_APPT_2_ID)) {
            IntakePayload payload = IntakePayload.builder()
                    .reason("Khám định kỳ tim mạch & kiểm tra huyết áp")
                    .symptoms("Không khó thở, huyết áp tương đối ổn định 130/80 mmHg")
                    .medicalHistory("Cao huyết áp")
                    .underlyingConditions("Rối loạn lipid máu nhẹ")
                    .symptomStartedAt("Khám định kỳ hàng quý")
                    .currentMedications("Amlodipine 5mg, Atorvastatin 10mg")
                    .drugAllergies("Không có")
                    .build();

            AesGcmCrypto.EncryptedData enc = cryptoService.encryptPayload(payload);
            IntakeEntity intake = IntakeEntity.builder()
                    .id(SAMPLE_APPT_2_ID)
                    .patientId(PATIENT_1_ID)
                    .state("SUBMITTED")
                    .payloadCiphertext(enc.ciphertext())
                    .nonce(enc.nonce())
                    .keyId("v1")
                    .payloadVersion(1)
                    .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .submittedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .build();

            intakeRepository.save(intake);
            log.info("Seeded demo intake 2: {}", SAMPLE_APPT_2_ID);
        }

        // Seed encounter and published report file for sample appointment 2
        if (encounterRepository.findByAppointmentId(SAMPLE_APPT_2_ID).isEmpty()) {
            UUID encounterId = UUID.randomUUID();
            EncounterEntity encounter = EncounterEntity.builder()
                    .id(encounterId)
                    .appointmentId(SAMPLE_APPT_2_ID)
                    .intakeId(SAMPLE_APPT_2_ID)
                    .patientId(PATIENT_1_ID)
                    .doctorId(DOCTOR_ID)
                    .state("PUBLISHED")
                    .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                    .publishedAt(Instant.now().minus(23, ChronoUnit.HOURS))
                    .build();

            encounterRepository.save(encounter);

            // Generate sample report document content
            String samplePdfContent = "%PDF-1.4\n" +
                    "BÁO CÁO KẾT LUẬN TƯ VẤN KHÁM BỆNH TỪ XA\n" +
                    "Họ và tên bệnh nhân: Trần Quốc Minh\n" +
                    "Bác sĩ tư vấn: BS.CKII Nguyễn Thị Ánh (Khoa Tim mạch)\n" +
                    "Ngày khám: Hôm qua\n" +
                    "Chẩn đoán: Theo dõi Cơn đau thắt ngực ổn định trên nền Tăng huyết áp độ 1\n" +
                    "Lời khuyên & Chỉ định:\n" +
                    "- Duy trì thuốc Amlodipine 5mg 1 viên/ngày vào buổi sáng\n" +
                    "- Hạn chế ăn mặn, tăng cường vận động nhẹ nhàng (đi bộ 30 phút/ngày)\n" +
                    "- Thực hiện điện tâm đồ (ECG) và siêu âm tim tại cơ sở y tế gần nhất nếu cơn đau kéo dài trên 20 phút\n" +
                    "- Tái khám sau 1 tháng hoặc khi có triệu chứng đau ngực tăng dần.\n";

            byte[] rawBytes = samplePdfContent.getBytes(StandardCharsets.UTF_8);
            String sha256 = ClinicalStorageService.computeSha256(rawBytes);
            AesGcmCrypto.EncryptedData encryptedFile = cryptoService.encryptBytes(rawBytes);
            String storageKey = UUID.randomUUID() + "_BaoCaoKetLuan_KhamTimMach_TranQuocMinh.pdf";

            storageService.saveFile(storageKey, encryptedFile.ciphertext());

            ReportFileEntity reportFile = ReportFileEntity.builder()
                    .id(UUID.randomUUID())
                    .encounterId(encounterId)
                    .originalName("BaoCaoKetLuan_KhamTimMach_TranQuocMinh.pdf")
                    .mimeType("application/pdf")
                    .sizeBytes((long) rawBytes.length)
                    .storageKey(storageKey)
                    .keyId("v1")
                    .nonce(encryptedFile.nonce())
                    .sha256(sha256)
                    .state("PUBLISHED")
                    .uploadedBy(DOCTOR_ID)
                    .createdAt(Instant.now().minus(23, ChronoUnit.HOURS))
                    .build();

            reportFileRepository.save(reportFile);
            log.info("Seeded demo report file for appointment {}", SAMPLE_APPT_2_ID);
        }
    }
}
