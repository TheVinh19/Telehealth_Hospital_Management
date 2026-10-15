package com.telehealth.realtime;

import com.telehealth.realtime.dto.ChatMessageDto;
import com.telehealth.realtime.dto.RoomAdmissionResponse;
import com.telehealth.realtime.dto.WebRtcConfigResponse;
import com.telehealth.realtime.entity.ChatMessageEntity;
import com.telehealth.realtime.repository.ChatMessageRepository;
import com.telehealth.realtime.service.RealtimeService;
import com.telehealth.realtime.service.RealtimeTokenService;
import com.telehealth.realtime.service.AppointmentRoomContextClient;
import com.telehealth.realtime.service.RoomClosedException;
import com.telehealth.realtime.repository.RoomRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class RealtimeServiceApplicationTests {

    @Autowired
    private RealtimeService realtimeService;

    @Autowired
    private RealtimeTokenService tokenService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private RoomRepository roomRepository;

    @MockBean
    private AppointmentRoomContextClient appointmentClient;

    @Test
    void testRoomAdmissionTokenGenerationAndValidation() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        prepareAppointment(appointmentId, patientId, UUID.randomUUID(), "CONFIRMED");

        RoomAdmissionResponse response = realtimeService.getOrCreateRoom(appointmentId, patientId, "PATIENT");
        assertNotNull(response.getRoomId());
        assertNotNull(response.getToken());
        assertEquals("PATIENT", response.getRole());

        // Validate token
        Claims claims = tokenService.parseAndValidateToken(response.getToken());
        assertEquals(patientId.toString(), claims.getSubject());
        assertEquals(response.getRoomId().toString(), claims.get("roomId", String.class));
        assertEquals("PATIENT", claims.get("role", String.class));
    }

    @Test
    void testChatMessageEncryptionAndDeduplication() {
        UUID appointmentId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        prepareAppointment(appointmentId, UUID.randomUUID(), doctorId, "IN_PROGRESS");
        RoomAdmissionResponse room = realtimeService.getOrCreateRoom(appointmentId, doctorId, "DOCTOR");

        UUID clientMsgId = UUID.randomUUID();
        String originalBody = "Chào bệnh nhân, tôi là bác sĩ phụ trách ca khám của bạn hôm nay.";

        // Save message
        ChatMessageDto msg1 = realtimeService.saveMessage(room.getRoomId(), doctorId, clientMsgId, originalBody);
        assertNotNull(msg1.getId());
        assertEquals(originalBody, msg1.getBody());

        // Verify DB encryption: Database must NOT contain plain text message!
        ChatMessageEntity entity = chatMessageRepository.findById(msg1.getId()).orElseThrow();
        String rawCiphertext = new String(entity.getBodyCiphertext(), StandardCharsets.UTF_8);
        assertFalse(rawCiphertext.contains("bác sĩ phụ trách"), "Chat message MUST be encrypted in database!");

        // Test Deduplication: calling saveMessage again with the same clientMessageId
        ChatMessageDto msg2 = realtimeService.saveMessage(room.getRoomId(), doctorId, clientMsgId, originalBody);
        assertEquals(msg1.getId(), msg2.getId());

        // Check history
        List<ChatMessageDto> history = realtimeService.getMessages(room.getRoomId());
        assertEquals(1, history.size());
        assertEquals(originalBody, history.get(0).getBody());
        assertEquals(originalBody, history.get(0).getContent());
        assertEquals("DOCTOR", history.get(0).getSenderRole());
        assertNull(history.get(0).getSenderName());

        // The history endpoint accepts the appointment id as well for clients
        // created before roomId was returned from the admission endpoint.
        List<ChatMessageDto> historyByAppointment = realtimeService.getMessages(appointmentId);
        assertEquals(1, historyByAppointment.size());
        assertEquals(originalBody, historyByAppointment.get(0).getContent());
    }

    @Test
    void testWebRtcConfig() {
        WebRtcConfigResponse config = realtimeService.getWebRtcConfig();
        assertNotNull(config.getIceServers());
        assertFalse(config.getIceServers().isEmpty());
    }

    @Test
    void closingRoomIsIdempotentRevokesTokensAndPreservesHistory() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorUserId = UUID.randomUUID();
        prepareAppointment(appointmentId, patientId, doctorUserId, "IN_PROGRESS");
        RoomAdmissionResponse admission = realtimeService.getOrCreateRoom(appointmentId, patientId, "PATIENT");
        realtimeService.saveMessage(admission.getRoomId(), patientId, UUID.randomUUID(), "history remains available");

        realtimeService.closeByAppointment(appointmentId);
        realtimeService.closeByAppointment(appointmentId);

        var room = roomRepository.findById(admission.getRoomId()).orElseThrow();
        assertEquals("CLOSED", room.getState());
        assertNotNull(room.getClosedAt());
        assertEquals(admission.getAdmissionVersion() + 1, room.getAdmissionVersion());
        assertThrows(RoomClosedException.class, () -> realtimeService.getOrCreateRoom(appointmentId, patientId, "PATIENT"));
        assertThrows(RoomClosedException.class, () -> realtimeService.assertActiveSession(
                admission.getRoomId(), patientId, "PATIENT", admission.getAdmissionVersion()));
        assertThrows(RoomClosedException.class, () -> realtimeService.saveMessage(
                admission.getRoomId(), patientId, UUID.randomUUID(), "must not persist"));
        assertEquals(1, realtimeService.getMessages(admission.getRoomId(), patientId, "PATIENT").size());
    }

    @Test
    void completedAppointmentCannotReenterOrSendEvenWhenCloseNotificationWasMissed() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        prepareAppointment(appointmentId, patientId, doctorId, "IN_PROGRESS");
        RoomAdmissionResponse room = realtimeService.getOrCreateRoom(appointmentId, patientId, "PATIENT");
        prepareAppointment(appointmentId, patientId, doctorId, "COMPLETED");

        assertThrows(RoomClosedException.class, () -> realtimeService.getOrCreateRoom(appointmentId, patientId, "PATIENT"));
        assertThrows(RoomClosedException.class, () -> realtimeService.assertActiveSession(
                room.getRoomId(), patientId, "PATIENT", room.getAdmissionVersion()));
        assertThrows(RoomClosedException.class, () -> realtimeService.saveMessage(
                room.getRoomId(), patientId, UUID.randomUUID(), "must not persist"));
        assertTrue(realtimeService.getMessages(room.getRoomId()).isEmpty());
    }

    @Test
    void completedAppointmentCannotCreateItsFirstRoomAndCloseCreatesATombstone() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        prepareAppointment(appointmentId, patientId, UUID.randomUUID(), "COMPLETED");
        assertThrows(RoomClosedException.class,
                () -> realtimeService.getOrCreateRoom(appointmentId, patientId, "PATIENT"));
        assertTrue(roomRepository.findByAppointmentId(appointmentId).isEmpty());

        realtimeService.closeByAppointment(appointmentId);
        var room = roomRepository.findByAppointmentId(appointmentId).orElseThrow();
        assertEquals("CLOSED", room.getState());
        assertEquals(2, room.getAdmissionVersion());
        assertNotNull(room.getClosedAt());
        assertThrows(RoomClosedException.class,
                () -> realtimeService.getOrCreateRoom(appointmentId, patientId, "PATIENT"));
    }

    @Test
    void unrelatedAccountCannotAcquireTokenOrReadHistory() {
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        prepareAppointment(appointmentId, patientId, UUID.randomUUID(), "IN_PROGRESS");
        RoomAdmissionResponse room = realtimeService.getOrCreateRoom(appointmentId, patientId, "PATIENT");
        UUID outsider = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> realtimeService.getOrCreateRoom(appointmentId, outsider, "PATIENT"));
        assertThrows(IllegalStateException.class, () -> realtimeService.getMessages(room.getRoomId(), outsider, "PATIENT"));
        assertThrows(IllegalStateException.class, () -> realtimeService.getOrCreateRoom(appointmentId, patientId, "ADMIN"));
    }

    private void prepareAppointment(UUID appointmentId, UUID patientId, UUID doctorUserId, String status) {
        when(appointmentClient.getContext(appointmentId)).thenReturn(new AppointmentRoomContextClient.RoomContext(
                appointmentId, patientId, UUID.randomUUID(), doctorUserId, Instant.now(), status));
    }
}
