package com.telehealth.realtime.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.telehealth.realtime.dto.ChatMessageDto;
import com.telehealth.realtime.service.RealtimeService;
import com.telehealth.realtime.service.RealtimeTokenService;
import com.telehealth.realtime.service.RoomClosedEvent;
import com.telehealth.realtime.service.RoomClosedException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final RealtimeService realtimeService;
    private final RealtimeTokenService tokenService;
    private final ObjectMapper objectMapper;

    // Map: roomId -> Set of WebSocketSessions
    private final Map<UUID, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    // Map: sessionId -> session context (roomId, userId, role)
    private final Map<String, SessionContext> sessionContexts = new ConcurrentHashMap<>();

    private record SessionContext(UUID roomId, UUID userId, String role, int admissionVersion) {
    }

    private record ParticipantSession(WebSocketSession session, SessionContext context) {
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established: session={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = textValue(root, "type");

        try {
            switch (type) {
                case "JOIN" -> handleJoin(session, root);
                case "CHAT" -> handleChat(session, root);
                case "SIGNAL" -> handleSignal(session, root);
                // The consultation UI uses flat signaling messages. Keep SIGNAL
                // support for older clients, and forward the current protocol too.
                case "OFFER", "ANSWER", "ICE_CANDIDATE" -> handleDirectSignal(session, root);
                case "LEAVE" -> handleLeave(session);
                default -> log.warn("Unknown message type: {}", type);
            }
        } catch (RoomClosedException exception) {
            sendRoomClosed(session, exception.getAppointmentId());
            cleanUpSession(session);
            session.close(CloseStatus.NORMAL);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sendJson(session, Map.of("type", "ERROR", "message", exception.getMessage()));
        }
    }

    private void handleJoin(WebSocketSession session, JsonNode root) throws IOException {
        String token = textValue(root, "token");
        if (token.isBlank()) {
            // Browser WebSocket clients cannot set an Authorization header, so
            // the web app supplies the short-lived room token in the query.
            token = queryParameter(session, "token").orElse("");
        }

        Claims claims;
        UUID roomId;
        UUID userId;
        String role;
        int admissionVersion;
        try {
            claims = tokenService.parseAndValidateToken(token);
            roomId = UUID.fromString(claims.get("roomId", String.class));
            userId = UUID.fromString(claims.getSubject());
            role = claims.get("role", String.class);
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("Room token is missing a role");
            }
            Number version = claims.get("admissionVersion", Number.class);
            if (version == null) {
                throw new IllegalArgumentException("Room token is missing admission version");
            }
            admissionVersion = version.intValue();
        } catch (Exception exception) {
            log.error("Failed to authenticate room token on join", exception);
            sendJson(session, Map.of(
                    "type", "ERROR",
                    "message", "Token phòng không hợp lệ hoặc đã hết hạn"
            ));
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        realtimeService.assertActiveSession(roomId, userId, role, admissionVersion);
        SessionContext context = new SessionContext(roomId, userId, role, admissionVersion);
        sessionContexts.put(session.getId(), context);

        // Mutate membership atomically per room. Two browsers commonly JOIN at
        // almost the same time; compute ensures at least one observes the other
        // and both receive the presence event needed to start WebRTC.
        List<ParticipantSession> existingParticipants = new ArrayList<>();
        roomSessions.compute(roomId, (ignored, currentSessions) -> {
            Set<WebSocketSession> sessions = currentSessions != null
                    ? currentSessions
                    : ConcurrentHashMap.newKeySet();
            sessions.removeIf(peer -> !peer.isOpen());
            sessions.stream()
                    .map(peer -> new ParticipantSession(peer, sessionContexts.get(peer.getId())))
                    .filter(peer -> Objects.nonNull(peer.context()))
                    .forEach(existingParticipants::add);
            sessions.add(session);
            return sessions;
        });

        // Membership must be visible before this second check: a close racing
        // with JOIN will either notify this socket or be detected here.
        realtimeService.assertActiveSession(roomId, userId, role, admissionVersion);

        log.info("User {} ({}) joined room {}", userId, role, roomId);

        sendJson(session, Map.of(
                "type", "JOINED",
                "roomId", roomId,
                "userId", userId,
                "role", role
        ));

        // Tell the new participant about peers who were already present.
        // Without this, the second browser never starts WebRTC negotiation.
        for (ParticipantSession peer : existingParticipants) {
            sendJson(session, Map.of(
                    "type", "USER_JOINED",
                    "userId", peer.context().userId(),
                    "role", peer.context().role()
            ));
        }

        // Notify exactly the peers captured before this session was added.
        // Broadcasting to the current room would let two near-simultaneous
        // joins both receive PEER_JOINED and both create WebRTC offers (glare).
        for (ParticipantSession peer : existingParticipants) {
            sendJson(peer.session(), Map.of(
                    "type", "USER_JOINED",
                    "userId", userId,
                    "role", role
            ));
            // PEER_JOINED is retained for older browser bundles.
            sendJson(peer.session(), Map.of(
                    "type", "PEER_JOINED",
                    "userId", userId,
                    "role", role
            ));
        }
    }

    private void handleChat(WebSocketSession session, JsonNode root) {
        SessionContext context = sessionContexts.get(session.getId());
        if (context == null) {
            sendJson(session, Map.of("type", "ERROR", "message", "Chưa tham gia phòng"));
            return;
        }

        UUID clientMessageId = parseUuidOrRandom(textValue(root, "clientMessageId"));
        String body = textValue(root, "body");
        if (body.isBlank()) {
            // The React consultation room names this field `content`.
            body = textValue(root, "content");
        }
        if (body.isBlank()) {
            return;
        }

        ChatMessageDto saved = realtimeService.saveMessage(
                context.roomId(),
                context.userId(),
                clientMessageId,
                body,
                context.role(),
                textValue(root, "senderName")
        );

        // Echo to the sender as well, so chat remains usable while waiting for
        // the other participant. The persisted/decrypted message is the single
        // canonical payload returned to every participant.
        broadcastToRoom(context.roomId(), chatEnvelope(saved));
    }

    private void handleSignal(WebSocketSession session, JsonNode root) {
        SessionContext context = sessionContexts.get(session.getId());
        if (context == null) {
            return;
        }
        realtimeService.assertActiveSession(context.roomId(), context.userId(), context.role(), context.admissionVersion());

        JsonNode payload = root.get("payload");
        String signalType = textValue(root, "signalType");
        if (signalType.isBlank()) {
            signalType = "OFFER";
        }

        broadcastToRoomExcept(context.roomId(), session, Map.of(
                "type", "SIGNAL",
                "signalType", signalType,
                "senderId", context.userId(),
                "role", context.role(),
                "payload", payload
        ));
    }

    private void handleDirectSignal(WebSocketSession session, JsonNode root) {
        SessionContext context = sessionContexts.get(session.getId());
        if (context == null) {
            return;
        }
        realtimeService.assertActiveSession(context.roomId(), context.userId(), context.role(), context.admissionVersion());

        ObjectNode forwarded = root.deepCopy();
        forwarded.put("senderId", context.userId().toString());
        forwarded.put("senderRole", context.role());
        broadcastToRoomExcept(context.roomId(), session, forwarded);
    }

    private void handleLeave(WebSocketSession session) {
        cleanUpSession(session);
    }

    @TransactionalEventListener
    public void onRoomClosed(RoomClosedEvent event) {
        Set<WebSocketSession> sessions = roomSessions.remove(event.roomId());
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            sessionContexts.remove(session.getId());
            sendRoomClosed(session, event.appointmentId());
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException exception) {
                log.warn("Failed to close completed room socket: {}", exception.getMessage());
            }
        }
    }

    private void sendRoomClosed(WebSocketSession session, UUID appointmentId) {
        sendJson(session, Map.of(
                "type", "ROOM_CLOSED",
                "appointmentId", appointmentId,
                "reason", "COMPLETED",
                "message", "Ca khám đã hoàn tất"
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cleanUpSession(session);
    }

    private void cleanUpSession(WebSocketSession session) {
        SessionContext context = sessionContexts.remove(session.getId());
        if (context == null) {
            return;
        }

        AtomicBoolean peersRemain = new AtomicBoolean();
        roomSessions.computeIfPresent(context.roomId(), (ignored, sessions) -> {
            sessions.remove(session);
            peersRemain.set(!sessions.isEmpty());
            return sessions.isEmpty() ? null : sessions;
        });
        if (peersRemain.get()) {
            broadcastToRoom(context.roomId(), Map.of(
                    "type", "USER_LEFT",
                    "userId", context.userId(),
                    "role", context.role()
            ));
            broadcastToRoom(context.roomId(), Map.of(
                    "type", "PEER_LEFT",
                    "userId", context.userId(),
                    "role", context.role()
            ));
        }
        log.info("User {} left room {}", context.userId(), context.roomId());
    }

    private String textValue(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private UUID parseUuidOrRandom(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return UUID.randomUUID();
        }
    }

    private Optional<String> queryParameter(WebSocketSession session, String name) {
        if (session.getUri() == null || session.getUri().getRawQuery() == null) {
            return Optional.empty();
        }
        return Arrays.stream(session.getUri().getRawQuery().split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .filter(parts -> URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals(name))
                .map(parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8))
                .findFirst();
    }

    private void broadcastToRoom(UUID roomId, Object message) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            sendJson(session, message);
        }
    }

    private void broadcastToRoomExcept(UUID roomId, WebSocketSession exceptSession, Object message) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (!session.getId().equals(exceptSession.getId())) {
                sendJson(session, message);
            }
        }
    }

    /**
     * Keep the canonical message under {@code message}, while also exposing its
     * fields at the top level for older browser bundles.  Those clients read
     * {@code data.clientMessageId}, {@code data.body}, etc.; a nested-only
     * event therefore produced one empty "()" bubble and then treated every
     * later event as a duplicate because all top-level ids were undefined.
     */
    private ObjectNode chatEnvelope(ChatMessageDto message) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("type", "CHAT");

        ObjectNode messageNode = objectMapper.valueToTree(message);
        envelope.set("message", messageNode);
        messageNode.fields().forEachRemaining(field -> envelope.set(field.getKey(), field.getValue()));
        return envelope;
    }

    private void sendJson(WebSocketSession session, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            // The same peer can receive chat, signaling and presence events
            // from different servlet threads. StandardWebSocketSession does
            // not allow concurrent sendMessage calls, so serialize writes per
            // session to avoid dropping the live event after it was persisted.
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException | IllegalStateException exception) {
            log.warn("Failed to send WebSocket message: {}", exception.getMessage());
        }
    }
}
