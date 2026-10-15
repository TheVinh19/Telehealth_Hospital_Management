package com.telehealth.realtime.service;

import java.util.UUID;

public class RoomClosedException extends IllegalStateException {
    private final UUID appointmentId;

    public RoomClosedException(UUID appointmentId) {
        super("Ca khám đã kết thúc, không thể vào phòng hoặc gửi tin nhắn mới.");
        this.appointmentId = appointmentId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }
}
