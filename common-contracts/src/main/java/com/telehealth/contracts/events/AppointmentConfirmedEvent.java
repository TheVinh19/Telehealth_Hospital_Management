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
public class AppointmentConfirmedEvent {
    private UUID appointmentId;
    private UUID patientId;
    private String patientEmail;
    private String patientName;
    private UUID doctorId;
    private String doctorName;
    private UUID slotId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant startsAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant confirmedAt;
    private Long amountVnd;
}
