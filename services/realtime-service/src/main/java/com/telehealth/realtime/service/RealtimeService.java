package com.telehealth.realtime.service;

import com.telehealth.contracts.crypto.AesGcmCrypto;
import com.telehealth.realtime.dto.ChatMessageDto;
import com.telehealth.realtime.dto.RoomAdmissionResponse;
import com.telehealth.realtime.dto.WebRtcConfigResponse;
import com.telehealth.realtime.entity.ChatMessageEntity;
import com.telehealth.realtime.entity.RoomEntity;
import com.telehealth.realtime.repository.ChatMessageRepository;
import com.telehealth.realtime.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeService {

    private final RoomRepository roomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RealtimeCryptoService cryptoService;
    private final RealtimeTokenService tokenService;
    private final AppointmentRoomContextClient appointmentClient;
    private final ApplicationEventPublisher events;

    @Value("${webrtc.stun.url:stun:stun.l.google.com:19302}")
    private String stunUrl;

    @Value("${webrtc.turn.url:turn:localhost:3478}")
    private String turnUrl;

    @Value("${webrtc.turn.username:telehealth}")
    private String turnUsername;

    @Value("${webrtc.turn.credential:telehealth_turn_shared_secret_2026}")
    private String turnCredential;

    @Transactional
    public RoomAdmissionResponse getOrCreateRoom(UUID appointmentId, UUID userId, String role) {
        AppointmentRoomContextClient.RoomContext context = appointmentClient.getContext(appointmentId);
        String normalizedRole = normalizeRole(role);
        requireParticipant(context, userId, normalizedRole);
        requireActiveAppointment(context);
        RoomEntity room = roomRepository.findByAppointmentIdForUpdate(appointmentId)
                .orElseGet(() -> {
                    RoomEntity newRoom = RoomEntity.builder()
                            .id(UUID.randomUUID())
                            .appointmentId(appointmentId)
                            .patientId(context.patientId())
                            .doctorUserId(context.doctorUserId())
                            .doctorId(context.doctorId())
                            .scheduledAt(context.startsAt())
                            .admissionVersion(1)
                            .state("OPEN")
                            .build();
                    return roomRepository.save(newRoom);
                });

        requireOpenRoom(room);
        // Repair legacy placeholders only from appointment authority, never
        // from the identity supplied by a caller requesting admission.
        room.setPatientId(context.patientId());
        room.setDoctorUserId(context.doctorUserId());
        room.setDoctorId(context.doctorId());
        room.setScheduledAt(context.startsAt());

        String token = tokenService.generateRoomToken(room.getId(), userId, normalizedRole, room.getAdmissionVersion());

        return RoomAdmissionResponse.builder()
                .roomId(room.getId())
                .appointmentId(appointmentId)
                .token(token)
                .role(normalizedRole)
                .admissionVersion(room.getAdmissionVersion())
                .build();
    }

    @Transactional
    public void closeByAppointment(UUID appointmentId) {
        RoomEntity room = roomRepository.findByAppointmentIdForUpdate(appointmentId).orElseGet(() -> {
            // Keep a tombstone even when nobody has requested a room yet. An
            // admission racing completion must never recreate an OPEN room.
            AppointmentRoomContextClient.RoomContext context = appointmentClient.getContext(appointmentId);
            return roomRepository.save(RoomEntity.builder()
                    .id(UUID.randomUUID())
                    .appointmentId(appointmentId)
                    .patientId(context.patientId())
                    .doctorUserId(context.doctorUserId())
                    .doctorId(context.doctorId())
                    .scheduledAt(context.startsAt())
                    .state("OPEN")
                    .admissionVersion(1)
                    .build());
        });
        if (!"CLOSED".equals(room.getState())) {
            room.setState("CLOSED");
            room.setClosedAt(Instant.now());
            room.setAdmissionVersion(room.getAdmissionVersion() + 1);
        }
        // Delivery is retriable. Repeated close requests may notify a
        // surviving socket again, but cannot reopen or re-version a room.
        events.publishEvent(new RoomClosedEvent(room.getId(), appointmentId));
    }

    @Transactional(readOnly = true)
    public void assertActiveSession(UUID roomId, UUID userId, String role, int admissionVersion) {
        RoomEntity room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Phòng khám không tồn tại"));
        requireOpenRoom(room);
        if (room.getAdmissionVersion() != admissionVersion) {
            throw new IllegalStateException("Vé vào phòng không còn hiệu lực.");
        }
        AppointmentRoomContextClient.RoomContext context = appointmentClient.getContext(room.getAppointmentId());
        requireParticipant(context, userId, normalizeRole(role));
        requireActiveAppointment(context);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(UUID roomOrAppointmentId, UUID userId, String role) {
        Optional<RoomEntity> room = roomRepository.findById(roomOrAppointmentId)
                .or(() -> roomRepository.findByAppointmentId(roomOrAppointmentId));
        if (room.isEmpty()) {
            return List.of();
        }
        requireParticipant(appointmentClient.getContext(room.get().getAppointmentId()), userId, normalizeRole(role));
        // Historical chat remains available after the consultation closes.
        return getMessages(roomOrAppointmentId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(UUID roomOrAppointmentId) {
        // The web client historically passed appointmentId here, while the API
        // contract called the value roomId. Accept both identifiers so history
        // works for rooms whose generated id differs from the appointment id.
        Optional<RoomEntity> room = roomRepository.findById(roomOrAppointmentId)
                .or(() -> roomRepository.findByAppointmentId(roomOrAppointmentId));
        if (room.isEmpty()) {
            return List.of();
        }

        RoomEntity resolvedRoom = room.get();
        return chatMessageRepository.findByRoomIdOrderByIdAsc(resolvedRoom.getId()).stream()
                .map(entity -> mapMessage(entity, resolvedRoom, null, null))
                .toList();
    }

    @Transactional
    public ChatMessageDto saveMessage(UUID roomId, UUID senderId, UUID clientMessageId, String body) {
        return saveMessage(roomId, senderId, clientMessageId, body, null, null);
    }

    @Transactional
    public ChatMessageDto saveMessage(UUID roomId, UUID senderId, UUID clientMessageId, String body,
                                      String senderRole, String senderName) {
        RoomEntity room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Phòng khám không tồn tại"));

        requireOpenRoom(room);
        AppointmentRoomContextClient.RoomContext context = appointmentClient.getContext(room.getAppointmentId());
        requireParticipant(context, senderId,
                senderRole == null ? inferSenderRole(room, senderId) : normalizeRole(senderRole));
        requireActiveAppointment(context);

        // Deduplication on clientMessageId
        Optional<ChatMessageEntity> existing = chatMessageRepository
                .findByRoomIdAndSenderIdAndClientMessageId(roomId, senderId, clientMessageId);
        if (existing.isPresent()) {
            log.info("Duplicate clientMessageId {} from sender {}, returning existing message", clientMessageId, senderId);
            return mapMessage(existing.get(), room, senderRole, senderName);
        }

        AesGcmCrypto.EncryptedData encrypted = cryptoService.encryptText(body);

        ChatMessageEntity entity = ChatMessageEntity.builder()
                .roomId(roomId)
                .senderId(senderId)
                .clientMessageId(clientMessageId)
                .bodyCiphertext(encrypted.ciphertext())
                .keyId("v1")
                .nonce(encrypted.nonce())
                .build();

        entity = chatMessageRepository.save(entity);
        return mapMessage(entity, room, senderRole, senderName);
    }

    public WebRtcConfigResponse getWebRtcConfig() {
        List<Map<String, Object>> iceServers = new ArrayList<>();

        // Google public STUN server
        iceServers.add(Map.of("urls", List.of("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")));

        // Coturn STUN/TURN server
        Map<String, Object> turnConfig = new HashMap<>();
        turnConfig.put("urls", List.of(turnUrl));
        turnConfig.put("username", turnUsername);
        turnConfig.put("credential", turnCredential);
        iceServers.add(turnConfig);

        return WebRtcConfigResponse.builder().iceServers(iceServers).build();
    }

    private ChatMessageDto mapMessage(ChatMessageEntity entity, RoomEntity room,
                                      String suppliedRole, String suppliedName) {
        String decryptedBody = cryptoService.decryptText(entity.getBodyCiphertext(), entity.getNonce());
        String senderRole = suppliedRole != null && !suppliedRole.isBlank()
                ? normalizeRole(suppliedRole)
                : inferSenderRole(room, entity.getSenderId());
        String senderName = suppliedName != null && !suppliedName.isBlank()
                ? suppliedName.strip()
                : null;

        return ChatMessageDto.builder()
                .id(entity.getId())
                .roomId(entity.getRoomId())
                .senderId(entity.getSenderId())
                .clientMessageId(entity.getClientMessageId())
                .body(decryptedBody)
                .content(decryptedBody)
                .senderRole(senderRole)
                .senderName(senderName)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String inferSenderRole(RoomEntity room, UUID senderId) {
        if (senderId.equals(room.getDoctorUserId())) {
            return "DOCTOR";
        }
        if (senderId.equals(room.getPatientId())) {
            return "PATIENT";
        }
        return "UNKNOWN";
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "PATIENT";
        }
        String normalized = role.strip().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DOCTOR", "PATIENT", "ADMIN" -> normalized;
            default -> "UNKNOWN";
        };
    }

    private void requireParticipant(AppointmentRoomContextClient.RoomContext context, UUID userId, String role) {
        boolean assigned = "PATIENT".equals(role) && context.patientId().equals(userId)
                || "DOCTOR".equals(role) && context.doctorUserId().equals(userId);
        if (!assigned) {
            throw new IllegalStateException("Bạn không được phân công vào ca khám này.");
        }
    }

    private void requireActiveAppointment(AppointmentRoomContextClient.RoomContext context) {
        if (Set.of("COMPLETED", "CANCELLED", "EXPIRED").contains(context.status())) {
            throw new RoomClosedException(context.appointmentId());
        }
        if (!Set.of("CONFIRMED", "IN_PROGRESS").contains(context.status())) {
            throw new IllegalStateException("Ca khám chưa được xác nhận để vào phòng.");
        }
    }

    private void requireOpenRoom(RoomEntity room) {
        if (!"OPEN".equals(room.getState())) {
            throw new RoomClosedException(room.getAppointmentId());
        }
    }

}
