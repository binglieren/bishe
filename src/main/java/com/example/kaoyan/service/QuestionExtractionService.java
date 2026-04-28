package com.example.kaoyan.service;

import com.example.kaoyan.dto.QuestionDTO;
import com.example.kaoyan.dto.QuestionExtractionResult;
import com.example.kaoyan.entity.KnowledgePoint;
import com.example.kaoyan.entity.Question;
import com.example.kaoyan.entity.UserQuestion;
import com.example.kaoyan.repository.KnowledgePointRepository;
import com.example.kaoyan.repository.QuestionRepository;
import com.example.kaoyan.repository.UserQuestionRepository;
import com.example.kaoyan.util.AgentDebugLog;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * 题目提取服务：从拍照问答结果中提取结构化题目并保存到题库
 */
@Service
@RequiredArgsConstructor
public class QuestionExtractionService {

    private final LlmService llmService;
    private final QuestionService questionService;
    private final KnowledgePointRepository knowledgePointRepository;
    private final UserQuestionRepository userQuestionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionTaggingService questionTaggingService;
    private final QuestionEmbeddingService questionEmbeddingService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 异步提取并保存题目（由 ChatService 在后台线程调用）
     * 整个方法在 try-catch 包裹内，任何失败都静默处理，不影响主聊天流程
     */
    public void extractAndSave(Long userId, Long sessionId, String imageBase64, String aiAnswer) {
        try {
            // 第一步：调用 LLM 提取结构化题目
            AgentDebugLog.ndjson("H5a", "QuestionExtractionService", "start extraction", "{}");
            String rawJson = llmService.extractQuestion(imageBase64, aiAnswer, userId);
            // 去掉可能的 markdown 代码块标记
            String json = rawJson
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            QuestionExtractionResult result = objectMapper.readValue(json, QuestionExtractionResult.class);
            if (result.getContent() == null || result.getAnswer() == null) {
                AgentDebugLog.ndjson("H5warn", "QuestionExtractionService", "missing content/answer", "{}");
                return;
            }
            AgentDebugLog.ndjson("H5b", "QuestionExtractionService", "parsed ok",
                    "{\"type\":\"" + result.getType() + "\"}");

            // 归一化分类：只保留 数学 / 英语 / 专业课 三类
            String normalizedSubject = QuestionService.normalizeSubject(result.getSubject());
            AgentDebugLog.ndjson("H5cat", "QuestionExtractionService", "normalize subject",
                    "{\"raw\":\"" + result.getSubject() + "\",\"normalized\":\"" + normalizedSubject + "\"}");

            // 第二步：获取或创建知识点（使用归一化后的科目）
            Long knowledgePointId = null;
            if (result.getKnowledgePoints() != null && !result.getKnowledgePoints().isEmpty()) {
                String kpName = result.getKnowledgePoints().get(0);
                KnowledgePoint kp = knowledgePointRepository
                        .findByNameAndSubject(kpName, normalizedSubject)
                        .orElseGet(() -> {
                            KnowledgePoint newKp = new KnowledgePoint();
                            newKp.setName(kpName);
                            newKp.setSubject(normalizedSubject);
                            return knowledgePointRepository.save(newKp);
                        });
                knowledgePointId = kp.getId();
            }

            // 第三步：构建 QuestionDTO 并保存到系统题库
            QuestionDTO dto = new QuestionDTO();
            dto.setType(result.getType() != null ? result.getType() : "简答");
            dto.setSubject(normalizedSubject);
            dto.setContent(result.getContent());
            dto.setAnswer(result.getAnswer());
            dto.setAnalysis(result.getAnalysis() != null ? result.getAnalysis() : "");
            dto.setDifficulty(3);
            dto.setSource("用户拍照上传");
            dto.setKnowledgePointId(knowledgePointId);
            if (result.getOptions() != null && !result.getOptions().isEmpty()) {
                dto.setOptions(result.getOptions().stream().map(o -> {
                    QuestionDTO.OptionDTO opt = new QuestionDTO.OptionDTO();
                    opt.setLabel(o.getLabel());
                    opt.setContent(o.getContent());
                    opt.setIsCorrect(Boolean.TRUE.equals(o.getIsCorrect()));
                    return opt;
                }).collect(Collectors.toList()));
            }
            Question question = questionService.createQuestion(dto);

            // 第四步：LLM 多标签打标（从候选知识点中选，允许新增标准知识点）
            try {
                questionTaggingService.tagQuestion(question.getId(), userId);
                AgentDebugLog.ndjson("H5tag", "QuestionExtractionService", "tagging done", "{}");
            } catch (Exception e) {
                AgentDebugLog.ndjson("H5tag_skip", "QuestionExtractionService",
                        e.getClass().getSimpleName(), "{}");
            }

            // 第五步：向量化题干 + 选项 + 标签（打标后再 embed，标签参与语义表示）
            try {
                questionEmbeddingService.embedQuestion(question.getId(), userId);
                AgentDebugLog.ndjson("H5c", "QuestionExtractionService", "embedding stored", "{}");
            } catch (Exception e) {
                AgentDebugLog.ndjson("H5skip", "QuestionExtractionService", "embedding skipped", "{}");
            }

            // 第六步：保存到用户个人题库（幂等，重复则跳过）
            if (!userQuestionRepository.existsByUserIdAndQuestionId(userId, question.getId())) {
                UserQuestion uq = new UserQuestion();
                uq.setUserId(userId);
                uq.setQuestionId(question.getId());
                uq.setSourceSessionId(sessionId);
                userQuestionRepository.save(uq);
                AgentDebugLog.ndjson("H5ok", "QuestionExtractionService", "user_question saved",
                        "{\"questionId\":" + question.getId() + ",\"userId\":" + userId + "}");
            }

        } catch (Exception e) {
            AgentDebugLog.ndjson("H5err", "QuestionExtractionService",
                    e.getClass().getSimpleName(), "{\"msg\":\"" + e.getMessage() + "\"}");
        }
    }
}
