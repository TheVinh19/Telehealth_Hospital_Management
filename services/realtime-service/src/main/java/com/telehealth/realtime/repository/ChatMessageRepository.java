package com.telehealth.realtime.repository;

import com.telehealth.realtime.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findByRoomIdOrderByIdAsc(UUID roomId);
    Optional<ChatMessageEntity> findByRoomIdAndSenderIdAndClientMessageId(UUID roomId, UUID senderId, UUID clientMessageId);
}
