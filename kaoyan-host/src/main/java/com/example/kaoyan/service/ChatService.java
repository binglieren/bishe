package com.example.kaoyan.service;

import com.example.kaoyan.agent.AgentContext;
import com.example.kaoyan.agent.AgentOrchestrator;
import com.example.kaoyan.dto.ChatSessionDTO;
import com.example.kaoyan.entity.*;
import com.example.kaoyan.repository.*;
import com.example.kaoyan.util.AgentDebugLog;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final SessionKbBindingRepository sessionKbBindingRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final AgentOrchestrator agentOrchestrator;
    private final LlmService llmService;
    private final QuestionExtractionService questionExtractionService;

    // === 会话生命周期 ===

    public ChatSession createSession(Long userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title != null ? title : "新对话");
        return chatSessionRepository.save(session);
    }

    public List<ChatSession> getUserSessions(Long userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public List<ChatSessionDTO> getUserSessionsWithPreview(Long userId) {
        List<ChatSession> sessions = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        List<ChatSessionDTO> dtos = new ArrayList<>(sessions.size());
        for (ChatSession s : sessions) {
            ChatSessionDTO dto = new ChatSessionDTO();
            dto.setId(s.getId());
            dto.setTitle(s.getTitle());
            dto.setKnowledgeBaseId(s.getKnowledgeBaseId());
            dto.setThinkingEnabled(s.getThinkingEnabled());
            dto.setSystemPrompt(s.getSystemPrompt());
            dto.setCreatedAt(s.getCreatedAt());
            dto.setUpdatedAt(s.getUpdatedAt());

            List<SessionKbBinding> bindings = sessionKbBindingRepository.findBySessionId(s.getId());
            dto.setKnowledgeBaseIds(bindings.stream().map(SessionKbBinding::getKbId).toList());

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

    public List<ChatMessage> getSessionMessages(Long sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        sessionKbBindingRepository.deleteBySessionId(sessionId);
        chatSessionRepository.deleteById(sessionId);
    }

    public ChatSession getSession(Long sessionId) {
        return chatSessionRepository.findById(sessionId).orElse(null);
    }

    @Transactional
    public void updateSessionTitle(Long sessionId, String title) {
        chatSessionRepository.findById(sessionId).ifPresent(s -> {
            s.setTitle(title);
            chatSessionRepository.save(s);
        });
    }

    @Transactional
    public ChatMessage saveMessage(ChatMessage msg) {
        return chatMessageRepository.save(msg);
    }

    // === 标题生成 ===

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

    // === 系统 Prompt 解析（带会话级覆盖） ===

    public String resolveSystemPrompt(Long userId, Long sessionId) {
        if (sessionId != null) {
            ChatSession session = chatSessionRepository.findById(sessionId).orElse(null);
            if (session != null && session.getSystemPrompt() != null && !session.getSystemPrompt().isBlank()) {
                return session.getSystemPrompt();
            }
        }
        return llmService.resolveSystemPrompt(userId);
    }

    // === 知识库绑定管理 ===

    @Transactional
    public ChatSession bindKnowledgeBase(Long userId, Long sessionId, Long kbId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getUserId().equals(userId)) throw new IllegalArgumentException("无权操作此会话");
        session.setKnowledgeBaseId(kbId); // 兼容旧字段
        // 同时写入多KB绑定表，确保RAG检索能找到
        sessionKbBindingRepository.deleteBySessionId(sessionId);
        if (kbId != null) {
            SessionKbBinding binding = new SessionKbBinding();
            binding.setSessionId(sessionId);
            binding.setKbId(kbId);
            binding.setWeight(1.0);
            sessionKbBindingRepository.save(binding);
        }
        return chatSessionRepository.save(session);
    }

    public List<Long> getSessionKnowledgeBaseIds(Long sessionId) {
        return sessionKbBindingRepository.findBySessionId(sessionId).stream()
                .map(SessionKbBinding::getKbId).toList();
    }

    @Transactional
    public void setSessionKnowledgeBases(Long userId, Long sessionId, List<Long> kbIds) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getUserId().equals(userId)) throw new IllegalArgumentException("无权操作此会话");

        sessionKbBindingRepository.deleteBySessionId(sessionId);
        if (kbIds != null) {
            for (Long kbId : kbIds) {
                SessionKbBinding binding = new SessionKbBinding();
                binding.setSessionId(sessionId);
                binding.setKbId(kbId);
                binding.setWeight(1.0);
                sessionKbBindingRepository.save(binding);
            }
        }
        if (kbIds != null && !kbIds.isEmpty()) {
            session.setKnowledgeBaseId(kbIds.get(0));
        } else {
            session.setKnowledgeBaseId(null);
        }
        chatSessionRepository.save(session);
    }

    @Transactional
    public void addSessionKnowledgeBase(Long userId, Long sessionId, Long kbId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getUserId().equals(userId)) throw new IllegalArgumentException("无权操作此会话");

        List<SessionKbBinding> existing = sessionKbBindingRepository.findBySessionId(sessionId);
        if (existing.size() >= 5) throw new IllegalArgumentException("最多绑定5个知识库");
        if (existing.stream().anyMatch(b -> b.getKbId().equals(kbId))) return;

        SessionKbBinding binding = new SessionKbBinding();
        binding.setSessionId(sessionId);
        binding.setKbId(kbId);
        binding.setWeight(1.0);
        sessionKbBindingRepository.save(binding);

        if (existing.isEmpty()) {
            session.setKnowledgeBaseId(kbId);
            chatSessionRepository.save(session);
        }
    }

    @Transactional
    public void removeSessionKnowledgeBase(Long userId, Long sessionId, Long kbId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getUserId().equals(userId)) throw new IllegalArgumentException("无权操作此会话");

        sessionKbBindingRepository.deleteBySessionIdAndKbId(sessionId, kbId);

        List<SessionKbBinding> remaining = sessionKbBindingRepository.findBySessionId(sessionId);
        session.setKnowledgeBaseId(remaining.isEmpty() ? null : remaining.get(0).getKbId());
        chatSessionRepository.save(session);
    }

    /** 新会话自动继承用户最近的知识库绑定 */
    @Transactional
    public void copyLastKbBinding(Long userId, Long newSessionId) {
        List<ChatSession> sessions = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        for (ChatSession s : sessions) {
            if (s.getId().equals(newSessionId)) continue;
            List<SessionKbBinding> bindings = sessionKbBindingRepository.findBySessionId(s.getId());
            if (!bindings.isEmpty()) {
                for (SessionKbBinding b : bindings) {
                    SessionKbBinding copy = new SessionKbBinding();
                    copy.setSessionId(newSessionId);
                    copy.setKbId(b.getKbId());
                    copy.setWeight(b.getWeight());
                    sessionKbBindingRepository.save(copy);
                }
                return;
            }
        }
    }

    // === 会话 Prompt 设置 ===

    @Transactional
    public ChatSession setSessionSystemPrompt(Long userId, Long sessionId, String systemPrompt) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getUserId().equals(userId)) throw new IllegalArgumentException("无权操作此会话");
        session.setSystemPrompt(systemPrompt);
        return chatSessionRepository.save(session);
    }

    // === Thinking 切换 ===

    @Transactional
    public ChatSession toggleThinking(Long userId, Long sessionId, boolean enabled) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getUserId().equals(userId)) throw new IllegalArgumentException("无权操作");
        session.setThinkingEnabled(enabled);
        return chatSessionRepository.save(session);
    }

    // === 消息渲染缓存 ===

    @Transactional
    public void updateMessageRender(Long userId, Long messageId,
                                    String contentHtml, String renderMeta) {
        ChatMessage msg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("消息不存在"));
        ChatSession session = chatSessionRepository.findById(msg.getSessionId())
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!session.getUserId().equals(userId)) throw new IllegalArgumentException("无权更新此消息");
        msg.setContentHtml(contentHtml);
        msg.setRenderMeta(renderMeta);
        chatMessageRepository.save(msg);
    }

    // ================================================================
    // 核心：发送消息（多知识库 RAG + 结构化上下文 + 会话级 Prompt）
    // ================================================================

    @Transactional
    public ChatMessage sendMessage(Long userId, Long sessionId, String userMessage, String imageBase64) {
        AgentDebugLog.ndjson("H1b", "ChatService.sendMessage:entry", "start",
                "{\"userId\":" + userId + ",\"sessionId\":" + sessionId + "}");

        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setImageBase64(imageBase64);
        chatMessageRepository.save(userMsg);

        boolean hasImage = imageBase64 != null && !imageBase64.isBlank();

        List<ChatMessage> history = chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        Collections.reverse(history);

        // === 1. 构建查询文本（F3：用最近3轮对话拼接） ===
        String queryText = buildQueryText(userMessage, history, userMsg.getId());

        // === 2. 多知识库 RAG 检索 + 结构化上下文（F2 + F4） ===
        String ragContext = buildRagContext(queryText, sessionId, userId, userMessage);

        // === 3. 解析系统 Prompt（F1：会话级覆盖） ===
        String systemPrompt = resolveSystemPrompt(userId, sessionId);
        if (!ragContext.isEmpty()) {
            systemPrompt += "\n\n" + ragContext;
        }

        // === 4. 计算 token 预算（F6） ===
        int estimatedTokens = estimateTokens(systemPrompt, history, userMessage);
        boolean needsCompression = estimatedTokens > 12000;
        if (needsCompression) {
            history = compressHistory(history, userId);
        }

        String aiResponse;
        if (hasImage) {
            List<Map<String, Object>> multimodalMessages = new ArrayList<>();
            multimodalMessages.add(Map.of("role", "system", "content", systemPrompt));

            for (ChatMessage msg : history) {
                if (!msg.getId().equals(userMsg.getId())) {
                    Map<String, Object> msgMap = new LinkedHashMap<>();
                    msgMap.put("role", msg.getRole());
                    msgMap.put("content", msg.getContent());
                    if (msg.getReasoningContent() != null && !msg.getReasoningContent().isBlank()) {
                        msgMap.put("reasoning_content", msg.getReasoningContent());
                    }
                    multimodalMessages.add(msgMap);
                }
            }

            List<Map<String, Object>> contentParts = new ArrayList<>();
            contentParts.add(Map.of("type", "text", "text", userMessage));
            contentParts.add(Map.of("type", "image_url", "image_url",
                    Map.of("url", "data:image/jpeg;base64," + imageBase64)));
            multimodalMessages.add(Map.of("role", "user", "content", contentParts));

            AgentDebugLog.ndjson("H4", "ChatService.sendMessage:beforeLlm", "calling multimodal chat",
                    "{\"messageCount\":" + multimodalMessages.size() + ",\"hasImage\":true}");
            aiResponse = llmService.chatMultimodal(multimodalMessages, userId);
        } else {
            // Agent 路径（F8：注入知识库上下文到 Agent）
            AgentContext agentCtx = new AgentContext(userId, String.valueOf(sessionId));
            List<Map<String, String>> agentHistory = new ArrayList<>();
            for (ChatMessage msg : history) {
                if (!msg.getId().equals(userMsg.getId())) {
                    Map<String, String> msgMap = new LinkedHashMap<>();
                    msgMap.put("role", msg.getRole());
                    msgMap.put("content", msg.getContent() != null ? msg.getContent() : "");
                    if (msg.getReasoningContent() != null && !msg.getReasoningContent().isBlank()) {
                        msgMap.put("reasoning_content", msg.getReasoningContent());
                    }
                    agentHistory.add(msgMap);
                }
            }
            agentCtx.setHistory(agentHistory);

            // F8: 注入绑定的知识库信息
            List<SessionKbBinding> bindings = sessionKbBindingRepository.findBySessionId(sessionId);
            if (!bindings.isEmpty()) {
                List<Long> kbIds = bindings.stream().map(SessionKbBinding::getKbId).toList();
                agentCtx.setKnowledgeBaseIds(kbIds);
                List<String> kbNames = knowledgeBaseRepository.findAllById(kbIds).stream()
                        .map(KnowledgeBase::getName).toList();
                agentCtx.setKnowledgeBaseNames(kbNames);
            }

            // 注入 systemPrompt 到 Agent 上下文
            agentCtx.setSystemPrompt(systemPrompt);

            AgentDebugLog.ndjson("H4", "ChatService.sendMessage:beforeAgent", "calling agent orchestrator",
                    "{\"messageCount\":" + agentHistory.size() + "}");
            aiResponse = agentOrchestrator.execute(userMessage, agentCtx);
        }
        AgentDebugLog.ndjson("H4ok", "ChatService.sendMessage:afterLlm", "chat ok",
                "{\"replyLen\":" + (aiResponse != null ? aiResponse.length() : 0) + "}");

        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(aiResponse);
        chatMessageRepository.save(assistantMsg);

        ChatSession session = chatSessionRepository.findById(sessionId).orElse(null);
        if (session != null && "新对话".equals(session.getTitle())) {
            String title = generateShortTitle(userId, userMessage, aiResponse, hasImage);
            session.setTitle(title);
            chatSessionRepository.save(session);
        }

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

    // ================================================================
    // RAG 上下文构建
    // ================================================================

    /**
     * F3: 用最近3轮对话 + 当前消息拼接为查询文本，提高检索精准度
     */
    private String buildQueryText(String userMessage, List<ChatMessage> history, Long currentMsgId) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = history.size() - 1; i >= 0 && count < 6; i--) {
            ChatMessage msg = history.get(i);
            if (msg.getId().equals(currentMsgId)) continue;
            if (msg.getContent() != null && !msg.getContent().isBlank()) {
                sb.insert(0, msg.getContent() + " ");
                count++;
            }
        }
        sb.append(userMessage);
        return sb.toString();
    }

    /**
     * F2 + F4: 多知识库联合检索 + 结构化上下文注入 (LangChain4j)
     */
    private String buildRagContext(String queryText, Long sessionId, Long userId, String userMessage) {
        List<SessionKbBinding> bindings = sessionKbBindingRepository.findBySessionId(sessionId);
        if (bindings.isEmpty()) return "";

        try {
            Set<String> kbIdSet = bindings.stream()
                    .map(b -> String.valueOf(b.getKbId()))
                    .collect(Collectors.toSet());

            // LangChain4j 向量检索
            Embedding queryEmbedding = embeddingModel.embed(queryText).content();
            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(30)
                            .build());

            // 按 metadata 过滤
            List<EmbeddingMatch<TextSegment>> filtered = searchResult.matches().stream()
                    .filter(m -> m.embedded() != null && m.embedded().metadata() != null)
                    .filter(m -> kbIdSet.contains(m.embedded().metadata().getString("kb_id")))
                    .filter(m -> "true".equals(m.embedded().metadata().getString("enabled")))
                    .toList();

            if (filtered.isEmpty()) return "";

            // 邻chunk扩展：为每个匹配块带入前后各1个相邻块
            Set<String> expandedIds = new HashSet<>();
            Map<String, Map<Integer, EmbeddingMatch<TextSegment>>> docIndex = new LinkedHashMap<>();
            for (EmbeddingMatch<TextSegment> m : filtered) {
                String docId = m.embedded().metadata().getString("document_id");
                int idx = Integer.parseInt(m.embedded().metadata().getString("chunk_index"));
                docIndex.computeIfAbsent(docId, k -> new LinkedHashMap<>()).put(idx, m);
            }
            for (EmbeddingMatch<TextSegment> best : filtered.subList(0, Math.min(6, filtered.size()))) {
                String docId = best.embedded().metadata().getString("document_id");
                int idx = Integer.parseInt(best.embedded().metadata().getString("chunk_index"));
                Map<Integer, EmbeddingMatch<TextSegment>> neighbors = docIndex.get(docId);
                if (neighbors != null) {
                    for (int offset = -1; offset <= 1; offset++) {
                        if (neighbors.containsKey(idx + offset)) {
                            expandedIds.add(docId + ":" + (idx + offset));
                        }
                    }
                }
            }

            // 按知识库分组，只展示扩展后的chunk
            Map<String, List<EmbeddingMatch<TextSegment>>> grouped = new LinkedHashMap<>();
            for (EmbeddingMatch<TextSegment> match : filtered) {
                String kbId = match.embedded().metadata().getString("kb_id");
                String docId = match.embedded().metadata().getString("document_id");
                int idx = Integer.parseInt(match.embedded().metadata().getString("chunk_index"));
                if (!expandedIds.contains(docId + ":" + idx)) continue;
                grouped.computeIfAbsent(kbId, k -> new ArrayList<>()).add(match);
            }

            // 为每个KB按chunk_index排序
            for (List<EmbeddingMatch<TextSegment>> list : grouped.values()) {
                list.sort(Comparator.comparingInt(m -> Integer.parseInt(m.embedded().metadata().getString("chunk_index"))));
            }

            StringBuilder ctx = new StringBuilder("## 参考资料（来自已绑定的知识库）\n\n");

            // 知识库元信息 + 本次检索统计
            List<Long> kbIds = bindings.stream().map(SessionKbBinding::getKbId).toList();
            List<KnowledgeBase> kbs = knowledgeBaseRepository.findAllById(kbIds);
            ctx.append("### 已绑定的知识库\n");
            for (KnowledgeBase kb : kbs) {
                long docCount = documentRepository.countByKnowledgeBaseIdAndEnabledTrue(kb.getId());
                ctx.append("- **").append(kb.getName()).append("**");
                if (kb.getDescription() != null && !kb.getDescription().isBlank()) {
                    ctx.append("：").append(kb.getDescription());
                }
                ctx.append("（").append(docCount).append(" 份文档）\n");
            }
            ctx.append("\n本次检索匹配到 ").append(filtered.size()).append(" 个片段\n\n");

            ctx.append("### 相关文档片段（含上下文）\n\n");
            int totalShown = 0;
            for (Map.Entry<String, List<EmbeddingMatch<TextSegment>>> entry : grouped.entrySet()) {
                String kbIdStr = entry.getKey();
                Long kbId = Long.valueOf(kbIdStr);
                String kbName = kbs.stream()
                        .filter(k -> k.getId().equals(kbId))
                        .findFirst().map(KnowledgeBase::getName).orElse("未知知识库");

                for (EmbeddingMatch<TextSegment> match : entry.getValue()) {
                    if (totalShown >= 10) break;
                    TextSegment seg = match.embedded();
                    String content = seg.text();
                    String docName = seg.metadata().getString("filename");
                    if (docName == null) docName = "未知文档";
                    double score = match.score();
                    int chunkIdx = Integer.parseInt(seg.metadata().getString("chunk_index"));

                    ctx.append("> **[知识库「").append(kbName).append("」]《").append(docName).append("》#").append(chunkIdx + 1);
                    ctx.append(" 相关度: ").append((int) Math.round(score * 100)).append("%");
                    ctx.append("**\n>\n");
                    String trimmed = content.length() > 500 ? content.substring(0, 500) + "…" : content;
                    ctx.append("> ").append(trimmed.replace("\n", "\n> ")).append("\n>\n");
                    totalShown++;
                }
                if (totalShown >= 10) break;
            }

            return ctx.toString();

        } catch (Exception e) {
            log.warn("RAG检索失败: {}", e.getMessage());
            return "";
        }
    }

    // ================================================================
    // Token 估算与上下文压缩（F6）
    // ================================================================

    private int estimateTokens(String systemPrompt, List<ChatMessage> history, String userMessage) {
        int total = 0;
        if (systemPrompt != null) total += systemPrompt.length() / 2;
        for (ChatMessage msg : history) {
            if (msg.getContent() != null) total += msg.getContent().length() / 2;
        }
        if (userMessage != null) total += userMessage.length() / 2;
        return total;
    }

    private List<ChatMessage> compressHistory(List<ChatMessage> history, Long userId) {
        if (history.size() <= 4) return history;

        int compressCount = history.size() - 4;
        StringBuilder toSummarize = new StringBuilder();
        for (int i = 0; i < compressCount; i++) {
            ChatMessage msg = history.get(i);
            toSummarize.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }

        try {
            List<Map<String, String>> summaryPrompt = new ArrayList<>();
            summaryPrompt.add(Map.of("role", "system", "content",
                    "你将一段对话历史压缩成一条简短摘要（不超过200字），仅保留关键信息：讨论的主题、用户问题、AI回答要点。只输出摘要本身。"));
            summaryPrompt.add(Map.of("role", "user", "content", toSummarize.toString()));
            String summary = llmService.chat(summaryPrompt, userId);

            ChatMessage summaryMsg = new ChatMessage();
            summaryMsg.setRole("system");
            summaryMsg.setContent("[对话摘要] " + (summary != null ? summary : "之前的对话内容"));

            List<ChatMessage> compressed = new ArrayList<>();
            compressed.add(summaryMsg);
            compressed.addAll(history.subList(compressCount, history.size()));
            return compressed;
        } catch (Exception e) {
            return history.subList(history.size() - 4, history.size());
        }
    }

    // ================================================================
    // 流式端点：构建完整的消息列表（含 System Prompt + RAG + 历史）
    // 提供给 ChatController 直接用于 chatStream
    // ================================================================

    public List<Map<String, String>> buildStreamMessages(Long userId, Long sessionId, String userMessage, Long userMsgId) {
        List<Map<String, String>> messages = new ArrayList<>();

        // 1. 解析系统 Prompt（F1：会话级覆盖）
        String systemPrompt = resolveSystemPrompt(userId, sessionId);

        // 2. RAG 检索（F2 + F4）LangChain4j — 含邻chunk扩展
        List<SessionKbBinding> bindings = sessionKbBindingRepository.findBySessionId(sessionId);
        if (!bindings.isEmpty()) {
            try {
                Set<String> kbIdSet = bindings.stream()
                        .map(b -> String.valueOf(b.getKbId()))
                        .collect(Collectors.toSet());
                Embedding queryEmbedding = embeddingModel.embed(userMessage).content();
                EmbeddingSearchResult<TextSegment> sr = embeddingStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(queryEmbedding)
                                .maxResults(50)
                                .build());

                List<EmbeddingMatch<TextSegment>> filtered = sr.matches().stream()
                        .filter(m -> m.embedded() != null && m.embedded().metadata() != null)
                        .filter(m -> kbIdSet.contains(m.embedded().metadata().getString("kb_id")))
                        .filter(m -> "true".equals(m.embedded().metadata().getString("enabled")))
                        .toList();

                if (!filtered.isEmpty()) {
                    // 邻chunk扩展
                    Set<String> expandedIds = new HashSet<>();
                    Map<String, Map<Integer, EmbeddingMatch<TextSegment>>> docIndex = new LinkedHashMap<>();
                    for (var m : filtered) {
                        String docId = m.embedded().metadata().getString("document_id");
                        int idx = Integer.parseInt(m.embedded().metadata().getString("chunk_index"));
                        docIndex.computeIfAbsent(docId, k -> new LinkedHashMap<>()).put(idx, m);
                    }
                    for (var best : filtered.subList(0, Math.min(5, filtered.size()))) {
                        String docId = best.embedded().metadata().getString("document_id");
                        int idx = Integer.parseInt(best.embedded().metadata().getString("chunk_index"));
                        var neighbors = docIndex.get(docId);
                        if (neighbors != null) {
                            for (int offset = -1; offset <= 1; offset++) {
                                if (neighbors.containsKey(idx + offset)) expandedIds.add(docId + ":" + (idx + offset));
                            }
                        }
                    }

                    StringBuilder ctx = new StringBuilder("\n\n## 参考资料（匹配 ").append(filtered.size()).append(" 个片段）\n");
                    log.info("[RAG] 检索到 {} 个匹配片段", filtered.size());
                    List<Long> kbIds = bindings.stream().map(SessionKbBinding::getKbId).toList();
                    List<KnowledgeBase> kbs = knowledgeBaseRepository.findAllById(kbIds);
                    List<EmbeddingMatch<TextSegment>> expanded = filtered.stream()
                            .filter(m -> expandedIds.contains(m.embedded().metadata().getString("document_id") + ":"
                                    + m.embedded().metadata().getString("chunk_index")))
                            .sorted(Comparator.comparingInt(m -> Integer.parseInt(m.embedded().metadata().getString("chunk_index"))))
                            .toList();
                    for (var match : expanded) {
                        TextSegment seg = match.embedded();
                        Long kbId = Long.valueOf(seg.metadata().getString("kb_id"));
                        String kbName = kbs.stream().filter(k -> k.getId().equals(kbId))
                                .findFirst().map(KnowledgeBase::getName).orElse("未知");
                        String docName = seg.metadata().getString("filename");
                        int chunkIdx = Integer.parseInt(seg.metadata().getString("chunk_index"));
                        ctx.append("\n> [").append(kbName).append("]《").append(docName).append("》#").append(chunkIdx + 1);
                        ctx.append(" ").append(seg.text().length() > 300 ? seg.text().substring(0, 300) + "…" : seg.text());
                    }
                    systemPrompt += ctx.toString();
                }
            } catch (Exception e) {
                log.warn("[RAG] 检索异常: {}", e.getMessage());
            }
        }

        messages.add(Map.of("role", "system", "content", systemPrompt));

        // 3. 注入对话历史（最近10条），含 reasoning_content
        List<ChatMessage> histList = chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        Collections.reverse(histList);
        for (ChatMessage hm : histList) {
            if (hm.getId().equals(userMsgId)) continue;
            String content = hm.getContent() != null ? hm.getContent() : "";
            Map<String, String> msgMap = new LinkedHashMap<>();
            msgMap.put("role", hm.getRole());
            msgMap.put("content", content);
            if (hm.getReasoningContent() != null && !hm.getReasoningContent().isBlank()) {
                msgMap.put("reasoning_content", hm.getReasoningContent());
            }
            messages.add(msgMap);
        }

        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }

    public String generateStreamTitle(Long userId, String userMessage, String aiResponse) {
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
}
