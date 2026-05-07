package com.example.kaoyan.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Agent 执行上下文 */
public class AgentContext {

    private Long userId;
    private String sessionId; // 可为 null（新会话）
    private List<Map<String, String>> history = new ArrayList<>(); // 最近对话历史
    private Map<String, Object> extra; // Supervisor 传递的额外参数

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
}
