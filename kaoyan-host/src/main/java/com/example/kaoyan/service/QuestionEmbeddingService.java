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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目向量化服务。
 *
 * 设计思路：
 *   - Embedding 输入 = 科目 + 题型 + 题干 + 选项 + 已打好的知识点标签
 *     把标签拼入文本，强化语义（不同标签的相似题在向量空间离得更近）
 *   - 先打标再 embed，所以 pipeline 是：extract → tag → embed
 *   - 失败不阻塞，标记 embedding_status = failed，允许后续重试
 */
@Service
@RequiredArgsConstructor
public class QuestionEmbeddingService {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionKnowledgePointRepository qkpRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final LlmService llmService;

    /** 为单题生成并存储 embedding。 */
    public boolean embedQuestion(Long questionId, Long userId) {
        Question q = questionRepository.findById(questionId).orElse(null);
        if (q == null) return false;

        try {
            String text = buildEmbeddingText(q);
            float[] vec = llmService.getEmbedding(text, userId);
            if (vec == null || vec.length == 0) {
                q.setEmbeddingStatus("failed");
                questionRepository.save(q);
                return false;
            }

            String vectorStr = llmService.vectorToString(vec);
            questionRepository.updateEmbedding(questionId, vectorStr);

            q.setEmbeddingStatus("success");
            questionRepository.save(q);

            AgentDebugLog.ndjson("EMB", "QuestionEmbeddingService.embedQuestion", "ok",
                    "{\"questionId\":" + questionId + ",\"dim\":" + vec.length + "}");
            return true;
        } catch (Exception e) {
            AgentDebugLog.ndjson("EMB_ERR", "QuestionEmbeddingService.embedQuestion",
                    e.getClass().getSimpleName(),
                    "{\"questionId\":" + questionId + "}");
            q.setEmbeddingStatus("failed");
            questionRepository.save(q);
            return false;
        }
    }

    /** 批量对 pending 状态的题目生成 embedding。 */
    public int embedPending(Long userId, int limit) {
        List<Question> pending = questionRepository.findAll().stream()
                .filter(q -> q.getEmbeddingStatus() == null || "pending".equals(q.getEmbeddingStatus()))
                .limit(limit)
                .collect(Collectors.toList());
        int done = 0;
        for (Question q : pending) {
            if (embedQuestion(q.getId(), userId)) done++;
        }
        return done;
    }

    /**
     * 构造 embedding 输入文本：
     *   [科目][题型][难度N]
     *   题干: ...
     *   选项: A. xxx  B. yyy
     *   知识点: 洛必达法则 / 极限 / 连续性
     */
    private String buildEmbeddingText(Question q) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(q.getSubject()).append("]");
        sb.append("[").append(q.getType()).append("]");
        if (q.getDifficulty() != null) {
            sb.append("[难度").append(q.getDifficulty()).append("]");
        }
        sb.append("\n题干: ").append(q.getContent() == null ? "" : q.getContent());

        // 选项
        List<QuestionOption> opts = questionOptionRepository.findByQuestionIdOrderByLabelAsc(q.getId());
        if (opts != null && !opts.isEmpty()) {
            sb.append("\n选项:");
            for (QuestionOption o : opts) {
                sb.append(" ").append(o.getLabel()).append(". ").append(o.getContent());
            }
        }

        // 知识点标签
        List<QuestionKnowledgePoint> qkps = qkpRepository.findByQuestionId(q.getId());
        if (qkps != null && !qkps.isEmpty()) {
            Set<Long> kpIds = qkps.stream()
                    .map(QuestionKnowledgePoint::getKnowledgePointId).collect(Collectors.toSet());
            List<KnowledgePoint> kps = knowledgePointRepository.findAllById(kpIds);
            if (!kps.isEmpty()) {
                sb.append("\n知识点: ").append(
                        kps.stream().map(KnowledgePoint::getName).collect(Collectors.joining(" / ")));
            }
        }
        return sb.toString();
    }
}
