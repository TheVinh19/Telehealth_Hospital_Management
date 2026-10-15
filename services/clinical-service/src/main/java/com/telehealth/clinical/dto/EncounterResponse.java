package com.telehealth.clinical.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncounterResponse {
    private UUID id;
    private UUID appointmentId;
    private UUID intakeId;
    private UUID patientId;
    private UUID doctorId;
    private String state;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant publishedAt;
    private List<ReportFileDto> files;
}
