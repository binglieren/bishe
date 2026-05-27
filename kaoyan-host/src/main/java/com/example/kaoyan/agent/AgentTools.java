package com.example.kaoyan.agent;

import com.example.kaoyan.service.KnowledgeInternalService;
import com.example.kaoyan.service.QuestionInternalService;
import com.example.kaoyan.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AgentTools {

    private final RagService ragService;
    private final QuestionInternalService questionService;
    private final KnowledgeInternalService knowledgeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentTools(RagService ragService,
                      QuestionInternalService questionService,
                      KnowledgeInternalService knowledgeService) {
        this.ragService = ragService;
        this.questionService = questionService;
        this.knowledgeService = knowledgeService;
    }

    @Tool("在用户上传的考研资料中语义搜索相关内容")
    public String searchKnowledgeBase(
            @ToolMemoryId Long sessionId,
            String query) {
        return toJson(ragService.searchKnowledgeBase(query, null));
    }

    @Tool("按科目/知识点/难度/题型搜索题目")
    public String searchQuestionBank(
            @ToolMemoryId Long sessionId,
            String subject, String kpId, String difficulty, String type) {
        Long kpIdLong = kpId != null ? Long.parseLong(kpId) : null;
        Integer diff = difficulty != null ? Integer.parseInt(difficulty) : null;
        return toJson(questionService.searchQuestions(subject, type, diff, kpIdLong));
    }

    @Tool("基于向量推荐相似题目")
    public String getSimilarQuestions(
            @ToolMemoryId Long sessionId,
            String questionId) {
        return toJson(questionService.getSimilarQuestions(Long.parseLong(questionId)));
    }

    @Tool("获取用户薄弱知识点列表")
    public String getWeakPoints(
            @ToolMemoryId Long sessionId,
            String threshold) {
        Double thr = threshold != null ? Double.parseDouble(threshold) : null;
        return toJson(knowledgeService.getWeakPoints(sessionId, thr));
    }

    @Tool("查询知识点详情和掌握度")
    public String getKpDetail(
            @ToolMemoryId Long sessionId,
            String kpId) {
        return toJson(knowledgeService.getKpDetail(Long.parseLong(kpId), sessionId));
    }

    @Tool("获取科目知识点树结构")
    public String getKnowledgePointTree(
            @ToolMemoryId Long sessionId,
            String subject) {
        return toJson(knowledgeService.getKnowledgePointTree(subject));
    }

    @Tool("生成AI学习诊断报告")
    public String diagnoseLearning(
            @ToolMemoryId Long sessionId) {
        return toJson(knowledgeService.diagnoseLearning(sessionId));
    }

    @Tool("根据用户掌握度推荐练习题")
    public String recommendQuestions(
            @ToolMemoryId Long sessionId,
            String count) {
        int cnt = count != null ? Integer.parseInt(count) : 5;
        return toJson(questionService.recommendQuestions(sessionId, cnt));
    }

    @Tool("从AI解答文本中提取结构化题目")
    public String extractQuestionFromAnswer(
            @ToolMemoryId Long sessionId,
            String answerText) {
        return toJson(questionService.extractQuestionFromAnswer(answerText, sessionId));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("AgentTool JSON序列化失败", e);
            return "{}";
        }
    }
}
