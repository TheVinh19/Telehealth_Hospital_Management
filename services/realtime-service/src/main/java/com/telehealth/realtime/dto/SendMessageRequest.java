package com.telehealth.realtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    @NotNull(message = "clientMessageId không được để trống")
    private UUID clientMessageId;

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    private String body;
}
