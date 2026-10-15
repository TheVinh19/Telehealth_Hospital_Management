package com.telehealth.realtime.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class RealtimeTokenService {

    private final SecretKey signingKey;

    public RealtimeTokenService(
            @Value("${jwt.secret:telehealth_jwt_super_secret_key_minimum_256_bits_length_12345}") String secret) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(secret.getBytes(StandardCharsets.UTF_8));
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize signing key", e);
        }
    }

    public String generateRoomToken(UUID roomId, UUID userId, String role, int admissionVersion) {
        Instant now = Instant.now();
        Instant expiry = now.plus(4, ChronoUnit.HOURS); // valid during the consultation session

        return Jwts.builder()
                .subject(userId.toString())
                .claim("userId", userId.toString())
                .claim("roomId", roomId.toString())
                .claim("role", role)
                .claim("admissionVersion", admissionVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAndValidateToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
