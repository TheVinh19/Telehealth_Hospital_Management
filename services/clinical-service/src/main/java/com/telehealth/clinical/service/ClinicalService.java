package com.telehealth.clinical.service;

import com.telehealth.clinical.dto.*;
import com.telehealth.clinical.entity.*;
import com.telehealth.clinical.repository.*;
import com.telehealth.contracts.crypto.AesGcmCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicalService {

    private final IntakeRepository intakeRepository;
    private final EncounterRepository encounterRepository;
    private final ReportFileRepository reportFileRepository;
    private final ClinicalCryptoService cryptoService;
    private final ClinicalStorageService storageService;
    private final AuditService auditService;
    private final AppointmentLookupClient appointmentLookupClient;

    private static final long MAX_TOTAL_SIZE_BYTES = 20 * 1024 * 1024; // 20 MB

    @Transactional
    public IntakeResponse saveIntake(UUID patientId, IntakePayload payload) {
        AesGcmCrypto.EncryptedData encrypted = cryptoService.encryptPayload(payload);

        IntakeEntity intake = IntakeEntity.builder()
                .id(UUID.randomUUID())
                .patientId(patientId)
                .state("DRAFT")
                .payloadCiphertext(encrypted.ciphertext())
                .nonce(encrypted.nonce())
                .keyId("v1")
                .payloadVersion(1)
                .build();

        intake = intakeRepository.save(intake);
        auditService.record(patientId, "CREATE_INTAKE", "INTAKE", intake.getId(), "ALLOWED");

        return IntakeResponse.builder()
                .id(intake.getId())
                .patientId(intake.getPatientId())
                .state(intake.getState())
                .payload(payload)
                .createdAt(intake.getCreatedAt())
                .build();
    }

    @Transactional
    public IntakeResponse getIntake(UUID intakeId, UUID actorId, String role) {
        IntakeEntity intake = intakeRepository.findById(intakeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biểu mẫu khám"));

        // Check permission: patient owner, doctor, or admin
        boolean isOwner = intake.getPatientId().equals(actorId);
        boolean isDoctor = "DOCTOR".equalsIgnoreCase(role);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);

        if (!isOwner && !isDoctor && !isAdmin) {
            auditService.record(actorId, "VIEW_INTAKE", "INTAKE", intakeId, "DENIED");
            throw new IllegalStateException("Bạn không có quyền truy cập biểu mẫu khám này");
        }

        IntakePayload payload = cryptoService.decryptPayload(intake.getPayloadCiphertext(), intake.getNonce());
        auditService.record(actorId, "VIEW_INTAKE", "INTAKE", intakeId, "ALLOWED");

        return IntakeResponse.builder()
                .id(intake.getId())
                .patientId(intake.getPatientId())
                .state(intake.getState())
                .payload(payload)
                .createdAt(intake.getCreatedAt())
                .submittedAt(intake.getSubmittedAt())
                .build();
    }

    @Transactional
    public IntakeResponse submitIntake(UUID intakeId, UUID patientId) {
        IntakeEntity intake = intakeRepository.findById(intakeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biểu mẫu khám"));

        if (!intake.getPatientId().equals(patientId)) {
            auditService.record(patientId, "SUBMIT_INTAKE", "INTAKE", intakeId, "DENIED");
            throw new IllegalStateException("Chỉ chủ sở hữu biểu mẫu mới có quyền gửi biểu mẫu");
        }

        intake.setState("SUBMITTED");
        intake.setSubmittedAt(Instant.now());
        intake = intakeRepository.save(intake);

        auditService.record(patientId, "SUBMIT_INTAKE", "INTAKE", intakeId, "ALLOWED");

        IntakePayload payload = cryptoService.decryptPayload(intake.getPayloadCiphertext(), intake.getNonce());
        return IntakeResponse.builder()
                .id(intake.getId())
                .patientId(intake.getPatientId())
                .state(intake.getState())
                .payload(payload)
                .createdAt(intake.getCreatedAt())
                .submittedAt(intake.getSubmittedAt())
                .build();
    }

    @Transactional
    public EncounterEntity getOrCreateEncounter(UUID appointmentId, UUID intakeId, UUID patientId, UUID doctorId) {
        return encounterRepository.findByAppointmentId(appointmentId)
                .orElseGet(() -> encounterRepository.save(EncounterEntity.builder()
                        .id(UUID.randomUUID())
                        .appointmentId(appointmentId)
                        .intakeId(intakeId)
                        .patientId(patientId)
                        .doctorId(doctorId)
                        .state("DRAFT")
                        .build()));
    }

    @Transactional(readOnly = true)
    public EncounterResponse getEncounterByAppointmentId(UUID appointmentId, UUID actorId, String role) {
        EncounterEntity encounter = encounterRepository.findByAppointmentId(appointmentId)
                .orElse(null);

        if (encounter == null) {
            return null;
        }

        boolean isPatient = encounter.getPatientId().equals(actorId);
        boolean isDoctor = encounter.getDoctorId().equals(actorId) || "DOCTOR".equalsIgnoreCase(role);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);

        if (!isPatient && !isDoctor && !isAdmin) {
            auditService.record(actorId, "VIEW_ENCOUNTER", "ENCOUNTER", encounter.getId(), "DENIED");
            throw new IllegalStateException("Bạn không có quyền truy cập hồ sơ khám này");
        }

        List<ReportFileDto> files = reportFileRepository.findByEncounterId(encounter.getId()).stream()
                .map(f -> ReportFileDto.builder()
                        .id(f.getId())
                        .originalName(f.getOriginalName())
                        .mimeType(f.getMimeType())
                        .sizeBytes(f.getSizeBytes())
                        .state(f.getState())
                        .createdAt(f.getCreatedAt())
                        .downloadUrl("/api/v1/reports/" + encounter.getId() + "/files/" + f.getId() + "/download")
                        .build())
                .toList();

        auditService.record(actorId, "VIEW_ENCOUNTER", "ENCOUNTER", encounter.getId(), "ALLOWED");

        return EncounterResponse.builder()
                .id(encounter.getId())
                .appointmentId(encounter.getAppointmentId())
                .intakeId(encounter.getIntakeId())
                .patientId(encounter.getPatientId())
                .doctorId(encounter.getDoctorId())
                .state(encounter.getState())
                .createdAt(encounter.getCreatedAt())
                .publishedAt(encounter.getPublishedAt())
                .files(files)
                .build();
    }

    @Transactional
    public ReportFileDto uploadReportFile(UUID appointmentId, UUID doctorUserId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File tải lên không được rỗng");
        }

        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "report_file");
        String lowerName = originalName.toLowerCase();
        if (!lowerName.endsWith(".docx") && !lowerName.endsWith(".pdf") && !lowerName.endsWith(".zip")) {
            throw new IllegalArgumentException("Hệ thống chỉ hỗ trợ tài liệu định dạng DOCX, PDF hoặc ZIP");
        }

        // Resolve the actual appointment; never invent foreign keys or patient IDs.
        var appointment = appointmentLookupClient.getForDoctor(appointmentId, doctorUserId);
        IntakeEntity intake = intakeRepository.findById(appointment.intakeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biểu mẫu trước khám của lịch hẹn. Vui lòng bổ sung biểu mẫu trước khi tải báo cáo."));
        if (!intake.getPatientId().equals(appointment.patientId())) {
            throw new IllegalStateException("Biểu mẫu trước khám không thuộc bệnh nhân của lịch hẹn");
        }

        EncounterEntity encounter = encounterRepository.findByAppointmentId(appointmentId)
                .orElseGet(() -> EncounterEntity.builder()
                        .id(UUID.randomUUID())
                        .appointmentId(appointmentId)
                        .state("DRAFT")
                        .build());
        encounter.setIntakeId(intake.getId());
        encounter.setPatientId(appointment.patientId());
        encounter.setDoctorId(doctorUserId);
        encounter = encounterRepository.saveAndFlush(encounter);

        // Check cumulative size limit (max 20 MB)
        List<ReportFileEntity> existingFiles = reportFileRepository.findByEncounterId(encounter.getId());
        long currentTotal = existingFiles.stream().mapToLong(ReportFileEntity::getSizeBytes).sum();
        if (currentTotal + file.getSize() > MAX_TOTAL_SIZE_BYTES) {
            throw new IllegalArgumentException("Tổng dung lượng các file báo cáo vượt quá giới hạn 20 MB");
        }

        try {
            byte[] rawBytes = file.getBytes();
            String sha256 = ClinicalStorageService.computeSha256(rawBytes);

            // Encrypt file bytes with AES-256-GCM before saving to disk
            AesGcmCrypto.EncryptedData encrypted = cryptoService.encryptBytes(rawBytes);
            String storageKey = UUID.randomUUID() + "_" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");

            storageService.saveFile(storageKey, encrypted.ciphertext());

            ReportFileEntity reportFile = ReportFileEntity.builder()
                    .id(UUID.randomUUID())
                    .encounterId(encounter.getId())
                    .storageKey(storageKey)
                    .originalName(originalName)
                    .mimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .sizeBytes(file.getSize())
                    .sha256(sha256)
                    .state("READY")
                    .keyId("v1")
                    .nonce(encrypted.nonce())
                    .uploadedBy(doctorUserId)
                    .build();

            reportFile = reportFileRepository.save(reportFile);
            auditService.record(doctorUserId, "UPLOAD_REPORT_FILE", "REPORT_FILE", reportFile.getId(), "ALLOWED");

            return ReportFileDto.builder()
                    .id(reportFile.getId())
                    .originalName(reportFile.getOriginalName())
                    .mimeType(reportFile.getMimeType())
                    .sizeBytes(reportFile.getSizeBytes())
                    .state(reportFile.getState())
                    .createdAt(reportFile.getCreatedAt())
                    .downloadUrl("/api/v1/reports/" + encounter.getId() + "/files/" + reportFile.getId() + "/download")
                    .build();

        } catch (Exception e) {
            log.error("Failed to upload report file", e);
            auditService.record(doctorUserId, "UPLOAD_REPORT_FILE", "ENCOUNTER", encounter.getId(), "FAILED");
            throw new RuntimeException("Tải lên tài liệu thất bại: " + e.getMessage(), e);
        }
    }

    @Transactional
    public EncounterResponse publishEncounter(UUID appointmentId, UUID doctorUserId) {
        EncounterEntity encounter = encounterRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Chưa có hồ sơ khám hoặc file báo cáo cho ca khám này"));

        if (!encounter.getDoctorId().equals(doctorUserId)) {
            throw new IllegalStateException("Chỉ bác sĩ phụ trách mới có thể công bố báo cáo");
        }

        List<ReportFileEntity> files = reportFileRepository.findByEncounterId(encounter.getId());
        if (files.isEmpty() || files.stream().noneMatch(f -> "READY".equalsIgnoreCase(f.getState()))) {
            throw new IllegalStateException("Cần tải lên ít nhất một file báo cáo hợp lệ trước khi hoàn tất và công bố ca khám");
        }

        encounter.setState("PUBLISHED");
        encounter.setPublishedAt(Instant.now());
        encounter = encounterRepository.save(encounter);

        auditService.record(doctorUserId, "PUBLISH_ENCOUNTER", "ENCOUNTER", encounter.getId(), "ALLOWED");
        log.info("Encounter {} published by doctor {}", encounter.getId(), doctorUserId);

        return getEncounterByAppointmentId(appointmentId, doctorUserId, "DOCTOR");
    }

    @Transactional(readOnly = true)
    public record DecryptedReportFile(String filename, String mimeType, byte[] data) {}

    @Transactional(readOnly = true)
    public DecryptedReportFile downloadReportFile(UUID encounterId, UUID fileId, UUID actorId, String role) {
        EncounterEntity encounter = encounterRepository.findById(encounterId)
                .or(() -> encounterRepository.findByAppointmentId(encounterId))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ khám"));

        // Only allow download if encounter is published, or actor is the doctor
        boolean isDoctor = encounter.getDoctorId().equals(actorId) || "DOCTOR".equalsIgnoreCase(role);
        boolean isPatient = encounter.getPatientId().equals(actorId) || "PATIENT".equalsIgnoreCase(role);

        if (!isDoctor && !isPatient && !"ADMIN".equalsIgnoreCase(role)) {
            auditService.record(actorId, "DOWNLOAD_REPORT_FILE", "REPORT_FILE", fileId, "DENIED");
            throw new IllegalStateException("Bạn không có quyền tải file báo cáo này");
        }

        if (isPatient && !"PUBLISHED".equalsIgnoreCase(encounter.getState())) {
            auditService.record(actorId, "DOWNLOAD_REPORT_FILE", "REPORT_FILE", fileId, "DENIED");
            throw new IllegalStateException("Báo cáo khám chưa được bác sĩ công bố hoàn tất");
        }

        ReportFileEntity reportFile = reportFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy file báo cáo"));

        byte[] encryptedBytes = storageService.readFile(reportFile.getStorageKey());
        byte[] decryptedBytes = cryptoService.decryptBytes(encryptedBytes, reportFile.getNonce());

        auditService.record(actorId, "DOWNLOAD_REPORT_FILE", "REPORT_FILE", fileId, "ALLOWED");

        return new DecryptedReportFile(reportFile.getOriginalName(), reportFile.getMimeType(), decryptedBytes);
    }
}
