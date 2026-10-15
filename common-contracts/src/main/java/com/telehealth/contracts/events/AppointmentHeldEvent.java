package com.telehealth.contracts.events;

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
public class AppointmentHeldEvent {
    private UUID appointmentId;
    private UUID patientId;
    private UUID doctorId;
    private UUID slotId;
    private UUID intakeId;
    private Long amountVnd;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant startsAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant expiresAt;
}
