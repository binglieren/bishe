package com.example.kaoyan.controller;

import com.example.kaoyan.agent.AgentContext;
import com.example.kaoyan.agent.AgentOrchestrator;
import com.example.kaoyan.dto.ChatRequest;
import com.example.kaoyan.dto.ChatSessionDTO;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;

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
    private final AgentOrchestrator agentOrchestrator;
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

    @PatchMapping("/message/{messageId}/render")
    @Operation(summary = "保存消息的预渲染 HTML（前端 KaTeX 转译完成后回传，跨设备共享）")
    public Result<Void> patchMessageRender(Authentication auth,
                                            @PathVariable Long messageId,
                                            @RequestBody Map<String, String> body) {
        Long userId = (Long) auth.getPrincipal();
        String contentHtml = body == null ? null : body.get("contentHtml");
        String renderMeta = body == null ? null : body.get("renderMeta");
        chatService.updateMessageRender(userId, messageId, contentHtml, renderMeta);
        return Result.success(null);
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
        String audioBase64 = llmService.synthesizeSpeech(
                request.getText(), request.getVoiceName(), userId);
        Map<String, String> payload = new HashMap<>();
        payload.put("audio", audioBase64);
        // mime 由具体 TTS 路由决定（DashScope=mp3、Gemini=wav）
        payload.put("mimeType", llmService.getTtsMimeType());
        return Result.success(payload);
    }

    @PostMapping(value = "/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "真流式 SSE，token 由 body 传入")
    public SseEmitter sendStream(@RequestBody Map<String, Object> body) {
        Long userId = extractTokenUserId(body);
        if (userId == null) throw new RuntimeException("token required");
        SseEmitter emitter = new SseEmitter(300_000L);

        final String message = (String) body.get("message");
        final Object sidObj = body.get("sessionId");
        final Long reqSessionId = sidObj instanceof Number n ? n.longValue() : null;
        final String imageBase64 = (String) body.get("image");
        final boolean hasImage = imageBase64 != null && !imageBase64.isBlank();

        Flux<String> flux;
        if (hasImage) {
            System.out.println("SSE path: image");
            flux = Flux.create(sink -> {
                try {
                    Long sid = reqSessionId != null ? reqSessionId : chatService.createSession(userId, null).getId();
                    emitter.send(SseEmitter.event().name("session").data(Map.of("sessionId", sid)));
                    ChatMessage reply = chatService.sendMessage(userId, sid, message, imageBase64);
                    String content = reply.getContent();
                    if (content != null) {
                        for (char c : content.toCharArray()) sink.next(String.valueOf(c));
                    }
                    sink.complete();
                } catch (Exception e) { sink.error(e); }
            });
        } else {
            System.out.println("SSE path: text, userId=" + userId + " message=" + (message != null ? message.substring(0, Math.min(20, message.length())) : "null"));

            // 先创建 session + 保存用户消息
            Long sid = reqSessionId;
            if (sid == null) {
                ChatSession session = chatService.createSession(userId, null);
                sid = session.getId();
            }
            // 保存用户消息
            ChatMessage userMsg = new ChatMessage();
            userMsg.setSessionId(sid);
            userMsg.setRole("user");
            userMsg.setContent(message);
            chatService.saveMessage(userMsg);

            final Long finalSid = sid;
            StringBuilder fullResponse = new StringBuilder();

            boolean thinking = false;
            ChatSession session = chatService.getSession(sid);
            if (session != null && Boolean.TRUE.equals(session.getThinkingEnabled())) {
                thinking = true;
            }

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", llmService.resolveSystemPrompt(userId)));
            messages.add(Map.of("role", "user", "content", message));
            flux = llmService.chatStream(messages, userId, thinking)
                .doOnNext(token -> fullResponse.append(token))
                .doOnComplete(() -> {
                    // 保存 AI 回复
                    ChatMessage aiMsg = new ChatMessage();
                    aiMsg.setSessionId(finalSid);
                    aiMsg.setRole("assistant");
                    aiMsg.setContent(fullResponse.toString());
                    chatService.saveMessage(aiMsg);
                });
        }

        System.out.println("SSE: about to subscribe flux");
        flux.subscribe(
            token -> {
                try {
                    emitter.send(SseEmitter.event().name("token").data(token));
                }
                catch (Exception e) { /* client gone */ }
            },
            error -> {
                System.err.println("SSE error: " + error.getMessage());
                error.printStackTrace();
                try { emitter.send(SseEmitter.event().name("error").data(error.getMessage())); } catch (Exception ex) {}
                emitter.completeWithError(error);
            },
            () -> {
                try {
                    emitter.send(SseEmitter.event().name("done").data(Map.of()));
                    emitter.complete();
                } catch (Exception e) { /* ignore */ }
            }
        );

        return emitter;
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
