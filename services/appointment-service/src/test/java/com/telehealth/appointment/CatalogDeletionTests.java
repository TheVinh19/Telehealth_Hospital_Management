package com.telehealth.appointment;

import com.telehealth.appointment.dto.BookAppointmentRequest;
import com.telehealth.appointment.dto.CreateDoctorProfileRequest;
import com.telehealth.appointment.dto.CreateSlotRequest;
import com.telehealth.appointment.dto.DoctorDto;
import com.telehealth.appointment.dto.SlotDto;
import com.telehealth.appointment.repository.DoctorRepository;
import com.telehealth.appointment.repository.SlotRepository;
import com.telehealth.appointment.service.AppointmentDataInitializer;
import com.telehealth.appointment.service.AppointmentService;
import com.telehealth.appointment.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogDeletionTests {

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminDeleteSlotEndpointReturnsSuccess() throws Exception {
        DoctorDto doctor = createDoctor();
        SlotDto slot = createSlot(doctor.getId(), 18);

        mockMvc.perform(delete("/api/v1/admin/slots/{id}", slot.getId())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertFalse(slotRepository.findById(slot.getId()).orElseThrow().isEnabled());
    }

    @Test
    void deleteEndpointRejectsRequestsWithoutAdminRole() throws Exception {
        DoctorDto doctor = createDoctor();
        SlotDto slot = createSlot(doctor.getId(), 19);

        mockMvc.perform(delete("/api/v1/admin/slots/{id}", slot.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        assertTrue(slotRepository.findById(slot.getId()).orElseThrow().isEnabled());
    }

    @Test
    void deleteSlotDisablesAndHidesAnUnbookedSlot() {
        DoctorDto doctor = createDoctor();
        SlotDto slot = createSlot(doctor.getId(), 20);

        catalogService.deleteSlot(slot.getId());

        assertFalse(slotRepository.findById(slot.getId()).orElseThrow().isEnabled());
        assertTrue(catalogService.getSlots(doctor.getId(), null).stream()
                .noneMatch(candidate -> candidate.getId().equals(slot.getId())));
    }

    @Test
    void deleteSlotRejectsAnActiveAppointment() {
        DoctorDto doctor = createDoctor();
        SlotDto slot = createSlot(doctor.getId(), 21);
        appointmentService.holdAppointment(
                UUID.randomUUID(),
                BookAppointmentRequest.builder()
                        .doctorId(doctor.getId())
                        .slotId(slot.getId())
                        .intakeId(UUID.randomUUID())
                        .build(),
                null
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> catalogService.deleteSlot(slot.getId())
        );

        assertTrue(error.getMessage().contains("Không thể xóa ca khám"));
        assertTrue(slotRepository.findById(slot.getId()).orElseThrow().isEnabled());
    }

    @Test
    void deleteDoctorDeactivatesProfileAndAllSlots() {
        DoctorDto doctor = createDoctor();
        SlotDto firstSlot = createSlot(doctor.getId(), 22);
        SlotDto secondSlot = createSlot(doctor.getId(), 23);

        catalogService.deleteDoctor(doctor.getId());

        assertFalse(doctorRepository.findById(doctor.getId()).orElseThrow().isActive());
        assertFalse(slotRepository.findById(firstSlot.getId()).orElseThrow().isEnabled());
        assertFalse(slotRepository.findById(secondSlot.getId()).orElseThrow().isEnabled());
        assertTrue(catalogService.getDoctors(null).stream()
                .noneMatch(candidate -> candidate.getId().equals(doctor.getId())));
    }

    @Test
    void deleteDoctorRejectsAnActiveAppointment() {
        DoctorDto doctor = createDoctor();
        SlotDto slot = createSlot(doctor.getId(), 24);
        appointmentService.holdAppointment(
                UUID.randomUUID(),
                BookAppointmentRequest.builder()
                        .doctorId(doctor.getId())
                        .slotId(slot.getId())
                        .intakeId(UUID.randomUUID())
                        .build(),
                null
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> catalogService.deleteDoctor(doctor.getId())
        );

        assertTrue(error.getMessage().contains("Không thể xóa bác sĩ"));
        assertTrue(doctorRepository.findById(doctor.getId()).orElseThrow().isActive());
    }

    @Test
    void creatingProfileAgainReactivatesADeletedDoctor() {
        UUID userId = UUID.randomUUID();
        CreateDoctorProfileRequest originalRequest = doctorRequest(userId, "Bác sĩ thử nghiệm");
        DoctorDto original = catalogService.createDoctor(originalRequest);
        catalogService.deleteDoctor(original.getId());

        DoctorDto restored = catalogService.createDoctor(doctorRequest(userId, "Bác sĩ đã khôi phục"));

        assertEquals(original.getId(), restored.getId());
        assertEquals("Bác sĩ đã khôi phục", restored.getDisplayName());
        assertTrue(restored.isActive());
    }

    private DoctorDto createDoctor() {
        return catalogService.createDoctor(doctorRequest(UUID.randomUUID(), "Bác sĩ thử nghiệm"));
    }

    private CreateDoctorProfileRequest doctorRequest(UUID userId, String displayName) {
        return CreateDoctorProfileRequest.builder()
                .userId(userId)
                .specialtyId(AppointmentDataInitializer.SPECIALTY_TIM_MACH_ID)
                .displayName(displayName)
                .bio("Hồ sơ dùng để kiểm thử")
                .build();
    }

    private SlotDto createSlot(UUID doctorId, long daysFromNow) {
        return catalogService.createSlot(CreateSlotRequest.builder()
                .doctorId(doctorId)
                .startsAt(Instant.now().plus(daysFromNow, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))
                .build());
    }
}
