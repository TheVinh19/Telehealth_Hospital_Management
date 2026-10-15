package com.telehealth.appointment;

import com.telehealth.appointment.repository.SpecialtyRepository;
import com.telehealth.appointment.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SpecialtySeedIntegrationTests {

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seedsFullVietnameseHospitalSpecialtyCatalog() {
        Set<String> specialtyNames = specialtyRepository.findByActiveTrue().stream()
                .map(specialty -> specialty.getName())
                .collect(Collectors.toSet());

        assertTrue(specialtyNames.size() >= 49);
        assertTrue(specialtyNames.containsAll(Set.of(
                "Tim mạch",
                "Nhi khoa",
                "Nội tổng quát",
                "Hồi sức cấp cứu",
                "Tiêu hóa - Gan mật",
                "Cơ xương khớp",
                "Thận - Tiết niệu",
                "Hô hấp",
                "Thần kinh",
                "Da liễu",
                "Nội tiết",
                "Ngoại tổng quát",
                "Chấn thương chỉnh hình",
                "Ung bướu",
                "Sản phụ khoa",
                "Mắt",
                "Tai Mũi Họng",
                "Răng Hàm Mặt",
                "Phục hồi chức năng",
                "Dinh dưỡng",
                "Chẩn đoán hình ảnh",
                "Y học dự phòng"
        )));
    }

    @Test
    void patientCatalogContainsOnlyFiveSupportedSpecialtiesInDisplayOrder() {
        List<String> names = catalogService.getPatientSpecialties().stream()
                .map(specialty -> specialty.getName())
                .toList();

        assertEquals(List.of(
                "Nội khoa tổng quát",
                "Nội tim mạch",
                "Nhi khoa",
                "Da liễu",
                "Thần kinh"
        ), names);
        assertTrue(catalogService.getAdminSpecialties().size() >= 49);
    }

    @Test
    void adminSpecialtyCatalogRequiresAdminRoleAndKeepsFullCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/admin/specialties"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/specialties")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(49));

        mockMvc.perform(get("/api/v1/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].name").value("Nội khoa tổng quát"))
                .andExpect(jsonPath("$.data[1].name").value("Nội tim mạch"));
    }
}
