package com.example.kaoyan.service;

import com.example.kaoyan.entity.KnowledgePoint;
import com.example.kaoyan.entity.Question;
import com.example.kaoyan.entity.QuestionKnowledgePoint;
import com.example.kaoyan.entity.QuestionOption;
import com.example.kaoyan.repository.KnowledgePointRepository;
import com.example.kaoyan.repository.QuestionKnowledgePointRepository;
import com.example.kaoyan.repository.QuestionOptionRepository;
import com.example.kaoyan.repository.QuestionRepository;
import com.example.kaoyan.util.AgentDebugLog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用 LLM 为题目打多知识点标签的服务。
 *
 * 流程：
 *   1. 组装题目全文（题干 + 选项）
 *   2. 从 knowledge_point 表拉取同科目候选知识点（LLM 约束输出范围）
 *   3. 调用 LLM tagQuestion，返回 JSON 多标签 + 置信度
 *   4. 解析后，逐个知识点：
 *      - 若已存在于 knowledge_point：直接关联
 *      - 若不存在但 LLM 新增：创建 KnowledgePoint 行
 *   5. 写入 question_knowledge_point 表（先清空旧标签再写入）
 *   6. 更新 question.knowledge_point_id 为置信度最高的主标签（兼容现有代码）
 *   7. 更新 question.tagging_status = success/failed
 */
@Service
@RequiredArgsConstructor
public class QuestionTaggingService {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final QuestionKnowledgePointRepository qkpRepository;
    private final LlmService llmService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 为单题打标（同步）。调用方应自行决定是否异步。 */
    @Transactional
    public void tagQuestion(Long questionId, Long userId) {
        Question q = questionRepository.findById(questionId).orElse(null);
        if (q == null) return;

        try {
            // 1. 题干 + 选项拼接
            String fullText = buildFullText(q);

            // 2. 候选知识点（同科目）
            List<KnowledgePoint> candidates = knowledgePointRepository.findBySubject(q.getSubject());
            List<String> candidateNames = candidates.stream()
                    .map(KnowledgePoint::getName)
                    .collect(Collectors.toList());

            // 3. 调用 LLM
            String json = llmService.tagQuestion(q.getSubject(), fullText, candidateNames, userId);
            json = stripCodeFence(json);

            // 4. 解析
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> tags = (List<Map<String, Object>>) parsed.get("tags");
            if (tags == null || tags.isEmpty()) {
                q.setTaggingStatus("failed");
                questionRepository.save(q);
                return;
            }

            // 5. 清空旧关联
            qkpRepository.deleteByQuestionId(questionId);

            Long primaryKpId = null;
            double primaryConf = -1;

            // 6. 写入新关联
            for (Map<String, Object> tag : tags) {
                String name = (String) tag.get("name");
                if (name == null || name.isBlank()) continue;
                Object confObj = tag.get("confidence");
                double conf = confObj instanceof Number ? ((Number) confObj).doubleValue() : 0.8;
                conf = Math.max(0.0, Math.min(1.0, conf));

                // 查找或创建知识点
                KnowledgePoint kp = knowledgePointRepository
                        .findByNameAndSubject(name, q.getSubject())
                        .orElseGet(() -> {
                            KnowledgePoint kpNew = new KnowledgePoint();
                            kpNew.setName(name);
                            kpNew.setSubject(q.getSubject());
                            return knowledgePointRepository.save(kpNew);
                        });

                QuestionKnowledgePoint rel = new QuestionKnowledgePoint();
                rel.setQuestionId(questionId);
                rel.setKnowledgePointId(kp.getId());
                rel.setWeight(BigDecimal.valueOf(conf));
                rel.setSource("llm");
                qkpRepository.save(rel);

                if (conf > primaryConf) {
                    primaryConf = conf;
                    primaryKpId = kp.getId();
                }
            }

            // 7. 设置主知识点（兼容 question.knowledge_point_id）
            if (primaryKpId != null) {
                q.setKnowledgePointId(primaryKpId);
            }
            q.setTaggingStatus("success");
            questionRepository.save(q);

            AgentDebugLog.ndjson("TAG", "QuestionTaggingService.tagQuestion", "ok",
                    "{\"questionId\":" + questionId + ",\"tagCount\":" + tags.size() + "}");
        } catch (Exception e) {
            AgentDebugLog.ndjson("TAG_ERR", "QuestionTaggingService.tagQuestion", e.getClass().getSimpleName(),
                    "{\"questionId\":" + questionId + "}");
            q.setTaggingStatus("failed");
            questionRepository.save(q);
        }
    }

    /** 批量为 tagging_status = pending 的题目打标。限量防止 LLM 调用爆炸。 */
    public int tagPending(Long userId, int limit) {
        // 按 id 升序取 pending 题目
        List<Question> pending = questionRepository.findAll().stream()
                .filter(q -> q.getTaggingStatus() == null || "pending".equals(q.getTaggingStatus()))
                .limit(limit)
                .collect(Collectors.toList());
        int done = 0;
        for (Question q : pending) {
            tagQuestion(q.getId(), userId);
            done++;
        }
        return done;
    }

    private String buildFullText(Question q) {
        StringBuilder sb = new StringBuilder();
        sb.append(q.getContent() == null ? "" : q.getContent());
        // 附加选项（选择题）
        List<QuestionOption> opts = questionOptionRepository.findByQuestionIdOrderByLabelAsc(q.getId());
        if (opts != null && !opts.isEmpty()) {
            sb.append("\n");
            for (QuestionOption o : opts) {
                sb.append("\n").append(o.getLabel()).append(". ").append(o.getContent());
            }
        }
        // 解析对主题不太相关，不拼入
        return sb.toString();
    }

    private String stripCodeFence(String s) {
        if (s == null) return "{}";
        return s.replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();
    }
}
