package com.example.kaoyan.service;

import com.example.kaoyan.dto.QuestionRecommendationDTO;
import com.example.kaoyan.entity.KnowledgeMastery;
import com.example.kaoyan.entity.KnowledgePoint;
import com.example.kaoyan.entity.Question;
import com.example.kaoyan.entity.QuestionKnowledgePoint;
import com.example.kaoyan.entity.UserQuestion;
import com.example.kaoyan.repository.KnowledgeMasteryRepository;
import com.example.kaoyan.repository.KnowledgePointRepository;
import com.example.kaoyan.repository.QuestionKnowledgePointRepository;
import com.example.kaoyan.repository.QuestionRepository;
import com.example.kaoyan.repository.UserQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目推荐服务：多信号加权推荐算法。
 *
 * 推荐分 = 0.45 × 薄弱度
 *        + 0.25 × 知识点相关度（利用树结构扩展）
 *        + 0.15 × 难度匹配
 *        + 0.15 × 新鲜度
 *
 * 推荐范围 = 薄弱知识点的题目 ∪ 薄弱知识点的兄弟/子节点题目 ∪ 错题重刷
 *         - 已做对且精通的题目
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    // 权重：薄弱度 + 知识点相关度（单标签） + 多标签 Jaccard + 难度匹配 + 新鲜度
    // 多标签 Jaccard 表达"综合考点重合度"，对考研综合题尤其重要
    private static final double W_WEAKNESS   = 0.40;
    private static final double W_RELEVANCE  = 0.15;
    private static final double W_JACCARD    = 0.20;
    private static final double W_DIFFICULTY = 0.12;
    private static final double W_FRESHNESS  = 0.13;

    private final KnowledgeMasteryRepository masteryRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final QuestionRepository questionRepository;
    private final UserQuestionRepository userQuestionRepository;
    private final QuestionKnowledgePointRepository qkpRepository;

    /**
     * 为用户生成推荐题目列表。
     */
    public List<QuestionRecommendationDTO> recommend(Long userId, int limit) {
        // ── 1. 加载用户上下文 ─────────────────────────────────────
        List<KnowledgeMastery> masteryList = masteryRepository.findByUserId(userId);
        List<UserQuestion> userQuestions = userQuestionRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // 用户做过的题目 ID → UserQuestion 映射（用于新鲜度计算）
        Map<Long, UserQuestion> doneQuestionMap = userQuestions.stream()
                .collect(Collectors.toMap(UserQuestion::getQuestionId, uq -> uq, (a, b) -> a));

        // 冷启动：用户从未做题
        if (masteryList.isEmpty() && userQuestions.isEmpty()) {
            return coldStart(limit);
        }

        // ── 2. 确定候选知识点集合（含相关性分数） ─────────────────
        // kp_id → (relevance, kpName)
        Map<Long, double[]> kpRelevance = new HashMap<>();  // value[0]=relevance, value[1]=mastery
        Map<Long, String> kpName = new HashMap<>();
        Map<Long, String> kpSubject = new HashMap<>();

        // 2.1 薄弱知识点：直接命中，relevance=1.0
        for (KnowledgeMastery km : masteryList) {
            double mastery = km.getMasteryLevel() != null ? km.getMasteryLevel().doubleValue() : 0.0;
            kpRelevance.merge(km.getKnowledgePointId(), new double[]{1.0, mastery},
                    (oldV, newV) -> oldV[0] >= newV[0] ? oldV : newV);
        }

        // 2.2 相关知识点扩展：兄弟 (0.7)、子 (0.6)、父 (0.5)
        Set<Long> seedKpIds = new HashSet<>(kpRelevance.keySet());
        for (Long kpId : seedKpIds) {
            KnowledgePoint kp = knowledgePointRepository.findById(kpId).orElse(null);
            if (kp == null) continue;

            // 子节点
            for (KnowledgePoint child : knowledgePointRepository.findByParentId(kpId)) {
                kpRelevance.merge(child.getId(), new double[]{0.6, 50.0},
                        (oldV, newV) -> oldV[0] >= newV[0] ? oldV : newV);
            }

            // 兄弟节点
            if (kp.getParentId() != null) {
                for (KnowledgePoint sib : knowledgePointRepository.findByParentId(kp.getParentId())) {
                    if (!sib.getId().equals(kpId)) {
                        kpRelevance.merge(sib.getId(), new double[]{0.7, 50.0},
                                (oldV, newV) -> oldV[0] >= newV[0] ? oldV : newV);
                    }
                }
                // 父节点
                kpRelevance.merge(kp.getParentId(), new double[]{0.5, 50.0},
                        (oldV, newV) -> oldV[0] >= newV[0] ? oldV : newV);
            }
        }

        // 2.3 缓存所有候选知识点的 name/subject
        for (Long kpId : kpRelevance.keySet()) {
            knowledgePointRepository.findById(kpId).ifPresent(kp -> {
                kpName.put(kp.getId(), kp.getName());
                kpSubject.put(kp.getId(), kp.getSubject());
            });
        }

        // ── 3. 计算用户每个科目的目标难度（用于难度匹配） ─────────
        Map<String, Integer> targetDifficulty = computeTargetDifficulty(userQuestions);

        // ── 4. 拉取候选题目，计算综合分 ─────────────────────────
        // 4.1 主知识点候选（question.knowledge_point_id 匹配）
        Set<Question> candidateSet = new LinkedHashSet<>();
        if (!kpRelevance.isEmpty()) {
            candidateSet.addAll(questionRepository.findByKnowledgePointIdIn(kpRelevance.keySet()));
            // 4.2 多标签候选（question_knowledge_point 表匹配）
            List<Long> qIdsFromTags = qkpRepository.findQuestionIdsByKnowledgePointIdIn(kpRelevance.keySet());
            if (!qIdsFromTags.isEmpty()) {
                candidateSet.addAll(questionRepository.findAllById(qIdsFromTags));
            }
        }
        List<Question> candidates = new ArrayList<>(candidateSet);

        // 4.3 预加载所有候选题的多标签（批量，避免 N+1）
        Set<Long> userKpSet = masteryList.stream()
                .map(KnowledgeMastery::getKnowledgePointId).collect(Collectors.toSet());
        Map<Long, Set<Long>> questionTagMap = new HashMap<>();
        if (!candidates.isEmpty()) {
            List<Long> qIds = candidates.stream().map(Question::getId).collect(Collectors.toList());
            List<QuestionKnowledgePoint> allQkps = qkpRepository.findByQuestionIdIn(qIds);
            for (QuestionKnowledgePoint qkp : allQkps) {
                questionTagMap.computeIfAbsent(qkp.getQuestionId(), k -> new HashSet<>())
                        .add(qkp.getKnowledgePointId());
            }
        }

        List<QuestionRecommendationDTO> scored = new ArrayList<>();
        for (Question q : candidates) {
            double[] rel = kpRelevance.get(q.getKnowledgePointId());
            if (rel == null) continue;
            double relevance = rel[0];
            double masteryLevel = rel[1];  // 该知识点的用户掌握度

            // 薄弱度：无记录按 0.5 中性
            double weakness = (100.0 - masteryLevel) / 100.0;

            // 难度匹配
            int target = targetDifficulty.getOrDefault(q.getSubject(), 3);
            int diff = q.getDifficulty() != null ? q.getDifficulty() : 3;
            double difficultyFit = 1.0 - Math.abs(diff - target) / 4.0;
            if (difficultyFit < 0) difficultyFit = 0;

            // 新鲜度
            UserQuestion uq = doneQuestionMap.get(q.getId());
            double freshness;
            String category;
            if (uq == null) {
                freshness = 1.0;
                category = relevance >= 1.0 ? "weak" : (relevance >= 0.7 ? "sibling" : "related");
            } else {
                int total = uq.getTotalAttempts() != null ? uq.getTotalAttempts() : 0;
                int correct = uq.getCorrectCount() != null ? uq.getCorrectCount() : 0;
                if (total == 0) {
                    freshness = 0.9;  // 在题库中但没尝试过
                    category = "revisit";
                } else {
                    double acc = (double) correct / total;
                    if (acc < 0.5) {
                        freshness = 0.6;
                        category = "revisit";
                    } else {
                        freshness = 0.15;
                        category = "revisit";
                    }
                }
            }

            // 多标签 Jaccard：题目标签集 ∩ 用户历史标签集 / 并集
            Set<Long> qTags = questionTagMap.getOrDefault(q.getId(), Collections.emptySet());
            double jaccard = 0.0;
            if (!qTags.isEmpty() && !userKpSet.isEmpty()) {
                Set<Long> inter = new HashSet<>(qTags);
                inter.retainAll(userKpSet);
                Set<Long> union = new HashSet<>(qTags);
                union.addAll(userKpSet);
                jaccard = (double) inter.size() / Math.max(1, union.size());
            }

            double score = W_WEAKNESS * weakness
                    + W_RELEVANCE * relevance
                    + W_JACCARD * jaccard
                    + W_DIFFICULTY * difficultyFit
                    + W_FRESHNESS * freshness;

            QuestionRecommendationDTO dto = new QuestionRecommendationDTO();
            dto.setQuestion(q);
            dto.setScore(Math.round(score * 10000) / 10000.0);
            dto.setCategory(category);
            dto.setKnowledgePointName(kpName.get(q.getKnowledgePointId()));
            dto.setReason(buildReason(category, kpName.get(q.getKnowledgePointId()), masteryLevel, uq));
            scored.add(dto);
        }

        // ── 5. 按分数排序 + 类别多样性去重（同一知识点最多取 2 条） ──
        scored.sort(Comparator.comparing(QuestionRecommendationDTO::getScore).reversed());

        Map<Long, Integer> kpCount = new HashMap<>();
        List<QuestionRecommendationDTO> result = new ArrayList<>();
        for (QuestionRecommendationDTO r : scored) {
            Long kpId = r.getQuestion().getKnowledgePointId();
            int cnt = kpCount.getOrDefault(kpId, 0);
            if (cnt >= 2) continue;  // 同一知识点最多 2 条，保证覆盖面
            kpCount.put(kpId, cnt + 1);
            result.add(r);
            if (result.size() >= limit) break;
        }

        // 候选不足时用冷启动补齐
        if (result.size() < limit) {
            Set<Long> existing = result.stream()
                    .map(r -> r.getQuestion().getId()).collect(Collectors.toSet());
            for (QuestionRecommendationDTO cs : coldStart(limit - result.size())) {
                if (!existing.contains(cs.getQuestion().getId())) {
                    result.add(cs);
                }
            }
        }

        return result;
    }

    /** 冷启动：各科目取中等难度题目。 */
    private List<QuestionRecommendationDTO> coldStart(int limit) {
        String[] subjects = {"政治", "英语", "数学", "专业课"};
        int perSubject = Math.max(1, limit / subjects.length);
        List<QuestionRecommendationDTO> list = new ArrayList<>();
        for (String subj : subjects) {
            for (Question q : questionRepository.findColdStartBySubject(subj, perSubject)) {
                QuestionRecommendationDTO dto = new QuestionRecommendationDTO();
                dto.setQuestion(q);
                dto.setCategory("cold_start");
                dto.setScore(0.5);
                dto.setReason("入门推荐：帮你熟悉 " + subj + " 的中等难度题目");
                list.add(dto);
                if (list.size() >= limit) return list;
            }
        }
        return list;
    }

    /** 根据用户在各科目的整体正确率推断目标难度。 */
    private Map<String, Integer> computeTargetDifficulty(List<UserQuestion> userQuestions) {
        Map<String, int[]> agg = new HashMap<>(); // [totalAttempts, totalCorrect]
        for (UserQuestion uq : userQuestions) {
            if (uq.getQuestion() == null) continue;
            String subj = uq.getQuestion().getSubject();
            int[] v = agg.computeIfAbsent(subj, k -> new int[2]);
            v[0] += uq.getTotalAttempts() != null ? uq.getTotalAttempts() : 0;
            v[1] += uq.getCorrectCount() != null ? uq.getCorrectCount() : 0;
        }
        Map<String, Integer> target = new HashMap<>();
        for (Map.Entry<String, int[]> e : agg.entrySet()) {
            int total = e.getValue()[0], correct = e.getValue()[1];
            if (total == 0) { target.put(e.getKey(), 3); continue; }
            double acc = (double) correct / total;
            if (acc >= 0.75) target.put(e.getKey(), 4);
            else if (acc >= 0.45) target.put(e.getKey(), 3);
            else target.put(e.getKey(), 2);
        }
        return target;
    }

    /** 生成面向用户的推荐理由。 */
    private String buildReason(String category, String kpName, double mastery, UserQuestion uq) {
        String safeName = kpName != null ? kpName : "相关知识点";
        switch (category) {
            case "weak":
                return String.format("针对薄弱知识点「%s」（当前掌握度 %.0f%%）", safeName, mastery);
            case "sibling":
                return String.format("拓展相关考点：与你常见考点同属一类「%s」", safeName);
            case "related":
                return String.format("延伸知识点「%s」，帮助建立知识关联", safeName);
            case "revisit":
                if (uq != null && uq.getTotalAttempts() != null && uq.getTotalAttempts() > 0) {
                    int correct = uq.getCorrectCount() != null ? uq.getCorrectCount() : 0;
                    int total = uq.getTotalAttempts();
                    int rate = (int) Math.round(100.0 * correct / total);
                    return String.format("错题回顾：「%s」正确率 %d%%，建议再做一遍", safeName, rate);
                }
                return String.format("错题回顾：「%s」", safeName);
            case "cold_start":
                return "入门推荐";
            default:
                return "为你挑选的题目";
        }
    }
}
