package com.telehealth.appointment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.telehealth.appointment.entity.OutboxEventEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PatientNotificationClient {
    private final RestClient identity;
    private final RestClient notifications;
    private final ObjectMapper mapper;
    private final String serviceToken;

    public PatientNotificationClient(ObjectMapper mapper,
            @Value("${services.identity.url:http://localhost:8081}") String identityUrl,
            @Value("${services.notification.url:http://localhost:8086}") String notificationUrl,
            @Value("${services.internal-token:telehealth-local-internal-token-2026}") String serviceToken) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(5_000);
        identity = RestClient.builder().baseUrl(identityUrl).requestFactory(factory).build();
        notifications = RestClient.builder().baseUrl(notificationUrl).requestFactory(factory).build();
        this.mapper = mapper;
        this.serviceToken = serviceToken;
    }

    public void deliver(OutboxEventEntity event) throws Exception {
        ObjectNode payload = (ObjectNode) mapper.readTree(event.getPayload());
        JsonNode contactResponse = identity.get()
                .uri("/api/v1/internal/users/{id}/notification-contact", payload.path("patientId").asText())
                .header("X-Service-Token", serviceToken).retrieve().body(JsonNode.class);
        JsonNode contact = contactResponse == null ? null : contactResponse.get("data");
        if (contact == null || contact.path("email").asText().isBlank()) {
            throw new IllegalStateException("Không tìm thấy email bệnh nhân");
        }
        payload.put("patientEmail", contact.path("email").asText());
        payload.put("patientName", contact.path("displayName").asText());
        String action = PatientNotificationDelivery.CONFIRMED.equals(event.getEventType()) ? "confirmed" : "rescheduled";
        JsonNode response = notifications.post().uri("/api/v1/internal/notifications/" + action)
                .header("X-Service-Token", serviceToken)
                .header("X-Event-Id", event.getEventId().toString())
                .body(payload).retrieve().body(JsonNode.class);
        if (response == null || !response.path("success").asBoolean()) {
            throw new IllegalStateException("Dịch vụ email chưa nhận yêu cầu");
        }
    }
}
