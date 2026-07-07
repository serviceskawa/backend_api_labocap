package com.labo.anapath.messaging;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.user.UserResponseDto;
import com.labo.anapath.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<ChatResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID receiverId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (receiverId != null) {
            return ResponseEntity.ok(ApiResponse.success(
                    chatService.findConversation(principal.getId(), receiverId, page, size)));
        }
        return ResponseEntity.ok(ApiResponse.success(
                chatService.findAll(page, size, principal.getId())));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatResponseDto>> send(
            @Valid @RequestBody ChatRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message envoyé",
                        chatService.send(dto, principal.getId(), principal.getBranchId())));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ChatResponseDto>> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Message marqué comme lu",
                chatService.markAsRead(id, principal.getId())));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(
            @RequestParam UUID senderId,
            @AuthenticationPrincipal UserPrincipal principal) {
        int count = chatService.markAllAsRead(senderId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @GetMapping("/users")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<UserResponseDto>>> findUsers(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.findAll(0, 200, principal.getBranchId()).content()));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", chatService.countUnread(principal.getId()))));
    }
}
