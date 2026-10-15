package com.telehealth.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSpecialtyRequest {
    @NotBlank(message = "Tên chuyên khoa không được để trống")
    private String name;
}
