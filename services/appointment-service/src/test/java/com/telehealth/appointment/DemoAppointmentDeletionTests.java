package com.telehealth.appointment;

import com.telehealth.appointment.dto.*;
import com.telehealth.appointment.repository.*;
import com.telehealth.appointment.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DemoAppointmentDeletionTests {
    @Autowired AppointmentService service;
    @Autowired CatalogService catalog;
    @Autowired AppointmentRepository appointments;
    @Autowired SlotClaimRepository claims;
    @Autowired MockMvc mvc;
    @Autowired EntityManager entityManager;
    @Autowired AppointmentDataInitializer initializer;

    private AppointmentResponse book(UUID patientId) {
        DoctorDto doctor = catalog.createDoctor(CreateDoctorProfileRequest.builder()
                .userId(UUID.randomUUID()).specialtyId(AppointmentDataInitializer.SPECIALTY_TIM_MACH_ID)
                .displayName("Test doctor").build());
        SlotDto slot = catalog.createSlot(CreateSlotRequest.builder().doctorId(doctor.getId())
                .startsAt(Instant.now().plus(10, ChronoUnit.DAYS)).build());
        return service.holdAppointment(patientId, BookAppointmentRequest.builder()
                .doctorId(doctor.getId()).slotId(slot.getId()).intakeId(UUID.randomUUID()).build(), null);
    }

    @Test
    void adminCanRemoveDemoButRealAppointmentsAreProtected() throws Exception {
        AppointmentResponse demo = book(AppointmentDataInitializer.PATIENT_1_ID);
        AppointmentResponse real = book(UUID.randomUUID());
        assertTrue(demo.isDemo());
        assertFalse(real.isDemo());
        mvc.perform(delete("/api/v1/admin/appointments/{id}", real.getId())
                .header("X-User-Role", "ADMIN").header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/admin/appointments/{id}", demo.getId())
                .header("X-User-Role", "ADMIN").header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        assertTrue(appointments.findById(demo.getId()).isEmpty());
        assertEquals(1, appointments.countStoredById(demo.getId()), "Soft deletion preserves history");
        assertTrue(claims.findByAppointmentId(demo.getId()).isEmpty());
        assertTrue(appointments.findById(real.getId()).isPresent());
        assertTrue(claims.findByAppointmentId(real.getId()).isPresent());
    }

    @Test
    void nonAdminsCannotListOrRemoveDemoAppointments() throws Exception {
        AppointmentResponse demo = book(AppointmentDataInitializer.PATIENT_1_ID);
        mvc.perform(get("/api/v1/admin/appointments/demo")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/appointments/{id}", demo.getId())
                .header("X-User-Role", "PATIENT").header("X-User-Id", demo.getPatientId()))
                .andExpect(status().isForbidden());
        assertTrue(claims.findByAppointmentId(demo.getId()).isPresent());
    }

    @Test
    void deletingSeededDemoDoesNotRecreateItOnRestart() {
        UUID id = AppointmentDataInitializer.SAMPLE_APPT_1_ID;
        service.deleteDemoAppointment(id, UUID.randomUUID());
        entityManager.flush();
        entityManager.clear();
        initializer.run(null);
        assertEquals(1, appointments.countStoredById(id));
        assertTrue(appointments.findById(id).isEmpty());
    }
}
