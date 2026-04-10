package com.example.kaoyan.service;

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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final LlmService llmService;

    public ChatSession createSession(Long userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title != null ? title : "新对话");
        return chatSessionRepository.save(session);
    }

    public List<ChatSession> getUserSessions(Long userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public List<ChatMessage> getSessionMessages(Long sessionId) {
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public ChatMessage sendMessage(Long userId, Long sessionId, String userMessage) {
        // #region agent log
        AgentDebugLog.ndjson("H1b", "ChatService.sendMessage:entry", "start",
                "{\"userId\":" + userId + ",\"sessionId\":" + sessionId + "}");
        // #endregion
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        chatMessageRepository.save(userMsg);

        float[] queryVector = llmService.getEmbedding(userMessage, userId);
        // #region agent log
        AgentDebugLog.ndjson("H2", "ChatService.sendMessage:afterEmbedding", "embedding ok",
                "{\"dim\":" + queryVector.length + "}");
        // #endregion
        String vectorStr = llmService.vectorToString(queryVector);

        List<DocumentChunk> relevantChunks = documentChunkRepository.findSimilarChunks(userId, vectorStr, 5);
        // #region agent log
        AgentDebugLog.ndjson("H3", "ChatService.sendMessage:afterRag", "similar chunks",
                "{\"count\":" + relevantChunks.size() + "}");
        // #endregion

        String context = relevantChunks.stream()
                .map(DocumentChunk::getContent)
                .collect(Collectors.joining("\n\n---\n\n"));

        List<ChatMessage> history = chatMessageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);
        Collections.reverse(history);

        List<Map<String, String>> messages = new ArrayList<>();

        String systemPrompt = llmService.resolveSystemPrompt(userId);
        if (!context.isEmpty()) {
            systemPrompt += "\n\n参考资料：\n" + context;
        }
        messages.add(Map.of("role", "system", "content", systemPrompt));

        for (ChatMessage msg : history) {
            if (!msg.getId().equals(userMsg.getId())) {
                messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        // #region agent log
        AgentDebugLog.ndjson("H4", "ChatService.sendMessage:beforeLlm", "calling chat",
                "{\"messageCount\":" + messages.size() + "}");
        // #endregion
        String aiResponse = llmService.chat(messages, userId);
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
            session.setTitle(userMessage.length() > 50 ? userMessage.substring(0, 50) + "..." : userMessage);
            chatSessionRepository.save(session);
        }

        return assistantMsg;
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        chatSessionRepository.deleteById(sessionId);
    }
}