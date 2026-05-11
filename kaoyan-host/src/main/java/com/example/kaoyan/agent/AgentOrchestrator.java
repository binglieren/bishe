package com.example.kaoyan.agent;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AgentOrchestrator {

    private final ChatLanguageModel chatLanguageModel;
    private final SupervisorAgent supervisorAgent;
    private final AgentTools agentTools;

    public AgentOrchestrator(ChatLanguageModel chatLanguageModel,
                             SupervisorAgent supervisorAgent,
                             AgentTools agentTools) {
        this.chatLanguageModel = chatLanguageModel;
        this.supervisorAgent = supervisorAgent;
        this.agentTools = agentTools;
    }

    interface AgentChat {
        @SystemMessage("{{systemPrompt}}")
        String chat(@V("systemPrompt") String systemPrompt,
                    @UserMessage String userMessage,
                    @V("sessionId") Long sessionId);
    }

    public String execute(String userMessage, AgentContext context) {
        if (userMessage == null || userMessage.isBlank()) return "";

        Map<String, Object> classification = supervisorAgent.classify(
                userMessage, context.getHistory(), context.getUserId());

        @SuppressWarnings("unchecked")
        List<String> agentNames = (List<String>) classification.getOrDefault("agents", List.of("TUTOR"));

        if (agentNames.size() == 1 && "CLARIFY".equals(agentNames.get(0))) {
            return "请问您是想：1）咨询学习问题 2）制定复习计划 3）查看学习效果 还是 4）练习题目？请告诉我具体需求，我会更好地为您服务。";
        }

        List<AgentResult> results = new ArrayList<>();
        for (String name : agentNames) {
            AgentType agentType = parseAgentType(name);
            if (agentType == null || agentType == AgentType.SUPERVISOR) continue;
            results.add(executeAgent(agentType, userMessage, context));
        }

        return formatResults(results);
    }

    private AgentResult executeAgent(AgentType agentType, String userMessage, AgentContext context) {
        AgentResult result = new AgentResult();
        result.setAgentName(agentType.name());
        result.setLabel(getLabel(agentType));

        try {
            String systemPrompt = buildPrompt(agentType, context);

            AgentChat agent = AiServices.builder(AgentChat.class)
                    .chatLanguageModel(chatLanguageModel)
                    .tools(agentTools)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                    .build();

            String reply = agent.chat(systemPrompt, userMessage, context.getUserId());
            result.setContent(reply != null ? reply : "");
            result.setSuccess(true);

        } catch (Exception e) {
            log.error("Agent执行失败: {}", agentType.name(), e);
            result.setContent("抱歉，处理过程出现问题：" + e.getMessage());
            result.setSuccess(false);
        }

        return result;
    }

    private String buildPrompt(AgentType agentType, AgentContext context) {
        StringBuilder sb = new StringBuilder();

        if (context.getSystemPrompt() != null && !context.getSystemPrompt().isBlank()) {
            sb.append(context.getSystemPrompt());
        } else {
            sb.append(agentType.getSystemPrompt());
        }

        if (context.getKnowledgeBaseIds() != null && !context.getKnowledgeBaseIds().isEmpty()) {
            sb.append("\n\n[当前知识库] ");
            List<String> names = context.getKnowledgeBaseNames();
            for (int i = 0; i < context.getKnowledgeBaseIds().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(names != null && i < names.size() ? names.get(i) : "KB#" + context.getKnowledgeBaseIds().get(i));
            }
        }

        return sb.toString();
    }

    private String formatResults(List<AgentResult> results) {
        if (results.isEmpty()) return "抱歉，未能理解您的问题。";
        if (results.size() == 1) return results.get(0).getContent();

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
}
