package com.telehealth.realtime.service;

import java.util.UUID;

public record RoomClosedEvent(UUID roomId, UUID appointmentId) {
}
