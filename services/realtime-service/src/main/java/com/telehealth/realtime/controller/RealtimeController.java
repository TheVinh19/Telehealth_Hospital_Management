package com.telehealth.realtime.controller;

import com.telehealth.contracts.dto.ApiResponse;
import com.telehealth.realtime.dto.ChatMessageDto;
import com.telehealth.realtime.dto.RoomAdmissionResponse;
import com.telehealth.realtime.dto.WebRtcConfigResponse;
import com.telehealth.realtime.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequiredArgsConstructor
public class RealtimeController {

    private final RealtimeService realtimeService;

    @Value("${services.internal-token:telehealth-local-internal-token-2026}")
    private String internalToken;

    @PostMapping("/api/v1/internal/rooms/by-appointment/{appointmentId}/close")
    public ResponseEntity<ApiResponse<Void>> closeRoom(
            @PathVariable UUID appointmentId,
            @RequestHeader(value = "X-Service-Token", required = false) String suppliedToken) {
        if (internalToken.isBlank() || suppliedToken == null || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Không có quyền đóng phòng."));
        }
        realtimeService.closeByAppointment(appointmentId);
        return ResponseEntity.ok(ApiResponse.ok("Đã đóng phòng khám", null));
    }

    @PostMapping("/api/v1/rooms/{appointmentId}/token")
    public ResponseEntity<ApiResponse<RoomAdmissionResponse>> getRoomToken(
            @PathVariable UUID appointmentId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {

        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Chưa đăng nhập"));
        }

        UUID userId = UUID.fromString(userIdHeader);
        String role = roleHeader != null ? roleHeader : "PATIENT";
        RoomAdmissionResponse response = realtimeService.getOrCreateRoom(appointmentId, userId, role);
        return ResponseEntity.ok(ApiResponse.ok("Cấp vé vào phòng thành công", response));
    }

    @GetMapping("/api/v1/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageDto>>> getRoomMessages(
            @PathVariable UUID roomId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {

        if (userIdHeader == null || userIdHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Chưa đăng nhập"));
        }

        List<ChatMessageDto> messages = realtimeService.getMessages(roomId, UUID.fromString(userIdHeader), roleHeader);
        return ResponseEntity.ok(ApiResponse.ok(messages));
    }

    @GetMapping({"/api/v1/webrtc/config", "/api/v1/rooms/ice-servers"})
    public ResponseEntity<ApiResponse<WebRtcConfigResponse>> getWebRtcConfig() {
        return ResponseEntity.ok(ApiResponse.ok(realtimeService.getWebRtcConfig()));
    }
}
