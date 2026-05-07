package com.example.kaoyan.agent;

import com.example.kaoyan.service.LlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Supervisor Agent — 意图分类 + 路由。
 * 使用轻量 LLM 调用，只输出 JSON，不调用工具。
 */
@Component
@RequiredArgsConstructor
public class SupervisorAgent {

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    /**
     * 分析用户意图，返回应该调用的 Agent 列表
     * @return { agents: ["TUTOR","PLAN",...], reasoning: "...", context: {...} }
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> classify(String userMessage, List<Map<String, String>> history, Long userId) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", AgentType.SUPERVISOR.getSystemPrompt()));

        // 注入对话历史（最近 4 条）
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 4);
            for (int i = start; i < history.size(); i++) {
                messages.add(history.get(i));
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        String raw = llmService.chat(messages, userId);
        return parseJson(raw);
    }

    /** 解析 LLM 返回的 JSON，处理 markdown 代码块和异常格式 */
    private Map<String, Object> parseJson(String raw) {
        if (raw == null || raw.isBlank()) return defaultResult();
        try {
            // 去掉 markdown ```json ... ``` 包裹
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```(json)?\\s*", "").replaceAll("```\\s*$", "").trim();
            }
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            // 解析失败 → 默认全部 agent
            return defaultResult();
        }
    }

    private Map<String, Object> defaultResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agents", List.of("TUTOR"));
        result.put("reasoning", "Fallback: JSON 解析失败，默认答疑");
        result.put("context", Map.of());
        return result;
    }
}
