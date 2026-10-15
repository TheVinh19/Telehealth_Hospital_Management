package com.telehealth.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jwt.secret=telehealth-super-secret-jwt-key-2026-min-32-chars-long!"
})
class ApiGatewayApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void whenUnauthorizedRequestToProtectedEndpoint_thenReturns401() {
        webTestClient.get()
                .uri("/api/v1/appointments/my")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void whenInvalidToken_thenReturns401() {
        webTestClient.get()
                .uri("/api/v1/appointments/my")
                .header("Authorization", "Bearer invalid-token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void contextLoads() {
    }
}
