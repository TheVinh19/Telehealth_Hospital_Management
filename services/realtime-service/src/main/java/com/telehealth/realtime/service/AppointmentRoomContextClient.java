package com.telehealth.realtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Appointment service is the authority for room participants and lifecycle. */
@Component
public class AppointmentRoomContextClient {
    private final RestClient client;
    private final String internalToken;

    public AppointmentRoomContextClient(
            @Value("${services.appointment-url:http://localhost:8082}") String appointmentUrl,
            @Value("${services.internal-token:telehealth-local-internal-token-2026}") String internalToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(appointmentUrl).requestFactory(factory).build();
        this.internalToken = internalToken;
    }

    public RoomContext getContext(UUID appointmentId) {
        try {
            JsonNode response = client.get()
                    .uri("/api/v1/internal/appointments/{id}/room-context", appointmentId)
                    .header("X-Service-Token", internalToken)
                    .retrieve().body(JsonNode.class);
            JsonNode data = response == null ? null : response.get("data");
            if (data == null || data.isNull()) {
                throw new IllegalStateException("Không xác minh được ca khám. Vui lòng thử lại.");
            }
            return new RoomContext(
                    UUID.fromString(data.path("appointmentId").asText()),
                    UUID.fromString(data.path("patientId").asText()),
                    UUID.fromString(data.path("doctorId").asText()),
                    UUID.fromString(data.path("doctorUserId").asText()),
                    Instant.parse(data.path("startsAt").asText()),
                    data.path("status").asText());
        } catch (RestClientException exception) {
            throw new IllegalStateException("Không xác minh được ca khám. Vui lòng thử lại.", exception);
        }
    }

    public record RoomContext(UUID appointmentId, UUID patientId, UUID doctorId,
                              UUID doctorUserId, Instant startsAt, String status) {
    }
}
