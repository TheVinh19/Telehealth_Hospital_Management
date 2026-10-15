package com.telehealth.appointment;

import com.telehealth.appointment.dto.AppointmentResponse;
import com.telehealth.appointment.dto.BookAppointmentRequest;
import com.telehealth.appointment.dto.SlotDto;
import com.telehealth.appointment.entity.SlotClaimEntity;
import com.telehealth.appointment.entity.SlotEntity;
import com.telehealth.appointment.repository.AppointmentRepository;
import com.telehealth.appointment.repository.SlotClaimRepository;
import com.telehealth.appointment.repository.SlotRepository;
import com.telehealth.appointment.service.AppointmentDataInitializer;
import com.telehealth.appointment.service.AppointmentService;
import com.telehealth.appointment.service.CatalogService;
import com.telehealth.contracts.enums.AppointmentStatus;
import com.telehealth.contracts.enums.ClaimKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AppointmentServiceApplicationTests {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private SlotClaimRepository slotClaimRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private UUID doctorId;
    private UUID patient1Id;
    private UUID patient2Id;

    @BeforeEach
    void setUp() {
        doctorId = AppointmentDataInitializer.DOCTOR_ID;
        patient1Id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        patient2Id = UUID.fromString("44444444-4444-4444-4444-444444444444");
    }

    @Test
    void testConcurrentHoldSameSlotFailsForSecondPatient() {
        // Create a unique slot for this test
        Instant slotTime = LocalDate.now(VN_ZONE).plusDays(5).atTime(LocalTime.of(16, 0)).atZone(VN_ZONE).toInstant();
        SlotEntity slot = slotRepository.save(SlotEntity.builder()
                .id(UUID.randomUUID())
                .doctorId(doctorId)
                .startsAt(slotTime)
                .enabled(true)
                .build());

        UUID intake1 = UUID.randomUUID();
        UUID intake2 = UUID.randomUUID();

        // Patient 1 books the slot
        AppointmentResponse app1 = appointmentService.holdAppointment(patient1Id,
                BookAppointmentRequest.builder()
                        .doctorId(doctorId)
                        .slotId(slot.getId())
                        .intakeId(intake1)
                        .build(),
                "idem-key-1");

        assertNotNull(app1.getId());
        assertEquals(AppointmentStatus.PENDING_PAYMENT, app1.getStatus());
        assertTrue(app1.getRemainingSeconds() <= 420 && app1.getRemainingSeconds() > 400);

        // Patient 2 attempts to hold the EXACT SAME slot -> MUST FAIL
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                appointmentService.holdAppointment(patient2Id,
                        BookAppointmentRequest.builder()
                                .doctorId(doctorId)
                                .slotId(slot.getId())
                                .intakeId(intake2)
                                .build(),
                        "idem-key-2")
        );
        assertTrue(ex.getMessage().contains("đang được một bệnh nhân khác giữ"));
    }

    @Test
    void testReloadDoesNotReset7MinuteTimer() throws Exception {
        Instant slotTime = LocalDate.now(VN_ZONE).plusDays(5).atTime(LocalTime.of(16, 15)).atZone(VN_ZONE).toInstant();
        SlotEntity slot = slotRepository.save(SlotEntity.builder()
                .id(UUID.randomUUID())
                .doctorId(doctorId)
                .startsAt(slotTime)
                .enabled(true)
                .build());

        UUID intake = UUID.randomUUID();
        AppointmentResponse initial = appointmentService.holdAppointment(patient1Id,
                BookAppointmentRequest.builder()
                        .doctorId(doctorId)
                        .slotId(slot.getId())
                        .intakeId(intake)
                        .build(),
                "idem-key-timer");

        Instant firstExpiresAt = initial.getExpiresAt().truncatedTo(ChronoUnit.SECONDS);
        long firstRemaining = initial.getRemainingSeconds();

        Thread.sleep(100);

        // Simulating reloading the page: fetching appointment by ID
        AppointmentResponse reloaded = appointmentService.getAppointmentById(initial.getId(), patient1Id, "PATIENT");
        assertEquals(firstExpiresAt, reloaded.getExpiresAt().truncatedTo(ChronoUnit.SECONDS));
        assertTrue(reloaded.getRemainingSeconds() <= firstRemaining);
    }

    @Test
    void testIdempotentBookingWithSameKey() {
        Instant slotTime = LocalDate.now(VN_ZONE).plusDays(5).atTime(LocalTime.of(16, 30)).atZone(VN_ZONE).toInstant();
        SlotEntity slot = slotRepository.save(SlotEntity.builder()
                .id(UUID.randomUUID())
                .doctorId(doctorId)
                .startsAt(slotTime)
                .enabled(true)
                .build());

        UUID intake = UUID.randomUUID();
        String key = "same-idempotency-key-" + UUID.randomUUID();

        BookAppointmentRequest req = BookAppointmentRequest.builder()
                .doctorId(doctorId)
                .slotId(slot.getId())
                .intakeId(intake)
                .build();

        AppointmentResponse app1 = appointmentService.holdAppointment(patient1Id, req, key);
        AppointmentResponse app2 = appointmentService.holdAppointment(patient1Id, req, key);

        assertEquals(app1.getId(), app2.getId());
        assertEquals(app1.getExpiresAt().truncatedTo(ChronoUnit.SECONDS),
                app2.getExpiresAt().truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void testConfirmAppointmentAfterPayment() {
        Instant slotTime = LocalDate.now(VN_ZONE).plusDays(5).atTime(LocalTime.of(16, 45)).atZone(VN_ZONE).toInstant();
        SlotEntity slot = slotRepository.save(SlotEntity.builder()
                .id(UUID.randomUUID())
                .doctorId(doctorId)
                .startsAt(slotTime)
                .enabled(true)
                .build());

        AppointmentResponse app = appointmentService.holdAppointment(patient1Id,
                BookAppointmentRequest.builder()
                        .doctorId(doctorId)
                        .slotId(slot.getId())
                        .intakeId(UUID.randomUUID())
                        .build(),
                null);

        // Confirm
        appointmentService.confirmAppointment(app.getId(), 100000L, "PAY-12345678");

        AppointmentResponse confirmed = appointmentService.getAppointmentById(app.getId(), patient1Id, "PATIENT");
        assertEquals(AppointmentStatus.CONFIRMED, confirmed.getStatus());
        assertNotNull(confirmed.getConfirmedAt());

        SlotClaimEntity claim = slotClaimRepository.findById(slot.getId()).orElseThrow();
        assertEquals(ClaimKind.BOOKED, claim.getKind());
        assertNull(claim.getExpiresAt());
    }

    @Test
    void testRescheduleFlowAndValidations() {
        // Create 2 future slots on Day + 6 and Day + 7
        LocalDate todayVn = LocalDate.now(VN_ZONE);
        Instant slot1Time = todayVn.plusDays(6).atTime(LocalTime.of(17, 0)).atZone(VN_ZONE).toInstant();
        Instant slot2Time = todayVn.plusDays(7).atTime(LocalTime.of(17, 30)).atZone(VN_ZONE).toInstant();

        SlotEntity slot1 = slotRepository.save(SlotEntity.builder()
                .id(UUID.randomUUID())
                .doctorId(doctorId)
                .startsAt(slot1Time)
                .enabled(true)
                .build());

        SlotEntity slot2 = slotRepository.save(SlotEntity.builder()
                .id(UUID.randomUUID())
                .doctorId(doctorId)
                .startsAt(slot2Time)
                .enabled(true)
                .build());

        // Book and confirm slot 1
        AppointmentResponse app = appointmentService.holdAppointment(patient1Id,
                BookAppointmentRequest.builder()
                        .doctorId(doctorId)
                        .slotId(slot1.getId())
                        .intakeId(UUID.randomUUID())
                        .build(),
                null);
        appointmentService.confirmAppointment(app.getId(), 100000L, "PAY-RESCHEDULE");

        AppointmentResponse rescheduled = appointmentService.reschedule(app.getId(), patient1Id, slot2.getId());
        assertEquals(slot2.getId(), rescheduled.getSlotId());
        assertTrue(rescheduled.getVersion() > app.getVersion());

        // Slot 1 should now be released (claim removed)
        assertFalse(slotClaimRepository.findById(slot1.getId()).isPresent());
        // Slot 2 should now have the BOOKED claim
        assertTrue(slotClaimRepository.findById(slot2.getId()).isPresent());
    }

    @Test
    void testDoctorCannotHaveMultipleInProgressConsultations() {
        LocalDate todayVn = LocalDate.now(VN_ZONE);
        Instant slotTime1 = todayVn.plusDays(6).atTime(LocalTime.of(18, 0)).atZone(VN_ZONE).toInstant();
        Instant slotTime2 = todayVn.plusDays(6).atTime(LocalTime.of(18, 30)).atZone(VN_ZONE).toInstant();

        SlotEntity s1 = slotRepository.save(SlotEntity.builder().id(UUID.randomUUID()).doctorId(doctorId).startsAt(slotTime1).enabled(true).build());
        SlotEntity s2 = slotRepository.save(SlotEntity.builder().id(UUID.randomUUID()).doctorId(doctorId).startsAt(slotTime2).enabled(true).build());

        AppointmentResponse a1 = appointmentService.holdAppointment(patient1Id, BookAppointmentRequest.builder().doctorId(doctorId).slotId(s1.getId()).intakeId(UUID.randomUUID()).build(), null);
        appointmentService.confirmAppointment(a1.getId(), 100000L, "PAY-1");

        AppointmentResponse a2 = appointmentService.holdAppointment(patient2Id, BookAppointmentRequest.builder().doctorId(doctorId).slotId(s2.getId()).intakeId(UUID.randomUUID()).build(), null);
        appointmentService.confirmAppointment(a2.getId(), 100000L, "PAY-2");

        // Doctor starts consultation 1
        appointmentService.startConsultation(a1.getId(), doctorId);

        // Doctor attempts to start consultation 2 while consultation 1 is still IN_PROGRESS -> MUST FAIL
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                appointmentService.startConsultation(a2.getId(), doctorId)
        );
        assertTrue(ex.getMessage().contains("đang có một ca khám khác đang diễn ra"));

        // Doctor completes consultation 1
        appointmentService.completeConsultation(a1.getId(), doctorId);

        // Now doctor can start consultation 2
        assertDoesNotThrow(() -> appointmentService.startConsultation(a2.getId(), doctorId));
    }
}
