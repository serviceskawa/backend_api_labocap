package com.labo.anapath.messaging;

import com.labo.anapath.common.dto.PageResponse;

import java.util.UUID;

public interface ChatService {

    PageResponse<ChatResponseDto> findAll(int page, int size, UUID userId);

    PageResponse<ChatResponseDto> findConversation(UUID userId, UUID receiverId, int page, int size);

    ChatResponseDto send(ChatRequestDto dto, UUID senderId, UUID branchId);

    ChatResponseDto markAsRead(UUID chatId, UUID currentUserId);

    int markAllAsRead(UUID senderId, UUID receiverId);

    /** Nombre de messages non lus reçus par l'utilisateur (pour le badge de la sidebar). */
    long countUnread(UUID userId);
}
