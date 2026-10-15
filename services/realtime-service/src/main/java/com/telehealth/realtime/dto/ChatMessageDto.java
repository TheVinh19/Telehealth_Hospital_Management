package com.telehealth.realtime.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private Long id;
    private UUID roomId;
    private UUID senderId;
    private UUID clientMessageId;
    private String body;
    /**
     * Frontend-compatible alias of {@link #body}.  Older API consumers use
     * {@code body}, while the consultation UI uses {@code content}.
     */
    private String content;
    private String senderRole;
    private String senderName;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
}
