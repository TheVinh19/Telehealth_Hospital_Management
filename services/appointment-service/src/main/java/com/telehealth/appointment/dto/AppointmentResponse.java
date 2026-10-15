package com.telehealth.appointment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.telehealth.contracts.enums.AppointmentStatus;
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
public class AppointmentResponse {
    private UUID id;
    private UUID patientId;
    private UUID doctorId;
    private String doctorName;
    private String specialtyName;
    private UUID slotId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant startsAt;
    private UUID intakeId;
    private AppointmentStatus status;
    private Long amountVnd;
    private String currency;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant expiresAt;
    private long remainingSeconds;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant confirmedAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant startedAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant completedAt;
    private Integer version;
    private String paymentCode;
    private boolean canReschedule;
    private boolean demo;
}
