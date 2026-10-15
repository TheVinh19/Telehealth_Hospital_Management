package com.telehealth.appointment.dto;

import java.time.Instant;
import java.util.UUID;

public record RoomAppointmentContext(UUID appointmentId, UUID patientId, UUID doctorId,
                                     UUID doctorUserId, Instant startsAt, String status) {}
