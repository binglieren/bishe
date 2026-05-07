package com.example.kaoyan.service;

import com.example.kaoyan.agent.AgentContext;
import com.example.kaoyan.agent.AgentOrchestrator;
import com.example.kaoyan.dto.ChatSessionDTO;
import com.example.kaoyan.entity.ChatMessage;
import com.example.kaoyan.entity.ChatSession;
import com.example.kaoyan.entity.DocumentChunk;
import com.example.kaoyan.repository.ChatMessageRepository;
import com.example.kaoyan.repository.ChatSessionRepository;
import com.example.kaoyan.repository.DocumentChunkRepository;
import com.example.kaoyan.util.AgentDebugLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final AgentOrchestrator agentOrchestrator;
    private final LlmService llmService;
    private final QuestionExtractionService questionExtractionService;

    public ChatSession createSession(Long userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title != null ? title : "新对话");
        return chatSessionRepository.save(session);
    }

    public List<ChatSession> getUserSessions(Long userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /**
     * 获取会话列表 + 最新消息预览（用于一级页面展示）
     */
    public List<ChatSessionDTO> getUserSessionsWithPreview(Long userId) {
        List<ChatSession> sessions = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        List<ChatSessionDTO> dtos = new ArrayList<>(sessions.size());
        for (ChatSession s : sessions) {
            ChatSessionDTO dto = new ChatSessionDTO();
            dto.setId(s.getId());
            dto.setTitle(s.getTitle());
            dto.setKnowledgeBaseId(s.getKnowledgeBaseId());
            dto.setThinkingEnabled(s.getThinkingEnabled());
            dto.setCreatedAt(s.getCreatedAt());
            dto.setUpdatedAt(s.getUpdatedAt());

            Integer count = chatMessageRepository.countBySessionId(s.getId());
            dto.setMessageCount(count == null ? 0 : count);

            chatMessageRepository.findFirstBySessionIdOrderByCreatedAtDesc(s.getId())
                    .ifPresent(last -> {
                        String content = last.getContent() == null ? "" : last.getContent();
                        if (last.getImageBase64() != null && !last.getImageBase64().isBlank()) {
                            content = "[图片] " + content;
                        }
                        if (content.length() > 60) content = content.substring(0, 60) + "…";
                        dto.setLastMessagePreview(content);
                        dto.setLastMessageRole(last.getRole());
                    });
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * 调用大模型生成一个不超过 10 个汉字的短标题
     * 失败时回退到用户消息前 16 字。
     */
    private String generateShortTitle(Long userId, String userMessage, String aiResponse, boolean hasImage) {
        try {
            String userPart = userMessage == null ? "" : userMessage;
            String aiPart = aiResponse == null ? "" : aiResponse;
            if (aiPart.length() > 200) aiPart = aiPart.substring(0, 200);

            List<Map<String, String>> titlePrompt = new ArrayList<>();
            titlePrompt.add(Map.of(
                    "role", "system",
                    "content", "你是一个标题生成器。根据用户的问题和 AI 回答，生成一个不超过 10 个汉字的简短主题标题。只返回标题本身，不要加引号、标点、前后缀、说明。"));
            titlePrompt.add(Map.of(
                    "role", "user",
                    "content", "用户问题：" + userPart + "\n\nAI回答：" + aiPart));

            String title = llmService.chat(titlePrompt, userId);
            if (title == null) return fallbackTitle(userMessage, hasImage);
            title = title.trim()
                    .replaceAll("[\"'「」『』《》\\[\\]（）()【】]", "")
                    .replaceAll("^[:：\\-\\s]+", "")
                    .split("\\n")[0]
                    .trim();
            if (title.length() > 20) title = title.substring(0, 20);
            if (title.isEmpty()) return fallbackTitle(userMessage, hasImage);
            return title;
        } catch (Exception e) {
            return fallbackTitle(userMessage, hasImage);
        }
    }

    private String fallbackTitle(String userMessage, boolean hasImage) {
        if (hasImage) return "📷 拍照搜题";
        if (userMessage == null || userMessage.isBlank()) return "新对话";
        String t = userMessage.trim();
        return t.length() > 16 ? t.substring(0, 16) + "…" : t;
    }

    public List<ChatMessage> getSessionMessages(Long sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public ChatMessage sendMessage(Long userId, Long sessionId, String userMessage, String imageBase64) {
        // #region agent log
        AgentDebugLog.ndjson("H1b", "ChatService.sendMessage:entry", "start",
                "{\"userId\":" + userId + ",\"sessionId\":" + sessionId + "}");
        // #endregion
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setImageBase64(imageBase64);
        chatMessageRepository.save(userMsg);

        boolean hasImage = imageBase64 != null && !imageBase64.isBlank();

        // 只有会话绑定了知识库时才做 RAG 检索
        ChatSession sessionForRag = chatSessionRepository.findById(sessionId).orElse(null);
        Long kbId = sessionForRag != null ? sessionForRag.getKnowledgeBaseId() : null;

        String context = "";
        if (kbId != null) {
            try {
                float[] queryVector = llmService.getEmbedding(userMessage, userId);
                // #region agent log
                AgentDebugLog.ndjson("H2", "ChatService.sendMessage:afterEmbedding", "embedding ok",
                        "{\"dim\":" + queryVector.length + ",\"kbId\":" + kbId + "}");
                // #endregion
                String vectorStr = llmService.vectorToString(queryVector);

                List<DocumentChunk> relevantChunks = documentChunkRepository.findSimilarChunksInKb(userId, kbId, vectorStr, 5);
                // #region agent log
                AgentDebugLog.ndjson("H3", "ChatService.sendMessage:afterRag", "similar chunks",
                        "{\"count\":" + relevantChunks.size() + ",\"kbId\":" + kbId + "}");
                // #endregion
                context = relevantChunks.stream()
                        .map(DocumentChunk::getContent)
                        .collect(Collectors.joining("\n\n---\n\n"));
            } catch (Exception e) {
                // #region agent log
                AgentDebugLog.ndjson("H2skip", "ChatService.sendMessage:embeddingSkipped", "embedding unavailable, skipping RAG", "{}");
                // #endregion
            }
        } else {
            // #region agent log
            AgentDebugLog.ndjson("H2none", "ChatService.sendMessage:noKb", "session has no KB, skip RAG", "{}");
            // #endregion
        }

        List<ChatMessage> history = chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        Collections.reverse(history);

        String systemPrompt = llmService.resolveSystemPrompt(userId);
        if (!context.isEmpty()) {
            systemPrompt += "\n\n参考资料：\n" + context;
        }

        String aiResponse;
        if (hasImage) {
            // 多模态路径：构建含图片的消息
            List<Map<String, Object>> multimodalMessages = new ArrayList<>();
            multimodalMessages.add(Map.of("role", "system", "content", systemPrompt));

            for (ChatMessage msg : history) {
                if (!msg.getId().equals(userMsg.getId())) {
                    multimodalMessages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
                }
            }

            // 当前用户消息：文本 + 图片
            List<Map<String, Object>> contentParts = new ArrayList<>();
            contentParts.add(Map.of("type", "text", "text", userMessage));
            contentParts.add(Map.of("type", "image_url", "image_url",
                    Map.of("url", "data:image/jpeg;base64," + imageBase64)));
            multimodalMessages.add(Map.of("role", "user", "content", contentParts));

            // #region agent log
            AgentDebugLog.ndjson("H4", "ChatService.sendMessage:beforeLlm", "calling multimodal chat",
                    "{\"messageCount\":" + multimodalMessages.size() + ",\"hasImage\":true}");
            // #endregion
            aiResponse = llmService.chatMultimodal(multimodalMessages, userId);
        } else {
            // Agent 路径：通过 Supervisor + MCP 工具协同回答
            AgentContext agentCtx = new AgentContext(userId, String.valueOf(sessionId));
            List<Map<String, String>> agentHistory = new ArrayList<>();
            for (ChatMessage msg : history) {
                if (!msg.getId().equals(userMsg.getId())) {
                    agentHistory.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
                }
            }
            agentCtx.setHistory(agentHistory);

            // #region agent log
            AgentDebugLog.ndjson("H4", "ChatService.sendMessage:beforeAgent", "calling agent orchestrator",
                    "{\"messageCount\":" + agentHistory.size() + "}");
            // #endregion
            aiResponse = agentOrchestrator.execute(userMessage, agentCtx);
        }
        // #region agent log
        AgentDebugLog.ndjson("H4ok", "ChatService.sendMessage:afterLlm", "chat ok",
                "{\"replyLen\":" + (aiResponse != null ? aiResponse.length() : 0) + "}");
        // #endregion

        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(aiResponse);
        chatMessageRepository.save(assistantMsg);

        ChatSession session = chatSessionRepository.findById(sessionId).orElse(null);
        if (session != null && "新对话".equals(session.getTitle())) {
            // 首次对话：调用 LLM 生成简短主题标题（失败回退到用户消息前 16 字）
            String title = generateShortTitle(userId, userMessage, aiResponse, hasImage);
            session.setTitle(title);
            chatSessionRepository.save(session);
        }

        // 拍照搜题：异步提取题目结构并保存到用户题库（后台执行，不阻塞响应）
        if (hasImage) {
            final Long finalUserId = userId;
            final Long finalSessionId = sessionId;
            final String finalImageBase64 = imageBase64;
            final String finalAiResponse = aiResponse;
            CompletableFuture.runAsync(() ->
                    questionExtractionService.extractAndSave(finalUserId, finalSessionId, finalImageBase64, finalAiResponse));
        }

        return assistantMsg;
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        chatSessionRepository.deleteById(sessionId);
    }

    /**
     * 更新消息的预渲染 HTML（前端转译完 LaTeX 后回传，跨设备永久缓存）。
     *
     * <p>仅允许该消息所属会话的拥有者更新；非自己的消息抛 IllegalArgumentException
     * 由 GlobalExceptionHandler 统一返回 400/403。
     */
    @Transactional
    public void updateMessageRender(Long userId, Long messageId,
                                     String contentHtml, String renderMeta) {
        ChatMessage msg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("消息不存在"));
        ChatSession session = chatSessionRepository.findById(msg.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权更新此消息");
        }
        msg.setContentHtml(contentHtml);
        msg.setRenderMeta(renderMeta);
        chatMessageRepository.save(msg);
    }

    /**
     * 将会话绑定到指定知识库（kbId 传 null 可取消绑定）
     */
    @Transactional
    public ChatSession bindKnowledgeBase(Long userId, Long sessionId, Long kbId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作此会话");
        }
        session.setKnowledgeBaseId(kbId);
        return chatSessionRepository.save(session);
    }

    @Transactional
    public ChatMessage saveMessage(ChatMessage msg) {
        return chatMessageRepository.save(msg);
    }

    @Transactional
    public ChatSession toggleThinking(Long userId, Long sessionId, boolean enabled) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getUserId().equals(userId)) throw new IllegalArgumentException("无权操作");
        session.setThinkingEnabled(enabled);
        return chatSessionRepository.save(session);
    }

    public ChatSession getSession(Long sessionId) {
        return chatSessionRepository.findById(sessionId).orElse(null);
    }
}