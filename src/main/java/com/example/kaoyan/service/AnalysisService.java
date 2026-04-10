package com.example.kaoyan.service;

import com.example.kaoyan.entity.KnowledgeMastery;
import com.example.kaoyan.entity.KnowledgePoint;
import com.example.kaoyan.entity.Question;
import com.example.kaoyan.repository.KnowledgeMasteryRepository;
import com.example.kaoyan.repository.KnowledgePointRepository;
import com.example.kaoyan.repository.QuestionRepository;
import com.example.kaoyan.util.AgentDebugLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 薄弱知识点分析服务
 */
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final KnowledgeMasteryRepository masteryRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final QuestionRepository questionRepository;

    /**
     * 获取所有知识点掌握度
     */
    public List<Map<String, Object>> getMasteryOverview(Long userId) {
        List<KnowledgeMastery> masteries = masteryRepository.findByUserId(userId);

        return masteries.stream().map(m -> {
            Map<String, Object> item = new HashMap<>();
            item.put("knowledgePointId", m.getKnowledgePointId());
            item.put("correctCount", m.getCorrectCount());
            item.put("totalCount", m.getTotalCount());
            item.put("masteryLevel", m.getMasteryLevel());

            // 附加知识点名称
            knowledgePointRepository.findById(m.getKnowledgePointId())
                    .ifPresent(kp -> {
                        item.put("knowledgePointName", kp.getName());
                        item.put("subject", kp.getSubject());
                    });

            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 获取薄弱知识点（掌握度低于阈值）
     */
    public List<Map<String, Object>> getWeakPoints(Long userId, BigDecimal threshold) {
        if (threshold == null) {
            threshold = new BigDecimal("60");
        }

        List<KnowledgeMastery> weakPoints = masteryRepository.findWeakPoints(userId, threshold);

        return weakPoints.stream().map(m -> {
            Map<String, Object> item = new HashMap<>();
            item.put("knowledgePointId", m.getKnowledgePointId());
            item.put("correctCount", m.getCorrectCount());
            item.put("totalCount", m.getTotalCount());
            item.put("masteryLevel", m.getMasteryLevel());

            knowledgePointRepository.findById(m.getKnowledgePointId())
                    .ifPresent(kp -> {
                        item.put("knowledgePointName", kp.getName());
                        item.put("subject", kp.getSubject());
                    });

            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 针对薄弱知识点推荐练习题
     */
    public List<Question> getRecommendedQuestions(Long userId, int count) {
        // 找到最薄弱的知识点
        List<KnowledgeMastery> weakPoints = masteryRepository
                .findByUserIdOrderByMasteryLevelAsc(userId);

        if (weakPoints.isEmpty()) {
            return Collections.emptyList();
        }

        // 从最薄弱的知识点中取题
        List<Question> recommended = new ArrayList<>();
        for (KnowledgeMastery mastery : weakPoints) {
            if (recommended.size() >= count) break;

            int remaining = count - recommended.size();
            List<Question> questions = questionRepository.findRandomByKnowledgePointId(
                    mastery.getKnowledgePointId(),
                    PageRequest.of(0, remaining)
            );
            recommended.addAll(questions);
        }

        return recommended;
    }

    /**
     * 按科目统计掌握度
     */
    public Map<String, Object> getSubjectAnalysis(Long userId) {
        List<KnowledgeMastery> masteries = masteryRepository.findByUserId(userId);

        // 按科目分组
        Map<String, List<KnowledgeMastery>> bySubject = new HashMap<>();
        for (KnowledgeMastery m : masteries) {
            knowledgePointRepository.findById(m.getKnowledgePointId())
                    .ifPresent(kp -> bySubject.computeIfAbsent(kp.getSubject(), k -> new ArrayList<>()).add(m));
        }

        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, List<KnowledgeMastery>> entry : bySubject.entrySet()) {
            List<KnowledgeMastery> subjectMasteries = entry.getValue();
            int totalCorrect = subjectMasteries.stream().mapToInt(m -> safeInt(m.getCorrectCount())).sum();
            int totalAttempts = subjectMasteries.stream().mapToInt(m -> safeInt(m.getTotalCount())).sum();
            double avgMastery = subjectMasteries.stream()
                    .mapToDouble(m -> masteryLevelOrZero(m, userId))
                    .average().orElse(0);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalCorrect", totalCorrect);
            stats.put("totalAttempts", totalAttempts);
            stats.put("averageMastery", Math.round(avgMastery * 100) / 100.0);
            stats.put("knowledgePointCount", subjectMasteries.size());
            result.put(entry.getKey(), stats);
        }

        return result;
    }

    private static int safeInt(Integer v) {
        return v != null ? v : 0;
    }

    /**
     * DB 中 correct_count / total_count / mastery_level 可能为 NULL，直接拆箱会在 mapToInt/mapToDouble 时 NPE。
     */
    private static double masteryLevelOrZero(KnowledgeMastery m, Long userId) {
        BigDecimal ml = m.getMasteryLevel();
        if (ml != null) {
            return ml.doubleValue();
        }
        // #region agent log
        AgentDebugLog.ndjson("Anull", "AnalysisService.getSubjectAnalysis", "masteryLevel was null",
                "{\"userId\":" + userId + ",\"knowledgePointId\":" + m.getKnowledgePointId() + "}");
        // #endregion
        return 0.0;
    }
}
