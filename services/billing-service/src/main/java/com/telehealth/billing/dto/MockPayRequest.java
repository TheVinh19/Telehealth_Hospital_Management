package com.telehealth.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockPayRequest {
    @NotBlank(message = "Mã thanh toán không được để trống")
    private String paymentCode;
}
