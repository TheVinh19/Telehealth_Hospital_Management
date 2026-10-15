package com.telehealth.appointment.service;

import com.telehealth.contracts.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class RealtimeRoomClient {
    private final RestClient client;
    private final String serviceToken;

    public RealtimeRoomClient(@Value("${services.realtime.url:http://localhost:8085}") String baseUrl,
                              @Value("${services.internal-token:telehealth-local-internal-token-2026}") String serviceToken) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(3_000);
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.serviceToken = serviceToken;
    }

    public void closeCompletedRoom(UUID appointmentId) {
        ApiResponse<Void> response = client.post()
                .uri("/api/v1/internal/rooms/by-appointment/{id}/close", appointmentId)
                .header("X-Service-Token", serviceToken)
                .retrieve().body(new ParameterizedTypeReference<>() {});
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("Realtime service did not acknowledge room closure");
        }
    }
}
