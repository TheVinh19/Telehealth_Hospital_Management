package com.telehealth.realtime.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages", uniqueConstraints = {
        @UniqueConstraint(name = "uq_room_client_msg", columnNames = {"room_id", "sender_id", "client_message_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    @Column(name = "body_ciphertext", nullable = false, columnDefinition = "BYTEA")
    private byte[] bodyCiphertext;

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Column(nullable = false)
    private byte[] nonce;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
