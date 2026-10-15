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
public class AppointmentExpiredEvent {
    private UUID appointmentId;
    private UUID slotId;
    private UUID doctorId;
    private UUID patientId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant expiredAt;
}
