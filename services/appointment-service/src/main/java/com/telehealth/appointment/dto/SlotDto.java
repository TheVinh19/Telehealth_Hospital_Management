package com.telehealth.appointment.dto;

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
public class SlotDto {
    private UUID id;
    private UUID doctorId;
    private String doctorName;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant startsAt;
    private boolean available;
    private boolean enabled;
}
