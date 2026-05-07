package com.example.kaoyan.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * MCP Client Manager — 管理对 3 个 MCP Server 的 REST 调用。
 *
 * 每个 MCP Server 暴露 POST 端点，本 Manager 负责：
 *   1. 工具发现（硬编码注册表，后续可改为动态拉取）
 *   2. 工具调用（路由到对应 Server 端点）
 *
 * Agent 系统通过本 Manager 调用 MCP 工具，而不是直接访问 Repository。
 */
@Service
@RequiredArgsConstructor
public class McpClientManager {

    private final WebClient.Builder webClientBuilder;

    @Value("${mcp.rag.url:http://localhost:8091}")
    private String ragUrl;

    @Value("${mcp.question.url:http://localhost:8092}")
    private String questionUrl;

    @Value("${mcp.knowledge.url:http://localhost:8093}")
    private String knowledgeUrl;

    // ─── 工具注册表 ───────────────────────────────

    /** 所有可用工具定义（供 Agent system prompt 使用） */
    public List<McpToolDef> getAllTools() {
        return List.of(
            // RAG Server
            new McpToolDef("search_knowledge_base", "在用户上传的考研资料中语义搜索相关内容", ragUrl + "/mcp/rag/search",
                Map.of("query", "string", "kbId", "integer?")),
            new McpToolDef("list_knowledge_bases", "列出用户的知识库列表", ragUrl + "/mcp/rag/list-kb",
                Map.of("userId", "integer")),
            new McpToolDef("get_document_info", "查询文档详情", ragUrl + "/mcp/rag/doc-info",
                Map.of("docId", "integer")),

            // Question Server
            new McpToolDef("search_question_bank", "按科目/知识点/难度/题型搜题", questionUrl + "/mcp/question/search",
                Map.of("subject", "string?", "kpId", "integer?", "difficulty", "integer?", "type", "string?")),
            new McpToolDef("recommend_questions", "根据用户掌握度推荐练习", questionUrl + "/mcp/question/recommend",
                Map.of("userId", "integer", "count", "integer")),
            new McpToolDef("get_similar_questions", "基于向量推荐相似题目", questionUrl + "/mcp/question/similar",
                Map.of("questionId", "integer")),
            new McpToolDef("extract_question_from_answer", "从AI解答文本中提取结构化题目", questionUrl + "/mcp/question/extract",
                Map.of("answerText", "string", "userId", "integer")),

            // Knowledge Server
            new McpToolDef("get_knowledge_point_tree", "获取科目知识点树结构", knowledgeUrl + "/mcp/knowledge/tree",
                Map.of("subject", "string")),
            new McpToolDef("get_kp_detail", "查询知识点详情和掌握度", knowledgeUrl + "/mcp/knowledge/detail",
                Map.of("kpId", "integer", "userId", "integer")),
            new McpToolDef("get_weak_points", "获取用户薄弱知识点列表", knowledgeUrl + "/mcp/knowledge/weak",
                Map.of("userId", "integer", "threshold", "number?")),
            new McpToolDef("diagnose_learning", "生成AI学习诊断报告", knowledgeUrl + "/mcp/knowledge/diagnose",
                Map.of("userId", "integer"))
        );
    }

    /** 生成 tools JSON 数组字符串，注入 LLM system prompt */
    public String getToolsJson() {
        StringBuilder sb = new StringBuilder("[\n");
        List<McpToolDef> tools = getAllTools();
        for (int i = 0; i < tools.size(); i++) {
            McpToolDef t = tools.get(i);
            sb.append("  {");
            sb.append("\"name\":\"").append(t.name()).append("\",");
            sb.append("\"description\":\"").append(t.description()).append("\",");
            sb.append("\"parameters\":{");
            boolean first = true;
            for (Map.Entry<String, String> p : t.params().entrySet()) {
                if (!first) sb.append(",");
                String[] parts = p.getValue().split("\\?");
                sb.append("\"").append(p.getKey()).append("\":{\"type\":\"").append(parts[0]).append("\"");
                if (parts.length > 1) sb.append(",\"required\":false");
                sb.append("}");
                first = false;
            }
            sb.append("}}");
            if (i < tools.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    // ─── 工具调用 ───────────────────────────────

    /** 调用指定工具 */
    public Object callTool(String toolName, Map<String, Object> args) {
        McpToolDef tool = getAllTools().stream()
            .filter(t -> t.name().equals(toolName))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("未知工具：" + toolName));

        WebClient client = webClientBuilder
            .codecs(c -> c.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .baseUrl(tool.endpoint().substring(0, tool.endpoint().lastIndexOf('/')))
            .build();

        String path = tool.endpoint().substring(tool.endpoint().indexOf("/mcp/"));

        try {
            return client.post()
                .uri(path)
                .bodyValue(args != null ? args : Map.of())
                .retrieve()
                .bodyToMono(Object.class)
                .block();
        } catch (Exception e) {
            return Map.of("error", "工具调用失败：" + e.getMessage());
        }
    }

    // ─── 工具定义 DTO ───────────────────────────

    public record McpToolDef(
        String name,
        String description,
        String endpoint,
        Map<String, String> params
    ) {}
}
