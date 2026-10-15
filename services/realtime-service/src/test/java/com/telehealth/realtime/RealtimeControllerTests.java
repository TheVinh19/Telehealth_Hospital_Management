package com.telehealth.realtime;

import com.telehealth.realtime.controller.RealtimeController;
import com.telehealth.realtime.service.RealtimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RealtimeController.class)
class RealtimeControllerTests {
    @Autowired
    private MockMvc mvc;

    @MockBean
    private RealtimeService service;

    @Test
    void roomCloseRequiresTheServiceCredentialRatherThanBrowserRoleHeaders() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        String path = "/api/v1/internal/rooms/by-appointment/" + appointmentId + "/close";
        mvc.perform(post(path).header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).header("X-Service-Token", "wrong-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);

        mvc.perform(post(path).header("X-Service-Token", "telehealth-local-internal-token-2026"))
                .andExpect(status().isOk());
        verify(service).closeByAppointment(appointmentId);
    }
}
