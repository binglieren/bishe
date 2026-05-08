package com.example.kaoyan.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Agent 执行上下文 */
public class AgentContext {

    private Long userId;
    private String sessionId;
    private List<Map<String, String>> history = new ArrayList<>();
    private Map<String, Object> extra;

    /** 会话绑定的知识库 ID 列表 */
    private List<Long> knowledgeBaseIds;

    /** 知识库名称列表（与 knowledgeBaseIds 一一对应） */
    private List<String> knowledgeBaseNames;

    /** 会话级自定义系统 Prompt（会注入到 Agent system prompt 之前） */
    private String systemPrompt;

    public AgentContext() {}

    public AgentContext(Long userId, String sessionId) {
        this.userId = userId;
        this.sessionId = sessionId;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public List<Map<String, String>> getHistory() { return history; }
    public void setHistory(List<Map<String, String>> history) { this.history = history; }

    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }

    public List<Long> getKnowledgeBaseIds() { return knowledgeBaseIds; }
    public void setKnowledgeBaseIds(List<Long> knowledgeBaseIds) { this.knowledgeBaseIds = knowledgeBaseIds; }

    public List<String> getKnowledgeBaseNames() { return knowledgeBaseNames; }
    public void setKnowledgeBaseNames(List<String> knowledgeBaseNames) { this.knowledgeBaseNames = knowledgeBaseNames; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
}
