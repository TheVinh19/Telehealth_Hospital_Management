package com.telehealth.appointment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
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
public class CreateSlotRequest {
    @NotNull(message = "doctorId không được để trống")
    private UUID doctorId;

    @NotNull(message = "startsAt không được để trống")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant startsAt;
}
