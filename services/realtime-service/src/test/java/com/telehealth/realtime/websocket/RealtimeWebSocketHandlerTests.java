package com.telehealth.realtime.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telehealth.realtime.dto.RoomAdmissionResponse;
import com.telehealth.realtime.service.RealtimeService;
import com.telehealth.realtime.service.AppointmentRoomContextClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class RealtimeWebSocketHandlerTests {

    @Autowired
    private RealtimeWebSocketHandler handler;

    @Autowired
    private RealtimeService realtimeService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AppointmentRoomContextClient appointmentClient;

    @Test
    void senderCanSendAndReceivePersistedChatWithoutASecondParticipant() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        prepareAppointment(appointmentId, patientId, UUID.randomUUID());
        RoomAdmissionResponse room = realtimeService.getOrCreateRoom(appointmentId, patientId, "PATIENT");

        List<TextMessage> outbound = new ArrayList<>();
        WebSocketSession session = session("patient-session", room.getToken(), outbound);

        try {
            handler.afterConnectionEstablished(session);
            // Also verifies the query-token fallback used by older browser builds.
            handler.handleMessage(session, new TextMessage("{\"type\":\"JOIN\"}"));
            assertFalse(outbound.isEmpty());
            assertEquals("JOINED", objectMapper.readTree(outbound.get(0).getPayload()).get("type").asText());

            outbound.clear();
            UUID clientMessageId = UUID.randomUUID();
            String body = "Tôi cần bác sĩ tư vấn về kết quả xét nghiệm.";
            handler.handleMessage(session, new TextMessage(objectMapper.writeValueAsString(new ChatInput(
                    "CHAT", clientMessageId, body
            ))));

            assertEquals(1, outbound.size(), "The sender must receive its own saved message immediately");
            JsonNode event = objectMapper.readTree(outbound.get(0).getPayload());
            assertEquals("CHAT", event.get("type").asText());
            assertEquals(body, event.at("/message/body").asText());
            assertEquals(body, event.at("/message/content").asText());
            assertEquals(patientId.toString(), event.at("/message/senderId").asText());
            assertEquals("PATIENT", event.at("/message/senderRole").asText());

            // Backwards compatibility for browser bundles that consume CHAT
            // as a flat event instead of reading the nested `message` object.
            assertEquals(body, event.get("body").asText());
            assertEquals(body, event.get("content").asText());
            assertEquals(clientMessageId.toString(), event.get("clientMessageId").asText());
            assertEquals(patientId.toString(), event.get("senderId").asText());
            assertEquals("PATIENT", event.get("senderRole").asText());
            assertEquals(event.at("/message/id"), event.get("id"));
            assertEquals(event.at("/message/createdAt"), event.get("createdAt"));

            assertEquals(1, realtimeService.getMessages(room.getRoomId()).size());
            assertEquals(body, realtimeService.getMessages(room.getRoomId()).get(0).getBody());
        } finally {
            handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        }
    }

    @Test
    void twoParticipantsReceiveRapidMessagesWithoutConcurrentSocketWrites() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        prepareAppointment(appointmentId, patientId, doctorId);
        RoomAdmissionResponse patientRoom = realtimeService.getOrCreateRoom(appointmentId, patientId, "PATIENT");
        RoomAdmissionResponse doctorRoom = realtimeService.getOrCreateRoom(appointmentId, doctorId, "DOCTOR");
        assertEquals(patientRoom.getRoomId(), doctorRoom.getRoomId());

        List<TextMessage> patientOutbound = Collections.synchronizedList(new ArrayList<>());
        List<TextMessage> doctorOutbound = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean patientConcurrentWrite = new AtomicBoolean();
        AtomicBoolean doctorConcurrentWrite = new AtomicBoolean();
        WebSocketSession patientSession = delayedSession(
                "rapid-patient", patientRoom.getToken(), patientOutbound, patientConcurrentWrite);
        WebSocketSession doctorSession = delayedSession(
                "rapid-doctor", doctorRoom.getToken(), doctorOutbound, doctorConcurrentWrite);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            handler.afterConnectionEstablished(patientSession);
            handler.afterConnectionEstablished(doctorSession);

            CountDownLatch joinStart = new CountDownLatch(1);
            Future<?> patientJoin = executor.submit(() -> join(patientSession, joinStart));
            Future<?> doctorJoin = executor.submit(() -> join(doctorSession, joinStart));
            joinStart.countDown();
            patientJoin.get(10, TimeUnit.SECONDS);
            doctorJoin.get(10, TimeUnit.SECONDS);

            assertTrue(hasEvent(patientOutbound, "JOINED"));
            assertTrue(hasEvent(doctorOutbound, "JOINED"));
            assertTrue(hasEvent(patientOutbound, "USER_JOINED"));
            assertTrue(hasEvent(doctorOutbound, "USER_JOINED"));
            assertEquals(1, countEvents(patientOutbound, "PEER_JOINED")
                    + countEvents(doctorOutbound, "PEER_JOINED"));
            assertFalse(patientConcurrentWrite.get(), "Concurrent JOIN events must serialize patient writes");
            assertFalse(doctorConcurrentWrite.get(), "Concurrent JOIN events must serialize doctor writes");
            patientOutbound.clear();
            doctorOutbound.clear();

            int messagesPerParticipant = 12;
            CountDownLatch start = new CountDownLatch(1);
            Future<?> patientSend = executor.submit(() -> sendBurst(
                    patientSession, "patient", messagesPerParticipant, start));
            Future<?> doctorSend = executor.submit(() -> sendBurst(
                    doctorSession, "doctor", messagesPerParticipant, start));
            start.countDown();
            patientSend.get(15, TimeUnit.SECONDS);
            doctorSend.get(15, TimeUnit.SECONDS);

            int expectedMessages = messagesPerParticipant * 2;
            assertEquals(expectedMessages, patientOutbound.size());
            assertEquals(expectedMessages, doctorOutbound.size());
            assertFalse(patientConcurrentWrite.get(), "Patient socket writes must be serialized");
            assertFalse(doctorConcurrentWrite.get(), "Doctor socket writes must be serialized");

            Set<String> patientMessageIds = assertCompatibleChatEvents(patientOutbound);
            Set<String> doctorMessageIds = assertCompatibleChatEvents(doctorOutbound);
            assertEquals(expectedMessages, patientMessageIds.size());
            assertEquals(patientMessageIds, doctorMessageIds);
            assertEquals(expectedMessages, realtimeService.getMessages(patientRoom.getRoomId()).size());
        } finally {
            executor.shutdownNow();
            handler.afterConnectionClosed(patientSession, CloseStatus.NORMAL);
            handler.afterConnectionClosed(doctorSession, CloseStatus.NORMAL);
        }
    }

    private void join(WebSocketSession session, CountDownLatch start) {
        try {
            assertTrue(start.await(5, TimeUnit.SECONDS));
            handler.handleMessage(session, new TextMessage("{\"type\":\"JOIN\"}"));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Test
    void roomClosureNotifiesBothParticipantsAndRejectsOldTokenWithoutNewChat() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        prepareAppointment(appointmentId, patientId, doctorId);
        RoomAdmissionResponse patientRoom = realtimeService.getOrCreateRoom(appointmentId, patientId, "PATIENT");
        RoomAdmissionResponse doctorRoom = realtimeService.getOrCreateRoom(appointmentId, doctorId, "DOCTOR");
        List<TextMessage> patientEvents = new ArrayList<>();
        List<TextMessage> doctorEvents = new ArrayList<>();
        WebSocketSession patient = session("closing-patient", patientRoom.getToken(), patientEvents);
        WebSocketSession doctor = session("closing-doctor", doctorRoom.getToken(), doctorEvents);
        handler.handleMessage(patient, new TextMessage("{\"type\":\"JOIN\"}"));
        handler.handleMessage(doctor, new TextMessage("{\"type\":\"JOIN\"}"));
        patientEvents.clear();
        doctorEvents.clear();

        realtimeService.closeByAppointment(appointmentId);

        assertEquals(1, countEvents(patientEvents, "ROOM_CLOSED"));
        assertEquals(1, countEvents(doctorEvents, "ROOM_CLOSED"));
        JsonNode event = objectMapper.readTree(patientEvents.get(0).getPayload());
        assertEquals(appointmentId.toString(), event.get("appointmentId").asText());
        assertEquals("COMPLETED", event.get("reason").asText());
        assertEquals("Ca khám đã hoàn tất", event.get("message").asText());

        handler.handleMessage(patient, new TextMessage("{\"type\":\"CHAT\",\"body\":\"blocked\"}"));
        assertTrue(realtimeService.getMessages(patientRoom.getRoomId()).isEmpty());

        List<TextMessage> retryEvents = new ArrayList<>();
        WebSocketSession retry = session("old-token", patientRoom.getToken(), retryEvents);
        handler.handleMessage(retry, new TextMessage("{\"type\":\"JOIN\"}"));
        assertEquals(1, countEvents(retryEvents, "ROOM_CLOSED"));
        assertEquals(0, countEvents(retryEvents, "JOINED"));
    }

    private void prepareAppointment(UUID appointmentId, UUID patientId, UUID doctorUserId) {
        when(appointmentClient.getContext(appointmentId)).thenReturn(new AppointmentRoomContextClient.RoomContext(
                appointmentId, patientId, UUID.randomUUID(), doctorUserId, Instant.now(), "IN_PROGRESS"));
    }

    private boolean hasEvent(List<TextMessage> outbound, String type) throws Exception {
        return countEvents(outbound, type) > 0;
    }

    private long countEvents(List<TextMessage> outbound, String type) throws Exception {
        long matches = 0;
        for (TextMessage message : List.copyOf(outbound)) {
            if (type.equals(objectMapper.readTree(message.getPayload()).path("type").asText())) {
                matches++;
            }
        }
        return matches;
    }

    private void sendBurst(WebSocketSession session, String prefix, int count, CountDownLatch start) {
        try {
            assertTrue(start.await(5, TimeUnit.SECONDS));
            for (int index = 0; index < count; index++) {
                handler.handleMessage(session, new TextMessage(objectMapper.writeValueAsString(new ChatInput(
                        "CHAT", UUID.randomUUID(), prefix + "-message-" + index
                ))));
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private Set<String> assertCompatibleChatEvents(List<TextMessage> outbound) throws Exception {
        Set<String> messageIds = new HashSet<>();
        for (TextMessage outboundMessage : List.copyOf(outbound)) {
            JsonNode event = objectMapper.readTree(outboundMessage.getPayload());
            assertEquals("CHAT", event.get("type").asText());
            assertEquals(event.at("/message/clientMessageId"), event.get("clientMessageId"));
            assertEquals(event.at("/message/body"), event.get("body"));
            assertEquals(event.at("/message/content"), event.get("content"));
            messageIds.add(event.get("clientMessageId").asText());
        }
        return messageIds;
    }

    private WebSocketSession session(String id, String token, List<TextMessage> outbound) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws?token=" + token));
        when(session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            WebSocketMessage<?> message = invocation.getArgument(0);
            outbound.add((TextMessage) message);
            return null;
        }).when(session).sendMessage(any(WebSocketMessage.class));
        return session;
    }

    private WebSocketSession delayedSession(String id, String token, List<TextMessage> outbound,
                                             AtomicBoolean concurrentWrite) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        AtomicInteger writesInProgress = new AtomicInteger();
        when(session.getId()).thenReturn(id);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws?token=" + token));
        when(session.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            if (writesInProgress.incrementAndGet() > 1) {
                concurrentWrite.set(true);
            }
            try {
                Thread.sleep(3);
                WebSocketMessage<?> message = invocation.getArgument(0);
                outbound.add((TextMessage) message);
            } finally {
                writesInProgress.decrementAndGet();
            }
            return null;
        }).when(session).sendMessage(any(WebSocketMessage.class));
        return session;
    }

    private record ChatInput(String type, UUID clientMessageId, String body) {
    }
}
