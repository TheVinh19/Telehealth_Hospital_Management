package com.telehealth.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookAppointmentRequest {
    @NotNull(message = "doctorId không được để trống")
    private UUID doctorId;

    @NotNull(message = "slotId không được để trống")
    private UUID slotId;

    @NotNull(message = "intakeId không được để trống")
    private UUID intakeId;
}
