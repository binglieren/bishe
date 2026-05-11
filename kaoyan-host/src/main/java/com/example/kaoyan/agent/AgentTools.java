package com.example.kaoyan.agent;

import com.example.kaoyan.mcp.McpClientManager;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AgentTools {

    private final McpClientManager mcpClientManager;

    public AgentTools(McpClientManager mcpClientManager) {
        this.mcpClientManager = mcpClientManager;
    }

    @Tool("在用户上传的考研资料中语义搜索相关内容")
    public String searchKnowledgeBase(
            @ToolMemoryId Long sessionId,
            String query) {
        return callMcp("search_knowledge_base", Map.of("query", query, "sessionId", sessionId));
    }

    @Tool("按科目/知识点/难度/题型搜索题目")
    public String searchQuestionBank(
            @ToolMemoryId Long sessionId,
            String subject, String kpId, String difficulty, String type) {
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        if (subject != null) args.put("subject", subject);
        if (kpId != null) args.put("kpId", Integer.parseInt(kpId));
        if (difficulty != null) args.put("difficulty", Integer.parseInt(difficulty));
        if (type != null) args.put("type", type);
        return callMcp("search_question_bank", args);
    }

    @Tool("基于向量推荐相似题目")
    public String getSimilarQuestions(
            @ToolMemoryId Long sessionId,
            String questionId) {
        return callMcp("get_similar_questions", Map.of("questionId", Integer.parseInt(questionId)));
    }

    @Tool("获取用户薄弱知识点列表")
    public String getWeakPoints(
            @ToolMemoryId Long sessionId,
            String threshold) {
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("userId", sessionId);
        if (threshold != null) args.put("threshold", Double.parseDouble(threshold));
        return callMcp("get_weak_points", args);
    }

    @Tool("查询知识点详情和掌握度")
    public String getKpDetail(
            @ToolMemoryId Long sessionId,
            String kpId) {
        return callMcp("get_kp_detail", Map.of("kpId", Integer.parseInt(kpId), "userId", sessionId));
    }

    @Tool("获取科目知识点树结构")
    public String getKnowledgePointTree(
            @ToolMemoryId Long sessionId,
            String subject) {
        return callMcp("get_knowledge_point_tree", Map.of("subject", subject != null ? subject : ""));
    }

    @Tool("生成AI学习诊断报告")
    public String diagnoseLearning(
            @ToolMemoryId Long sessionId) {
        return callMcp("diagnose_learning", Map.of("userId", sessionId));
    }

    @Tool("根据用户掌握度推荐练习题")
    public String recommendQuestions(
            @ToolMemoryId Long sessionId,
            String count) {
        return callMcp("recommend_questions", Map.of("userId", sessionId,
                "count", count != null ? Integer.parseInt(count) : 5));
    }

    @Tool("从AI解答文本中提取结构化题目")
    public String extractQuestionFromAnswer(
            @ToolMemoryId Long sessionId,
            String answerText) {
        return callMcp("extract_question_from_answer",
                Map.of("answerText", answerText, "userId", sessionId));
    }

    private String callMcp(String toolName, Map<String, Object> args) {
        try {
            Object result = mcpClientManager.callTool(toolName, args);
            return result instanceof String ? (String) result : new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("MCP工具调用失败: {} {}", toolName, e.getMessage());
            return "工具调用失败: " + e.getMessage();
        }
    }
}
