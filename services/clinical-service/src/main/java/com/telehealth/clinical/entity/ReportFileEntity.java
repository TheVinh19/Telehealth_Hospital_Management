package com.telehealth.clinical.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_files")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportFileEntity {

    @Id
    private UUID id;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Builder.Default
    @Column(nullable = false)
    private String state = "READY"; // UPLOADING, READY, FAILED

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Column(nullable = false)
    private byte[] nonce;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
