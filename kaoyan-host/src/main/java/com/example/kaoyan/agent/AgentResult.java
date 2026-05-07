package com.example.kaoyan.agent;

import java.util.ArrayList;
import java.util.List;

/** Agent 执行结果 */
public class AgentResult {

    private String agentName;
    private String content;
    private String label; // 前端显示标签，如 "正在分析薄弱点..."
    private boolean success = true;
    private List<String> toolCalls = new ArrayList<>();

    public AgentResult() {}

    public AgentResult(String agentName, String content, String label) {
        this.agentName = agentName;
        this.content = content;
        this.label = label;
    }

    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public List<String> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<String> toolCalls) { this.toolCalls = toolCalls; }
}
