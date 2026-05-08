package com.example.kaoyan.controller;

import com.example.kaoyan.agent.AgentContext;
import com.example.kaoyan.agent.AgentOrchestrator;
import com.example.kaoyan.dto.ChatRequest;
import com.example.kaoyan.dto.ChatSessionDTO;
import com.example.kaoyan.dto.TranscribeRequest;
import com.example.kaoyan.dto.TtsRequest;
import com.example.kaoyan.entity.*;
import com.example.kaoyan.repository.*;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "AI 智能问答", description = "基于 RAG 的智能对话")
public class ChatController {

    private final ChatService chatService;
    private final LlmService llmService;
    private final AgentOrchestrator agentOrchestrator;
    private final JwtUtil jwtUtil;
    private final SessionKbBindingRepository sessionKbBindingRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;

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

    // === F1: 会话级自定义 Prompt ===

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

    // === F2: 多知识库绑定 ===

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

    // === 兼容旧单知识库绑定 ===

    @PatchMapping("/session/{sessionId}/knowledge-base")
    @Operation(summary = "为对话会话绑定（或解绑）知识库（兼容旧版）")
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

    // === 语音 ===

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
        payload.put("mimeType", llmService.getTtsMimeType());
        return Result.success(payload);
    }

    // === 渲染缓存 ===

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

    // ============================================================
    // F5: 流式端点改造 — 历史 + RAG + thinking + 自动标题
    // ============================================================

    @PostMapping(value = "/send/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "真流式 SSE，支持 RAG + 历史 + thinking")
    @Transactional
    public SseEmitter sendStream(@RequestBody Map<String, Object> body) {
        Long userId = extractTokenUserId(body);
        if (userId == null) throw new RuntimeException("token required");
        SseEmitter emitter = new SseEmitter(300_000L);

        final String message = (String) body.get("message");
        final Object sidObj = body.get("sessionId");
        final Long reqSessionId = sidObj instanceof Number n ? n.longValue() : null;
        final String imageBase64 = (String) body.get("image");
        final boolean hasImage = imageBase64 != null && !imageBase64.isBlank();

        if (hasImage) {
            Flux<String> flux = Flux.create(sink -> {
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
            flux.subscribe(
                token -> { try { emitter.send(SseEmitter.event().name("token").data(token)); } catch (Exception ex) {} },
                error -> { try { emitter.send(SseEmitter.event().name("error").data(error.getMessage())); emitter.completeWithError(error); } catch (Exception ex) {} },
                () -> { try { emitter.send(SseEmitter.event().name("done").data(Map.of())); emitter.complete(); } catch (Exception ex) {} }
            );
            return emitter;
        }

        // === 文本路径 ===

        Long sid = reqSessionId;
        final boolean isNewSession = (sid == null);
        if (sid == null) {
            ChatSession session = chatService.createSession(userId, null);
            sid = session.getId();
        }
        final Long finalSid = sid;

        // 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(finalSid);
        userMsg.setRole("user");
        userMsg.setContent(message);
        chatMessageRepository.save(userMsg);

        // thinking 状态
        ChatSession session = chatSessionRepository.findById(finalSid).orElse(null);
        boolean thinking = session != null && Boolean.TRUE.equals(session.getThinkingEnabled());

        try {
            emitter.send(SseEmitter.event().name("session").data(
                    Map.of("sessionId", finalSid, "thinkingEnabled", thinking)));
        } catch (Exception ignored) {}

        // 构建消息列表（异步，不阻塞 SSE 返回）
        final Long fUserId = userId;
        final Long fFinalSid = finalSid;
        CompletableFuture.runAsync(() -> {
            try {
                List<Map<String, String>> messages = new ArrayList<>();

                // 1. System prompt（F1）
                String systemPrompt = chatService.resolveSystemPrompt(fUserId, fFinalSid);

                // 2. RAG 检索（F2 + F4）
                List<SessionKbBinding> bindings = sessionKbBindingRepository.findBySessionId(fFinalSid);
                if (!bindings.isEmpty()) {
                    try {
                        float[] queryVector = llmService.getEmbedding(message, fUserId);
                        String vectorStr = llmService.vectorToString(queryVector);
                        String kbIdsArray = "{" + bindings.stream()
                                .map(b -> b.getKbId().toString())
                                .collect(Collectors.joining(",")) + "}";
                        List<Object[]> rawResults = documentChunkRepository.findSimilarChunksInKbs(kbIdsArray, vectorStr, 5);
                        if (!rawResults.isEmpty()) {
                            StringBuilder ctx = new StringBuilder("\n\n## 参考资料\n");
                            List<Long> kbIds = bindings.stream().map(SessionKbBinding::getKbId).toList();
                            List<KnowledgeBase> kbs = knowledgeBaseRepository.findAllById(kbIds);
                            for (int i = 0; i < Math.min(rawResults.size(), 5); i++) {
                                Object[] row = rawResults.get(i);
                                String content = row[0] instanceof DocumentChunk c ? c.getContent() : String.valueOf(row[0]);
                                Long kbId = row.length > 2 && row[2] instanceof Number n ? n.longValue() : null;
                                String kbName = kbId != null
                                        ? kbs.stream().filter(k -> k.getId().equals(kbId))
                                                .findFirst().map(KnowledgeBase::getName).orElse("未知")
                                        : "未知";
                                ctx.append("\n> [").append(kbName).append("] ")
                                        .append(content.length() > 300 ? content.substring(0, 300) + "…" : content);
                            }
                            systemPrompt += ctx.toString();
                        }
                    } catch (Exception ignored) {}
                }

                messages.add(Map.of("role", "system", "content", systemPrompt));

                // 3. 注入对话历史（最近10条，排除当前用户消息）
                List<ChatMessage> histList = chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(finalSid);
                java.util.Collections.reverse(histList);
                for (ChatMessage hm : histList) {
                    if (!hm.getId().equals(userMsg.getId())) {
                        messages.add(Map.of("role", hm.getRole(), "content", hm.getContent()));
                    }
                }

                messages.add(Map.of("role", "user", "content", message));

                // 4. 发起 streaming
                Flux<String> flux = llmService.chatStream(messages, fUserId, thinking);
                StringBuilder fullResponse = new StringBuilder();

                flux.doOnNext(token -> fullResponse.append(token))
                    .doOnComplete(() -> {
                        // 保存 AI 回复
                        ChatMessage aiMsg = new ChatMessage();
                        aiMsg.setSessionId(fFinalSid);
                        aiMsg.setRole("assistant");
                        aiMsg.setContent(fullResponse.toString());
                        chatMessageRepository.save(aiMsg);

                        // 首次对话生成标题
                        ChatSession s = chatSessionRepository.findById(fFinalSid).orElse(null);
                        if (s != null && "新对话".equals(s.getTitle())) {
                            try {
                                String userText = message != null ? message : "";
                                String aiText = fullResponse.toString();
                                String title = generateStreamTitle(fUserId, userText, aiText);
                                if (title != null && !title.isBlank()) {
                                    s.setTitle(title);
                                    chatSessionRepository.save(s);
                                }
                            } catch (Exception ignored) {}
                        }
                    })
                    .doOnError(err -> {
                        try { emitter.send(SseEmitter.event().name("error").data(err.getMessage())); } catch (Exception ex) {}
                        emitter.completeWithError(err);
                    })
                    .subscribe(
                        token -> {
                            try { emitter.send(SseEmitter.event().name("token").data(token)); } catch (Exception e) {}
                        },
                        error -> {},
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data(Map.of()));
                                emitter.complete();
                            } catch (Exception e) {}
                        }
                    );
            } catch (Exception e) {
                try { emitter.send(SseEmitter.event().name("error").data(e.getMessage())); emitter.completeWithError(e); } catch (Exception ex) {}
            }
        });

        return emitter;
    }

    private String generateStreamTitle(Long userId, String userMessage, String aiResponse) {
        try {
            String aiPart = aiResponse == null ? "" : aiResponse;
            if (aiPart.length() > 200) aiPart = aiPart.substring(0, 200);
            List<Map<String, String>> titlePrompt = new ArrayList<>();
            titlePrompt.add(Map.of(
                    "role", "system",
                    "content", "你是一个标题生成器。根据用户的问题和 AI 回答，生成一个不超过 10 个汉字的简短主题标题。只返回标题本身，不要加引号、标点、前后缀、说明。"));
            titlePrompt.add(Map.of(
                    "role", "user",
                    "content", "用户问题：" + (userMessage != null ? userMessage : "") + "\n\nAI回答：" + aiPart));
            String title = llmService.chat(titlePrompt, userId);
            if (title == null) return null;
            return title.trim().replaceAll("[\"'「」『』《》\\[\\]（）()【】]", "")
                    .split("\\n")[0].trim();
        } catch (Exception e) {
            return null;
        }
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
