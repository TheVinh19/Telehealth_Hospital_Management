package com.telehealth.appointment;

import com.telehealth.appointment.dto.*;
import com.telehealth.appointment.entity.OutboxEventEntity;
import com.telehealth.appointment.repository.OutboxEventRepository;
import com.telehealth.appointment.service.*;
import com.telehealth.contracts.enums.AppointmentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RoomCompletionIntegrationTests {
    @Autowired AppointmentService service;
    @Autowired CatalogService catalog;
    @Autowired OutboxEventRepository outbox;
    @Autowired RoomClosureDelivery delivery;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;
    @MockBean RealtimeRoomClient realtimeClient;

    private DoctorDto doctor() {
        return catalog.createDoctor(CreateDoctorProfileRequest.builder().userId(UUID.randomUUID())
                .specialtyId(AppointmentDataInitializer.SPECIALTY_TIM_MACH_ID).displayName("Completion test doctor").build());
    }

    private AppointmentResponse appointment(DoctorDto doctor, int offset) {
        SlotDto slot = catalog.createSlot(CreateSlotRequest.builder().doctorId(doctor.getId())
                .startsAt(Instant.now().plus(20, ChronoUnit.DAYS).plus(offset, ChronoUnit.HOURS)).build());
        AppointmentResponse appointment = service.holdAppointment(AppointmentDataInitializer.PATIENT_1_ID,
                BookAppointmentRequest.builder().doctorId(doctor.getId()).slotId(slot.getId()).intakeId(UUID.randomUUID()).build(), null);
        service.confirmAppointment(appointment.getId(), 100000L, "TEST-COMPLETION");
        return appointment;
    }

    private OutboxEventEntity closeEvent(UUID appointmentId) {
        return outbox.findAll().stream().filter(event -> event.getAggregateId().equals(appointmentId)
                && RoomClosureDelivery.EVENT_TYPE.equals(event.getEventType())).findFirst().orElseThrow();
    }

    @Test
    void completionIsIdempotentAndReleasesTheDoctorsSingleActiveCase() {
        DoctorDto doctor = doctor();
        AppointmentResponse first = appointment(doctor, 1);
        AppointmentResponse second = appointment(doctor, 2);
        service.startConsultation(first.getId(), doctor.getUserId());
        assertThrows(IllegalStateException.class, () -> service.startConsultation(second.getId(), doctor.getUserId()));
        assertThrows(IllegalStateException.class, () -> service.completeConsultation(first.getId(), UUID.randomUUID()));
        AppointmentResponse completed = service.completeConsultation(first.getId(), doctor.getUserId());
        assertEquals(AppointmentStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
        UUID eventId = closeEvent(first.getId()).getEventId();
        service.completeConsultation(first.getId(), doctor.getUserId());
        assertEquals(1, outbox.findAll().stream().filter(event -> event.getAggregateId().equals(first.getId())
                && RoomClosureDelivery.EVENT_TYPE.equals(event.getEventType())).count());
        assertEquals(eventId, closeEvent(first.getId()).getEventId());
        assertDoesNotThrow(() -> service.startConsultation(second.getId(), doctor.getUserId()));
    }

    @Test
    void failedRoomCallbackRemainsDurableAndRetryIsIdempotent() {
        DoctorDto doctor = doctor();
        AppointmentResponse appointment = appointment(doctor, 1);
        service.startConsultation(appointment.getId(), doctor.getUserId());
        service.completeConsultation(appointment.getId(), doctor.getUserId());
        UUID eventId = closeEvent(appointment.getId()).getEventId();
        doThrow(new IllegalStateException("Temporary realtime outage")).doNothing()
                .when(realtimeClient).closeCompletedRoom(appointment.getId());
        delivery.deliver(eventId);
        OutboxEventEntity failed = outbox.findById(eventId).orElseThrow();
        assertNull(failed.getPublishedAt());
        assertEquals(1, failed.getAttempts());
        assertTrue(failed.getNextAttemptAt().isAfter(Instant.now()));
        assertEquals("COMPLETED", service.getRoomContext(appointment.getId()).status());
        delivery.deliver(eventId);
        assertNotNull(outbox.findById(eventId).orElseThrow().getPublishedAt());
        delivery.deliver(eventId);
        verify(realtimeClient, times(2)).closeCompletedRoom(appointment.getId());
    }

    @Test
    void rolledBackCompletionDoesNotPersistRoomClosure() {
        DoctorDto doctor = doctor();
        AppointmentResponse appointment = appointment(doctor, 1);
        service.startConsultation(appointment.getId(), doctor.getUserId());
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            service.completeConsultation(appointment.getId(), doctor.getUserId());
            transaction.setRollbackOnly();
        });
        assertEquals("IN_PROGRESS", service.getRoomContext(appointment.getId()).status());
        assertFalse(outbox.existsByEventTypeAndAggregateId(RoomClosureDelivery.EVENT_TYPE, appointment.getId()));
        verifyNoInteractions(realtimeClient);
    }

    @Test
    void roomContextRequiresServiceAuthentication() throws Exception {
        DoctorDto doctor = doctor();
        AppointmentResponse appointment = appointment(doctor, 1);
        mvc.perform(get("/api/v1/internal/appointments/{id}/room-context", appointment.getId()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/internal/appointments/{id}/room-context", appointment.getId())
                        .header("X-Service-Token", "telehealth-local-internal-token-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientId").value(appointment.getPatientId().toString()))
                .andExpect(jsonPath("$.data.doctorUserId").value(doctor.getUserId().toString()));
    }
}
