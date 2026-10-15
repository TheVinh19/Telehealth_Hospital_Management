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
public class AuditLogDto {
    private Long id;
    private UUID actorId;
    private String action;
    private String resourceType;
    private UUID resourceId;
    private String result;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
}
