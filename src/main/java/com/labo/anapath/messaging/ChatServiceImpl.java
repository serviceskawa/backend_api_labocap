package com.labo.anapath.messaging;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final ChatMapper chatMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatResponseDto> findAll(int page, int size, UUID userId) {
        return PageResponse.of(chatRepository.findAllByUserId(userId, PageRequest.of(page, size))
                .map(chatMapper::toResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatResponseDto> findConversation(UUID userId, UUID receiverId, int page, int size) {
        return PageResponse.of(chatRepository.findConversation(userId, receiverId, PageRequest.of(page, size))
                .map(chatMapper::toResponseDto));
    }

    @Override
    @Transactional
    public ChatResponseDto send(ChatRequestDto dto, UUID senderId, UUID branchId) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", senderId));
        User receiver = userRepository.findById(dto.getReceiverId())
                .orElseThrow(() -> new ResourceNotFoundException("Destinataire", dto.getReceiverId()));

        Chat chat = new Chat();
        chat.setBranchId(branchId);
        chat.setSender(sender);
        chat.setReceiver(receiver);
        chat.setMessage(dto.getMessage());
        chat.setIsRead(false);
        return chatMapper.toResponseDto(chatRepository.save(chat));
    }

    @Override
    @Transactional
    public ChatResponseDto markAsRead(UUID chatId, UUID currentUserId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", chatId));
        if (!chat.getReceiver().getId().equals(currentUserId)) {
            throw new InvalidOperationException("Seul le destinataire peut marquer ce message comme lu");
        }
        chat.setIsRead(true);
        return chatMapper.toResponseDto(chatRepository.save(chat));
    }

    @Override
    @Transactional
    public int markAllAsRead(UUID senderId, UUID receiverId) {
        List<Chat> unread = chatRepository.findByReceiverIdAndSenderIdAndIsReadFalse(receiverId, senderId);
        unread.forEach(c -> c.setIsRead(true));
        chatRepository.saveAll(unread);
        return unread.size();
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return chatRepository.countByReceiverIdAndIsReadFalse(userId);
    }
}
