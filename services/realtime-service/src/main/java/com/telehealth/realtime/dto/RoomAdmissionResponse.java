package com.telehealth.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomAdmissionResponse {
    private UUID roomId;
    private String token;
    private UUID appointmentId;
    private String role;
    private Integer admissionVersion;
}
