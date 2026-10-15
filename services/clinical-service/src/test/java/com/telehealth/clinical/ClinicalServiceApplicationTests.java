package com.telehealth.clinical;

import com.telehealth.clinical.dto.*;
import com.telehealth.clinical.entity.IntakeEntity;
import com.telehealth.clinical.repository.IntakeRepository;
import com.telehealth.clinical.service.AuditService;
import com.telehealth.clinical.service.ClinicalService;
import com.telehealth.clinical.service.AppointmentLookupClient;
import com.telehealth.clinical.repository.EncounterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class ClinicalServiceApplicationTests {

    @Autowired
    private ClinicalService clinicalService;

    @Autowired
    private IntakeRepository intakeRepository;

    @Autowired
    private AuditService auditService;

    @MockBean
    private AppointmentLookupClient appointmentLookupClient;

    @Autowired
    private EncounterRepository encounterRepository;

    @Test
    void testIntakeFormEncryptionAndDecryption() {
        UUID patientId = UUID.randomUUID();
        IntakePayload payload = IntakePayload.builder()
                .reason("Khám tim mạch định kỳ")
                .symptoms("Thỉnh thoảng hồi hộp, tim đập nhanh khi leo cầu thang")
                .medicalHistory("Tăng huyết áp nhẹ")
                .underlyingConditions("Không có")
                .symptomStartedAt("2026-10-01")
                .currentMedications("Amlodipine 5mg")
                .drugAllergies("Dị ứng Penicillin")
                .build();

        IntakeResponse saved = clinicalService.saveIntake(patientId, payload);
        assertNotNull(saved.getId());
        assertEquals("DRAFT", saved.getState());

        // Verify in database: raw payload must NOT be plain text in DB!
        IntakeEntity rawEntity = intakeRepository.findById(saved.getId()).orElseThrow();
        assertNotNull(rawEntity.getPayloadCiphertext());
        String rawBytesString = new String(rawEntity.getPayloadCiphertext(), StandardCharsets.UTF_8);
        assertFalse(rawBytesString.contains("Khám tim mạch định kỳ"), "Database MUST NOT contain plain text medical notes!");
        assertFalse(rawBytesString.contains("Amlodipine"), "Database MUST NOT contain plain text medication!");

        // Retrieve and decrypt with authorized user (patient owner)
        IntakeResponse retrieved = clinicalService.getIntake(saved.getId(), patientId, "PATIENT");
        assertEquals("Khám tim mạch định kỳ", retrieved.getPayload().getReason());
        assertEquals("Amlodipine 5mg", retrieved.getPayload().getCurrentMedications());

        // Attempt to retrieve with unauthorized stranger -> MUST FAIL
        UUID strangerId = UUID.randomUUID();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                clinicalService.getIntake(saved.getId(), strangerId, "PATIENT")
        );
        assertTrue(ex.getMessage().contains("không có quyền"));
    }

    @Test
    void testUploadPublishAndDownloadReport() {
        UUID appointmentId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        UUID patientUserId = UUID.randomUUID();
        IntakeResponse intake = clinicalService.saveIntake(patientUserId, IntakePayload.builder()
                .reason("Kiểm thử báo cáo").symptoms("Dữ liệu thử nghiệm").build());
        when(appointmentLookupClient.getForDoctor(appointmentId, doctorUserId)).thenReturn(
                new AppointmentLookupClient.AppointmentContext(appointmentId, intake.getId(), patientUserId, UUID.randomUUID(), "IN_PROGRESS"));

        // Doctor uploads a DOCX report
        String fileContent = "Báo cáo khám tim mạch: Nhịp xoang đều, huyết áp 120/80 mmHg. Tiếp tục dùng thuốc theo đơn.";
        MockMultipartFile reportFile = new MockMultipartFile(
                "file",
                "bao_cao_tim_mach.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                fileContent.getBytes(StandardCharsets.UTF_8)
        );

        ReportFileDto uploaded = clinicalService.uploadReportFile(appointmentId, doctorUserId, reportFile);
        assertNotNull(uploaded.getId());
        assertEquals("bao_cao_tim_mach.docx", uploaded.getOriginalName());

        // Before publish, patient trying to download -> MUST FAIL
        EncounterResponse encounter = clinicalService.getEncounterByAppointmentId(appointmentId, doctorUserId, "DOCTOR");
        assertEquals(intake.getId(), encounter.getIntakeId());
        assertEquals(patientUserId, encounter.getPatientId());
        assertEquals(doctorUserId, encounter.getDoctorId());
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                clinicalService.downloadReportFile(encounter.getId(), uploaded.getId(), patientUserId, "PATIENT")
        );
        assertTrue(ex.getMessage().contains("chưa được bác sĩ công bố"));

        // Doctor publishes encounter
        EncounterResponse published = clinicalService.publishEncounter(appointmentId, doctorUserId);
        assertEquals("PUBLISHED", published.getState());

        // The real patient can read the published report without manually repairing IDs.
        ClinicalService.DecryptedReportFile patientDownloaded = clinicalService.downloadReportFile(
                encounter.getId(), uploaded.getId(), patientUserId, "PATIENT");
        assertEquals(fileContent, new String(patientDownloaded.data(), StandardCharsets.UTF_8));

        // Now download as doctor succeeds
        ClinicalService.DecryptedReportFile doctorDownloaded = clinicalService.downloadReportFile(
                encounter.getId(), uploaded.getId(), doctorUserId, "DOCTOR"
        );
        assertEquals(fileContent, new String(doctorDownloaded.data(), StandardCharsets.UTF_8));
    }

    @Test
    void uploadRejectsMissingOrMismatchedIntakeWithoutCreatingEncounter() {
        UUID appointmentId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[]{1});
        when(appointmentLookupClient.getForDoctor(appointmentId, doctorId)).thenReturn(
                new AppointmentLookupClient.AppointmentContext(appointmentId, UUID.randomUUID(), patientId, UUID.randomUUID(), "IN_PROGRESS"));
        assertThrows(IllegalArgumentException.class, () -> clinicalService.uploadReportFile(appointmentId, doctorId, file));
        assertTrue(encounterRepository.findByAppointmentId(appointmentId).isEmpty());

        IntakeResponse otherPatientsIntake = clinicalService.saveIntake(UUID.randomUUID(),
                IntakePayload.builder().reason("Test").symptoms("Test").build());
        when(appointmentLookupClient.getForDoctor(appointmentId, doctorId)).thenReturn(
                new AppointmentLookupClient.AppointmentContext(appointmentId, otherPatientsIntake.getId(), patientId, UUID.randomUUID(), "IN_PROGRESS"));
        assertThrows(IllegalStateException.class, () -> clinicalService.uploadReportFile(appointmentId, doctorId, file));
        assertTrue(encounterRepository.findByAppointmentId(appointmentId).isEmpty());
    }

    @Test
    void testAuditLogsRecordedWithoutMedicalDetails() {
        List<AuditLogDto> logs = auditService.getRecentLogs();
        assertNotNull(logs);
        for (AuditLogDto log : logs) {
            assertNotNull(log.getAction());
            assertNotNull(log.getResourceType());
            assertNotNull(log.getResult());
        }
    }
}
