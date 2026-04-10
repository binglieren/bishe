package com.example.kaoyan.controller;

import com.example.kaoyan.dto.ChatRequest;
import com.example.kaoyan.entity.ChatMessage;
import com.example.kaoyan.entity.ChatSession;
import com.example.kaoyan.service.ChatService;
import com.example.kaoyan.util.AgentDebugLog;
import com.example.kaoyan.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 问答控制器
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "AI 智能问答", description = "基于 RAG 的智能对话")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/session")
    @Operation(summary = "创建对话会话")
    public Result<ChatSession> createSession(Authentication auth,
                                              @RequestParam(required = false) String title) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(chatService.createSession(userId, title));
    }

    @GetMapping("/sessions")
    @Operation(summary = "获取对话会话列表")
    public Result<List<ChatSession>> getSessions(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(chatService.getUserSessions(userId));
    }

    @GetMapping("/session/{sessionId}/messages")
    @Operation(summary = "获取对话历史消息")
    public Result<List<ChatMessage>> getMessages(@PathVariable Long sessionId) {
        return Result.success(chatService.getSessionMessages(sessionId));
    }

    @PostMapping("/send")
    @Operation(summary = "发送消息并获取 AI 回答")
    public Result<ChatMessage> sendMessage(Authentication auth,
                                            @Valid @RequestBody ChatRequest request) {
        Long userId = (Long) auth.getPrincipal();

        // 如果没有 sessionId，创建新会话
        Long sessionId = request.getSessionId();
        if (sessionId == null) {
            ChatSession session = chatService.createSession(userId, null);
            sessionId = session.getId();
        }

        // #region agent log
        String um = request.getMessage();
        AgentDebugLog.ndjson("H1", "ChatController.sendMessage", "resolved session",
                "{\"userId\":" + userId + ",\"sessionId\":" + sessionId + ",\"msgLen\":"
                        + (um != null ? um.length() : 0) + "}");
        // #endregion

        return Result.success(chatService.sendMessage(userId, sessionId, request.getMessage()));
    }

    @DeleteMapping("/session/{sessionId}")
    @Operation(summary = "删除对话会话")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return Result.success("删除成功");
    }
}
