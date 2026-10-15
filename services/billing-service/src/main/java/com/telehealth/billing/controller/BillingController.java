package com.telehealth.billing.controller;

import com.telehealth.billing.dto.CreateOrderRequest;
import com.telehealth.billing.dto.MockPayRequest;
import com.telehealth.billing.dto.PaymentOrderResponse;
import com.telehealth.billing.service.BillingService;
import com.telehealth.contracts.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/api/v1/billing/orders")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        PaymentOrderResponse response = billingService.getOrCreateOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo đơn thanh toán thành công", response));
    }

    @GetMapping({"/api/v1/appointments/{appointmentId}/payment", "/api/v1/billing/orders/{appointmentId}"})
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> getPaymentDetails(
            @PathVariable UUID appointmentId) {
        PaymentOrderResponse response = billingService.getPaymentByAppointmentId(appointmentId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/api/v1/billing/mock-pay")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> mockPay(
            @Valid @RequestBody MockPayRequest request) {
        PaymentOrderResponse response = billingService.mockPay(request);
        return ResponseEntity.ok(ApiResponse.ok("Thanh toán mô phỏng thành công", response));
    }

    @GetMapping("/api/v1/admin/billing/orders")
    public ResponseEntity<ApiResponse<List<PaymentOrderResponse>>> getAllOrders(
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Yêu cầu quyền ADMIN"));
        }
        return ResponseEntity.ok(ApiResponse.ok(billingService.getAllOrders()));
    }
}
