package com.telehealth.clinical.service;

import com.telehealth.contracts.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class AppointmentLookupClient {
    private final RestClient client;

    public AppointmentLookupClient(@Value("${services.appointment.url:http://localhost:8082}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public record AppointmentContext(UUID id, UUID intakeId, UUID patientId, UUID doctorId, String status) {}

    public AppointmentContext getForDoctor(UUID appointmentId, UUID doctorUserId) {
        ApiResponse<AppointmentContext> response;
        try {
            response = client.get().uri("/api/v1/appointments/{id}", appointmentId)
                    .header("X-User-Id", doctorUserId.toString())
                    .header("X-User-Role", "DOCTOR")
                    .retrieve().body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException exception) {
            throw new IllegalStateException("Không thể xác thực lịch hẹn với bác sĩ phụ trách. Vui lòng kiểm tra lịch hẹn và thử lại.");
        }
        AppointmentContext appointment = response != null && response.isSuccess() ? response.getData() : null;
        if (appointment == null || !appointmentId.equals(appointment.id())
                || appointment.intakeId() == null || appointment.patientId() == null) {
            throw new IllegalStateException("Lịch hẹn chưa có thông tin biểu mẫu khám hợp lệ");
        }
        if (appointment.status() == null || !java.util.Set.of("CONFIRMED", "IN_PROGRESS", "COMPLETED").contains(appointment.status())) {
            throw new IllegalStateException("Không thể lập báo cáo cho lịch hẹn chưa xác nhận hoặc đã hủy");
        }
        return appointment;
    }
}
