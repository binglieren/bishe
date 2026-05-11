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
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionEmbeddingService {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionKnowledgePointRepository qkpRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /** 为单题生成并存储 embedding。 */
    public boolean embedQuestion(Long questionId, Long userId) {
        Question q = questionRepository.findById(questionId).orElse(null);
        if (q == null) return false;

        try {
            String text = buildEmbeddingText(q);
            Embedding embedding = embeddingModel.embed(text).content();
            if (embedding == null || embedding.vector().length == 0) {
                q.setEmbeddingStatus("failed");
                questionRepository.save(q);
                return false;
            }

            // 存到 LangChain4j 向量表
            TextSegment seg = TextSegment.from(text);
            seg.metadata().put("type", "question");
            seg.metadata().put("question_id", String.valueOf(questionId));
            seg.metadata().put("subject", q.getSubject() != null ? q.getSubject() : "");
            embeddingStore.add(embedding, seg);

            // 同步更新 question.embedding 列（兼容 findSimilarByVector 等旧查询）
            String vectorStr = embeddingToVectorString(embedding);
            questionRepository.updateEmbedding(questionId, vectorStr);

            q.setEmbeddingStatus("success");
            questionRepository.save(q);

            AgentDebugLog.ndjson("EMB", "QuestionEmbeddingService.embedQuestion", "ok",
                    "{\"questionId\":" + questionId + ",\"dim\":" + embedding.vector().length + "}");
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

    private String embeddingToVectorString(Embedding embedding) {
        float[] vec = embedding.vector();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
