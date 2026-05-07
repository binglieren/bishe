package com.example.kaoyan.agent;

import com.example.kaoyan.mcp.McpClientManager;
import com.example.kaoyan.service.LlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 编排器 — 多 Agent 协同的核心。
 *
 * 流程：
 *   1. Supervisor 分类用户意图 → 选出应调用的 Agent 列表
 *   2. 按顺序执行每个 Agent：注入 system prompt + tools → LLM loop（tool_call / respond）
 *   3. 拼接所有 Agent 的结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final LlmService llmService;
    private final McpClientManager mcpClientManager;
    private final SupervisorAgent supervisorAgent;
    private final ObjectMapper objectMapper;

    /**
     * 执行完整的 Agent 协调流程
     * @return 拼接后的最终回复文本
     */
    public String execute(String userMessage, AgentContext context) {
        if (userMessage == null || userMessage.isBlank()) return "";

        // Step 1: Supervisor 分类
        Map<String, Object> classification = supervisorAgent.classify(
            userMessage, context.getHistory(), context.getUserId());

        @SuppressWarnings("unchecked")
        List<String> agentNames = (List<String>) classification.getOrDefault("agents", List.of("TUTOR"));
        @SuppressWarnings("unchecked")
        Map<String, Object> extraContext = (Map<String, Object>) classification.getOrDefault("context", Map.of());

        // 如果返回 CLARIFY，让用户说明意图
        if (agentNames.size() == 1 && "CLARIFY".equals(agentNames.get(0))) {
            return "请问您是想：1）咨询学习问题 2）制定复习计划 3）查看学习效果 还是 4）练习题目？请告诉我具体需求，我会更好地为您服务。";
        }

        // Step 2: 按序执行 Agent
        List<AgentResult> results = new ArrayList<>();
        for (String name : agentNames) {
            AgentType agentType = parseAgentType(name);
            if (agentType == null || agentType == AgentType.SUPERVISOR) continue;

            AgentResult result = executeAgent(agentType, userMessage, extraContext, context);
            results.add(result);
        }

        // Step 3: 拼接结果
        return formatResults(results, agentNames);
    }

    /** 执行单个 Agent，含 tool_call loop */
    private AgentResult executeAgent(AgentType agentType, String userMessage,
                                      Map<String, Object> extraContext, AgentContext context) {
        AgentResult result = new AgentResult();
        result.setAgentName(agentType.name());
        result.setLabel(getLabel(agentType));

        List<Map<String, String>> messages = new ArrayList<>();
        // System prompt
        String systemPrompt = buildSystemPrompt(agentType);
        messages.add(Map.of("role", "system", "content", systemPrompt));

        // 注入上下文消息
        if (extraContext != null && !extraContext.isEmpty()) {
            String ctxStr = extraContext.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("; "));
            messages.add(Map.of("role", "system", "content",
                "[上下文参数] " + ctxStr));
        }

        // 注入最近对话历史
        if (context.getHistory() != null && !context.getHistory().isEmpty()) {
            int start = Math.max(0, context.getHistory().size() - 4);
            messages.addAll(context.getHistory().subList(start, context.getHistory().size()));
        }

        messages.add(Map.of("role", "user", "content", userMessage));

        // Tool call loop（最多 5 轮）
        int maxRounds = 5;
        for (int round = 0; round < maxRounds; round++) {
            String response = llmService.chat(messages, context.getUserId());

            // 检查是否是 tool_call
            Map<String, Object> toolCall = extractToolCall(response);
            if (toolCall != null) {
                String toolName = (String) toolCall.get("name");
                @SuppressWarnings("unchecked")
                Map<String, Object> toolArgs = (Map<String, Object>) toolCall.get("arguments");

                result.getToolCalls().add(toolName);

                // 调用 MCP 工具
                Object toolResult = mcpClientManager.callTool(toolName, toolArgs);

                // 将工具结果追加到对话
                messages.add(Map.of("role", "assistant", "content", response));
                messages.add(Map.of("role", "user", "content",
                    "[工具返回 " + toolName + "]: " + toJson(toolResult)));
            } else {
                // 最终文本回复
                result.setContent(response != null ? response : "");
                result.setSuccess(true);
                return result;
            }
        }

        // 达到最大轮次
        result.setContent("[" + agentType.name() + " 回答达到最大轮次，请重试]");
        result.setSuccess(false);
        return result;
    }

    /** 构建 Agent 的 system prompt（含工具定义） */
    private String buildSystemPrompt(AgentType agentType) {
        String toolsJson = buildToolsForAgent(agentType);
        return agentType.getSystemPrompt() + "\n\n" +
            "你有以下工具可用。需要获取数据时，必须输出一个 JSON tool_call，格式为：\n" +
            "{\"type\":\"tool_call\",\"name\":\"工具名\",\"arguments\":{...}}\n" +
            "不需要工具时直接回答。\n\n" +
            "可用工具定义：\n" + toolsJson;
    }

    /** 构建仅包含该 Agent 工具集的 JSON 数组 */
    private String buildToolsForAgent(AgentType agentType) {
        List<String> toolNames = agentType.getTools();
        if (toolNames.isEmpty()) return "[]";

        List<McpClientManager.McpToolDef> allTools = mcpClientManager.getAllTools();
        StringBuilder sb = new StringBuilder("[\n");
        List<McpClientManager.McpToolDef> filtered = allTools.stream()
            .filter(t -> toolNames.contains(t.name()))
            .toList();

        for (int i = 0; i < filtered.size(); i++) {
            McpClientManager.McpToolDef t = filtered.get(i);
            sb.append("  {\"name\":\"").append(t.name()).append("\",");
            sb.append("\"description\":\"").append(t.description()).append("\"");
            sb.append("}");
            if (i < filtered.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    /** 尝试从 LLM 响应中提取 tool_call JSON */
    private Map<String, Object> extractToolCall(String response) {
        if (response == null || response.isBlank()) return null;
        try {
            String trimmed = response.trim();
            // 尝试直接解析
            if (trimmed.startsWith("{") && trimmed.contains("\"type\":\"tool_call\"")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(trimmed, Map.class);
                if ("tool_call".equals(map.get("type")) && map.containsKey("name")) {
                    return map;
                }
            }
            // 尝试提取 JSON 块
            if (trimmed.contains("```json")) {
                String json = trimmed.replaceAll("```json\\s*", "").replaceAll("```", "").trim();
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(json, Map.class);
                if ("tool_call".equals(map.get("type")) && map.containsKey("name")) {
                    return map;
                }
            }
        } catch (Exception e) {
            // 不是 tool_call，是普通文本
        }
        return null;
    }

    /** 拼接多 Agent 结果 */
    private String formatResults(List<AgentResult> results, List<String> agentNames) {
        if (results.isEmpty()) return "抱歉，未能理解您的问题。请换个方式提问。";

        if (results.size() == 1) {
            return results.get(0).getContent();
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            AgentResult r = results.get(i);
            sb.append("## ").append(getLabel(AgentType.valueOf(r.getAgentName()))).append("\n\n");
            sb.append(r.getContent());
            if (i < results.size() - 1) sb.append("\n\n---\n\n");
        }
        return sb.toString();
    }

    private String getLabel(AgentType type) {
        return switch (type) {
            case TUTOR -> "专业答疑";
            case PLAN -> "学习规划";
            case TRACKER -> "学习督导";
            case RECOMMEND -> "题目推荐";
            default -> type.name();
        };
    }

    private AgentType parseAgentType(String name) {
        try { return AgentType.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return String.valueOf(obj); }
    }
}
