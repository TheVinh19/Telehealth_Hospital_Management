package com.telehealth.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telehealth.appointment.dto.*;
import com.telehealth.appointment.entity.OutboxEventEntity;
import com.telehealth.appointment.repository.OutboxEventRepository;
import com.telehealth.appointment.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class PatientNotificationIntegrationTests {
    @Autowired AppointmentService service;
    @Autowired CatalogService catalog;
    @Autowired OutboxEventRepository outbox;
    @Autowired PatientNotificationDelivery delivery;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper mapper;
    @MockBean PatientNotificationClient client;

    record Fixture(DoctorDto doctor, SlotDto slot, AppointmentResponse appointment) {}

    private SlotDto slot(DoctorDto doctor, int day) {
        return catalog.createSlot(CreateSlotRequest.builder().doctorId(doctor.getId())
                .startsAt(Instant.now().plus(day, ChronoUnit.DAYS)).build());
    }

    private Fixture hold() {
        var doctor = catalog.createDoctor(CreateDoctorProfileRequest.builder().userId(UUID.randomUUID())
                .specialtyId(AppointmentDataInitializer.SPECIALTY_TIM_MACH_ID).displayName("Email test doctor").build());
        var slot = slot(doctor, 20);
        var appointment = service.holdAppointment(AppointmentDataInitializer.PATIENT_1_ID,
                BookAppointmentRequest.builder().doctorId(doctor.getId()).slotId(slot.getId()).intakeId(UUID.randomUUID()).build(), null);
        return new Fixture(doctor, slot, appointment);
    }

    private List<OutboxEventEntity> emails(UUID appointmentId) {
        return outbox.findAll().stream().filter(event -> event.getAggregateId().equals(appointmentId)
                && PatientNotificationDelivery.TYPES.contains(event.getEventType())).toList();
    }

    @Test
    void paymentProducesOneConfirmationAndEachSuccessfulRescheduleGetsItsOwnEvent() throws Exception {
        var fixture = hold();
        UUID id = fixture.appointment().getId();
        assertTrue(emails(id).isEmpty(), "No email for an unpaid hold");
        service.confirmAppointment(id, 100000L, "TEST-MAIL");
        service.confirmAppointment(id, 100000L, "TEST-MAIL");
        assertEquals(1, emails(id).size());
        assertEquals(PatientNotificationDelivery.CONFIRMED, emails(id).get(0).getEventType());
        var firstNewSlot = slot(fixture.doctor(), 21);
        service.reschedule(id, fixture.appointment().getPatientId(), firstNewSlot.getId());
        var secondNewSlot = slot(fixture.doctor(), 22);
        service.reschedule(id, fixture.appointment().getPatientId(), secondNewSlot.getId());
        var events = emails(id);
        assertEquals(3, events.size());
        assertEquals(3, events.stream().map(OutboxEventEntity::getEventId).distinct().count());
        var moves = events.stream().filter(e -> PatientNotificationDelivery.RESCHEDULED.equals(e.getEventType())).toList();
        assertEquals(2, moves.size());
        for (var event : moves) {
            var payload = mapper.readTree(event.getPayload());
            assertEquals(id.toString(), payload.path("appointmentId").asText());
            assertNotEquals(payload.path("oldSlotId").asText(), payload.path("newSlotId").asText());
            assertFalse(payload.has("symptoms"));
            assertFalse(payload.has("diagnosis"));
        }
        assertThrows(IllegalArgumentException.class, () -> service.reschedule(id, fixture.appointment().getPatientId(), secondNewSlot.getId()));
        assertEquals(3, emails(id).size(), "Failed reschedule must not request mail");
    }

    @Test
    void rollbackDoesNotLeaveAConfirmationEmail() {
        var fixture = hold();
        UUID id = fixture.appointment().getId();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.confirmAppointment(id, 100000L, "TEST-ROLLBACK");
            tx.setRollbackOnly();
        });
        assertEquals("PENDING_PAYMENT", service.getRoomContext(id).status());
        assertTrue(emails(id).isEmpty());
        verifyNoInteractions(client);
    }

    @Test
    void unavailableEmailServiceRetriesWithTheSameEventAndDoesNotUndoPayment() throws Exception {
        var fixture = hold();
        UUID id = fixture.appointment().getId();
        service.confirmAppointment(id, 100000L, "TEST-RETRY");
        UUID eventId = emails(id).get(0).getEventId();
        doThrow(new IllegalStateException("Temporary notification outage")).doNothing().when(client).deliver(any());
        delivery.deliver(eventId);
        var failed = outbox.findById(eventId).orElseThrow();
        assertNull(failed.getPublishedAt());
        assertEquals(1, failed.getAttempts());
        assertTrue(failed.getNextAttemptAt().isAfter(Instant.now()));
        assertEquals("CONFIRMED", service.getRoomContext(id).status());
        delivery.deliver(eventId);
        delivery.deliver(eventId);
        assertNotNull(outbox.findById(eventId).orElseThrow().getPublishedAt());
        verify(client, times(2)).deliver(argThat(event -> eventId.equals(event.getEventId())));
    }

    @Test
    void historicalDomainEventsAreNotSentAsNewEmails() throws Exception {
        var fixture = hold();
        service.confirmAppointment(fixture.appointment().getId(), 100000L, "TEST-HISTORY");
        var oldEvent = outbox.findAll().stream().filter(e -> e.getAggregateId().equals(fixture.appointment().getId())
                && "AppointmentConfirmed".equals(e.getEventType())).findFirst().orElseThrow();
        delivery.deliver(oldEvent.getEventId());
        verifyNoInteractions(client);
        assertTrue(outbox.findTop20ByEventTypeInAndPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByOccurredAtAsc(
                PatientNotificationDelivery.TYPES, Instant.now()).stream().noneMatch(e -> e.getEventId().equals(oldEvent.getEventId())));
    }
}
