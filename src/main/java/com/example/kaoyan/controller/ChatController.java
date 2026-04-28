package com.example.kaoyan.controller;

import com.example.kaoyan.dto.ChatRequest;
import com.example.kaoyan.dto.ChatSessionDTO;
import com.example.kaoyan.dto.TranscribeRequest;
import com.example.kaoyan.dto.TtsRequest;
import com.example.kaoyan.entity.ChatMessage;
import com.example.kaoyan.entity.ChatSession;
import com.example.kaoyan.service.ChatService;
import com.example.kaoyan.service.LlmService;
import com.example.kaoyan.util.AgentDebugLog;
import com.example.kaoyan.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 问答控制器
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "AI 智能问答", description = "基于 RAG 的智能对话")
public class ChatController {

    private final ChatService chatService;
    private final LlmService llmService;

    @PostMapping("/session")
    @Operation(summary = "创建对话会话")
    public Result<ChatSession> createSession(Authentication auth,
                                              @RequestParam(required = false) String title) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(chatService.createSession(userId, title));
    }

    @GetMapping("/sessions")
    @Operation(summary = "获取对话会话列表（带预览，用于一级页面）")
    public Result<List<ChatSessionDTO>> getSessions(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(chatService.getUserSessionsWithPreview(userId));
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

        return Result.success(chatService.sendMessage(userId, sessionId, request.getMessage(), request.getImage()));
    }

    @DeleteMapping("/session/{sessionId}")
    @Operation(summary = "删除对话会话")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return Result.success("删除成功");
    }

    @PatchMapping("/session/{sessionId}/knowledge-base")
    @Operation(summary = "为对话会话绑定（或解绑）知识库")
    public Result<ChatSession> bindKnowledgeBase(Authentication auth,
                                                  @PathVariable Long sessionId,
                                                  @RequestBody Map<String, Long> body) {
        Long userId = (Long) auth.getPrincipal();
        Long kbId = body == null ? null : body.get("knowledgeBaseId");
        return Result.successWithMessage("已更新知识库绑定",
                chatService.bindKnowledgeBase(userId, sessionId, kbId));
    }

    @PostMapping("/transcribe")
    @Operation(summary = "语音识别：将录音转为文字")
    public Result<Map<String, String>> transcribe(Authentication auth,
                                                   @Valid @RequestBody TranscribeRequest request) {
        Long userId = (Long) auth.getPrincipal();
        AgentDebugLog.ndjson("A0", "ChatController.transcribe", "request",
                "{\"userId\":" + userId + ",\"format\":\"" + request.getFormat() + "\",\"size\":"
                        + (request.getAudio() == null ? 0 : request.getAudio().length()) + "}");
        String text = llmService.transcribeAudio(request.getAudio(), request.getFormat(), userId);
        Map<String, String> payload = new HashMap<>();
        payload.put("text", text);
        return Result.success(payload);
    }

    @PostMapping("/tts")
    @Operation(summary = "语音合成：把 AI 回答的文本转成可播放的音频")
    public Result<Map<String, String>> tts(Authentication auth,
                                            @Valid @RequestBody TtsRequest request) {
        Long userId = (Long) auth.getPrincipal();
        AgentDebugLog.ndjson("T0", "ChatController.tts", "request",
                "{\"userId\":" + userId + ",\"voice\":\"" + request.getVoiceName() + "\",\"len\":"
                        + (request.getText() == null ? 0 : request.getText().length()) + "}");
        String audioBase64 = llmService.synthesizeSpeech(
                request.getText(), request.getVoiceName(), userId);
        Map<String, String> payload = new HashMap<>();
        payload.put("audio", audioBase64);
        // mime 由具体 TTS 路由决定（DashScope=mp3、Gemini=wav）
        payload.put("mimeType", llmService.getTtsMimeType());
        return Result.success(payload);
    }
}
