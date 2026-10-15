package com.telehealth.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalingMessage {
    private String type; // OFFER, ANSWER, ICE_CANDIDATE, JOIN, LEAVE
    private UUID roomId;
    private UUID senderId;
    private UUID targetId;
    private Object payload;
}
