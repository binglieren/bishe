package com.example.kaoyan.controller;

import com.example.kaoyan.dto.ChatRequest;
import com.example.kaoyan.dto.ChatSessionDTO;
import com.example.kaoyan.dto.StreamChatEvent;
import com.example.kaoyan.dto.TranscribeRequest;
import com.example.kaoyan.dto.TtsRequest;
import com.example.kaoyan.entity.ChatMessage;
import com.example.kaoyan.entity.ChatSession;
import com.example.kaoyan.service.ChatService;
import com.example.kaoyan.service.LlmService;
import com.example.kaoyan.util.AgentDebugLog;
import com.example.kaoyan.util.JwtUtil;
import com.example.kaoyan.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "AI 智能问答", description = "基于 RAG 的智能对话")
public class ChatController {

    private final ChatService chatService;
    private final LlmService llmService;
    private final JwtUtil jwtUtil;

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
        Long sessionId = request.getSessionId();
        if (sessionId == null) {
            ChatSession session = chatService.createSession(userId, null);
            sessionId = session.getId();
        }
        String um = request.getMessage();
        AgentDebugLog.ndjson("H1", "ChatController.sendMessage", "resolved session",
                "{\"userId\":" + userId + ",\"sessionId\":" + sessionId + ",\"msgLen\":"
                        + (um != null ? um.length() : 0) + "}");
        return Result.success(chatService.sendMessage(userId, sessionId, request.getMessage(), request.getImage()));
    }

    @DeleteMapping("/session/{sessionId}")
    @Operation(summary = "删除对话会话")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return Result.success("删除成功");
    }

    @PatchMapping("/session/{sessionId}/system-prompt")
    @Operation(summary = "设置会话级自定义系统 Prompt")
    public Result<ChatSession> setSystemPrompt(Authentication auth,
                                                @PathVariable Long sessionId,
                                                @RequestBody Map<String, String> body) {
        Long userId = (Long) auth.getPrincipal();
        String prompt = body == null ? null : body.get("systemPrompt");
        return Result.successWithMessage(prompt != null ? "已设置自定义指令" : "已清除自定义指令",
                chatService.setSessionSystemPrompt(userId, sessionId, prompt));
    }

    @PutMapping("/session/{sessionId}/knowledge-bases")
    @Operation(summary = "设置会话绑定的知识库列表（覆盖式）")
    public Result<ChatSession> setKnowledgeBases(Authentication auth,
                                                   @PathVariable Long sessionId,
                                                   @RequestBody Map<String, Object> body) {
        Long userId = (Long) auth.getPrincipal();
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = body == null ? List.of() : (List<Integer>) body.getOrDefault("knowledgeBaseIds", List.of());
        List<Long> kbIds = rawIds.stream().map(Integer::longValue).toList();
        chatService.setSessionKnowledgeBases(userId, sessionId, kbIds);
        return Result.successWithMessage("已更新知识库绑定", chatService.getSession(sessionId));
    }

    @PostMapping("/session/{sessionId}/knowledge-bases/{kbId}")
    @Operation(summary = "添加单个知识库绑定")
    public Result<Void> addKnowledgeBase(Authentication auth,
                                          @PathVariable Long sessionId,
                                          @PathVariable Long kbId) {
        Long userId = (Long) auth.getPrincipal();
        chatService.addSessionKnowledgeBase(userId, sessionId, kbId);
        return Result.success("已添加");
    }

    @DeleteMapping("/session/{sessionId}/knowledge-bases/{kbId}")
    @Operation(summary = "移除单个知识库绑定")
    public Result<Void> removeKnowledgeBase(Authentication auth,
                                             @PathVariable Long sessionId,
                                             @PathVariable Long kbId) {
        Long userId = (Long) auth.getPrincipal();
        chatService.removeSessionKnowledgeBase(userId, sessionId, kbId);
        return Result.success("已移除");
    }

    @GetMapping("/session/{sessionId}/knowledge-bases")
    @Operation(summary = "获取会话当前绑定的知识库列表")
    public Result<List<Long>> getKnowledgeBases(@PathVariable Long sessionId) {
        return Result.success(chatService.getSessionKnowledgeBaseIds(sessionId));
    }

    @PatchMapping("/session/{sessionId}/knowledge-base")
    @Operation(summary = "为对话会话绑定知识库（兼容旧版）")
    public Result<ChatSession> bindKnowledgeBase(Authentication auth,
                                                   @PathVariable Long sessionId,
                                                   @RequestBody Map<String, Long> body) {
        Long userId = (Long) auth.getPrincipal();
        Long kbId = body == null ? null : body.get("knowledgeBaseId");
        return Result.successWithMessage("已更新知识库绑定",
                chatService.bindKnowledgeBase(userId, sessionId, kbId));
    }

    @PatchMapping("/session/{sessionId}/thinking")
    @Operation(summary = "切换会话的深度思考模式")
    public Result<ChatSession> toggleThinking(Authentication auth,
                                                @PathVariable Long sessionId,
                                                @RequestBody Map<String, Boolean> body) {
        Long userId = (Long) auth.getPrincipal();
        boolean enabled = body != null && Boolean.TRUE.equals(body.get("thinkingEnabled"));
        return Result.successWithMessage(enabled ? "已开启深度思考" : "已关闭深度思考",
                chatService.toggleThinking(userId, sessionId, enabled));
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
        String audioBase64 = llmService.synthesizeSpeech(request.getText(), request.getVoiceName(), userId);
        Map<String, String> payload = new HashMap<>();
        payload.put("audio", audioBase64);
        payload.put("mimeType", llmService.getTtsMimeType());
        return Result.success(payload);
    }

    @PatchMapping("/message/{messageId}/render")
    @Operation(summary = "保存消息的预渲染 HTML")
    public Result<Void> patchMessageRender(Authentication auth,
                                            @PathVariable Long messageId,
                                            @RequestBody Map<String, String> body) {
        Long userId = (Long) auth.getPrincipal();
        String contentHtml = body == null ? null : body.get("contentHtml");
        String renderMeta = body == null ? null : body.get("renderMeta");
        chatService.updateMessageRender(userId, messageId, contentHtml, renderMeta);
        return Result.success(null);
    }

    @PostMapping(value = "/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "真流式 SSE，支持 RAG + 历史 + thinking")
    public SseEmitter sendStream(@RequestBody Map<String, Object> body) {
        Long userId = extractTokenUserId(body);
        if (userId == null) throw new RuntimeException("token required");
        SseEmitter emitter = new SseEmitter(600_000L);

        final String message = (String) body.get("message");
        final Object sidObj = body.get("sessionId");
        final Long reqSessionId = sidObj instanceof Number n ? n.longValue() : null;
        final String imageBase64 = (String) body.get("image");

        if (imageBase64 != null && !imageBase64.isBlank()) {
            Flux<String> flux = Flux.create(sink -> {
                try {
                    Long sid = reqSessionId != null ? reqSessionId : chatService.createSession(userId, null).getId();
                    emitter.send(SseEmitter.event().name("session").data(Map.of("sessionId", sid)));
                    ChatMessage reply = chatService.sendMessage(userId, sid, message, imageBase64);
                    String content = reply.getContent();
                    if (content != null) for (char c : content.toCharArray()) sink.next(String.valueOf(c));
                    sink.complete();
                } catch (Exception e) {
                    System.err.println("[SSE image] " + e.getMessage());
                    sink.error(e);
                }
            });
            subscribeFlux(flux, emitter);
            return emitter;
        }

        Long sid = reqSessionId != null ? reqSessionId : chatService.createSession(userId, null).getId();
        final Long finalSid = sid;

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(finalSid);
        userMsg.setRole("user");
        userMsg.setContent(message);
        chatService.saveMessage(userMsg);

        ChatSession session = chatService.getSession(finalSid);
        boolean thinking = session != null && Boolean.TRUE.equals(session.getThinkingEnabled());
        System.out.println("[SSE] sid=" + finalSid + " thinking=" + thinking + " msg=" + (message != null ? message.substring(0, Math.min(30, message.length())) : "null"));

        try {
            emitter.send(SseEmitter.event().name("session").data(
                    Map.of("sessionId", finalSid, "thinkingEnabled", thinking)));
        } catch (Exception e) {
            System.err.println("[SSE] session event failed: " + e.getMessage());
            emitter.completeWithError(e);
            return emitter;
        }

        System.out.println("[SSE] building messages...");
        final List<Map<String, String>> messages;
        try {
            messages = chatService.buildStreamMessages(userId, finalSid, message, userMsg.getId());
            System.out.println("[SSE] messages built, count=" + messages.size());
        } catch (Exception e) {
            System.err.println("[SSE] buildStreamMessages failed: " + e.getMessage());
            e.printStackTrace();
            try { emitter.send(SseEmitter.event().name("error").data(e.getMessage())); } catch (Exception ex) {}
            emitter.completeWithError(e);
            return emitter;
        }

        final Long fUserId = userId;
        CompletableFuture.runAsync(() -> {
            try {
                System.out.println("[SSE] starting chatStream...");
                Flux<StreamChatEvent> flux = llmService.chatStream(messages, fUserId, thinking);
                StringBuilder reasoningBuf = new StringBuilder();
                StringBuilder contentBuf = new StringBuilder();

                flux.doOnNext(event -> {
                    if ("reasoning".equals(event.getType())) {
                        reasoningBuf.append(event.getText());
                    } else {
                        contentBuf.append(event.getText());
                    }
                })
                .doOnComplete(() -> {
                    System.out.println("[SSE] stream complete, content=" + contentBuf.length() + " reasoning=" + reasoningBuf.length());
                    ChatMessage aiMsg = new ChatMessage();
                    aiMsg.setSessionId(finalSid);
                    aiMsg.setRole("assistant");
                    aiMsg.setContent(contentBuf.toString());
                    if (reasoningBuf.length() > 0) {
                        aiMsg.setReasoningContent(reasoningBuf.toString());
                    }
                    chatService.saveMessage(aiMsg);

                    ChatSession s = chatService.getSession(finalSid);
                    if (s != null && "新对话".equals(s.getTitle())) {
                        String title = chatService.generateStreamTitle(fUserId, message, contentBuf.toString());
                        if (title != null && !title.isBlank()) {
                            chatService.updateSessionTitle(finalSid, title);
                        }
                    }
                })
                .doOnError(err -> {
                    System.err.println("[SSE] stream error: " + err.getMessage());
                    err.printStackTrace();
                    try { emitter.send(SseEmitter.event().name("error").data(err.getMessage())); } catch (Exception ex) {}
                    emitter.completeWithError(err);
                })
                .subscribe(
                    event -> {
                        try {
                            if ("reasoning".equals(event.getType())) {
                                emitter.send(SseEmitter.event().name("reasoning").data(event.getText()));
                            } else {
                                emitter.send(SseEmitter.event().name("token").data(event.getText()));
                            }
                        } catch (Exception e) {
                            // client disconnected
                        }
                    },
                    error -> {
                        System.err.println("[SSE] subscribe error: " + error.getMessage());
                    },
                    () -> {
                        try {
                            emitter.send(SseEmitter.event().name("done").data(Map.of()));
                            emitter.complete();
                        } catch (Exception e) {
                            System.err.println("[SSE] done event failed: " + e.getMessage());
                        }
                    }
                );
            } catch (Exception e) {
                System.err.println("[SSE] async error: " + e.getMessage());
                e.printStackTrace();
                try { emitter.send(SseEmitter.event().name("error").data(e.getMessage())); emitter.completeWithError(e); } catch (Exception ex) {}
            }
        });

        return emitter;
    }

    private void subscribeFlux(Flux<String> flux, SseEmitter emitter) {
        flux.subscribe(
            token -> { try { emitter.send(SseEmitter.event().name("token").data(token)); } catch (Exception ex) {} },
            error -> { try { emitter.send(SseEmitter.event().name("error").data(error.getMessage())); emitter.completeWithError(error); } catch (Exception ex) {} },
            () -> { try { emitter.send(SseEmitter.event().name("done").data(Map.of())); emitter.complete(); } catch (Exception ex) {} }
        );
    }

    private Long extractTokenUserId(Map<String, Object> body) {
        try {
            Object tok = body.get("token");
            if (tok == null) return null;
            String token = tok.toString();
            if (token.startsWith("Bearer ")) token = token.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            return null;
        }
    }
}
