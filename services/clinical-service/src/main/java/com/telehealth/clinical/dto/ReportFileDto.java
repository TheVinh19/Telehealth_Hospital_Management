package com.telehealth.clinical.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportFileDto {
    private UUID id;
    private String originalName;
    private String mimeType;
    private Long sizeBytes;
    private String state;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
    private String downloadUrl;
}
