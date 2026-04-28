package com.example.kaoyan.service;

import com.example.kaoyan.dto.*;
import com.example.kaoyan.entity.KnowledgeMastery;
import com.example.kaoyan.entity.KnowledgePoint;
import com.example.kaoyan.entity.KnowledgePointFocus;
import com.example.kaoyan.entity.Question;
import com.example.kaoyan.entity.UserProfile;
import com.example.kaoyan.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱核心服务。
 *
 * 职责：
 *   1. 构建图（节点 + 边） — buildGraph(userId, subject)
 *   2. 节点详情 — getKpDetail(userId, kpId)
 *   3. AI 学习诊断 — diagnose(userId, subject)
 *   4. 收藏管理 — toggleFocus
 *   5. 取专项练习题列表 — getKpPracticeQuestionIds
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    // mastery 等级阈值
    private static final double LEVEL_STRONG_MIN       = 0.8;
    private static final double LEVEL_INTERMEDIATE_MIN = 0.5;
    private static final int    WEAK_MIN_ATTEMPTS      = 3;

    private final KnowledgePointRepository kpRepo;
    private final KnowledgeMasteryRepository masteryRepo;
    private final QuestionKnowledgePointRepository qkpRepo;
    private final KnowledgePointFocusRepository focusRepo;
    private final WrongAnswerRepository wrongRepo;
    private final QuestionRepository questionRepo;
    private final UserProfileRepository profileRepo;
    private final LlmService llmService;

    // ==================================================
    //  图谱构建
    // ==================================================

    public GraphResponseDTO buildGraph(Long userId, String subject) {
        // 1. 取所有 KP（按 subject）
        List<KnowledgePoint> allKps = (subject == null || subject.isBlank())
                ? kpRepo.findAll()
                : kpRepo.findBySubject(subject);
        if (allKps.isEmpty()) {
            return GraphResponseDTO.builder()
                    .subject(subject)
                    .nodes(Collections.emptyList())
                    .edges(Collections.emptyList())
                    .stats(zeroStats())
                    .build();
        }

        // 2. 取该用户在这些 KP 上的 mastery 数据
        Set<Long> kpIds = allKps.stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
        Map<Long, KnowledgeMastery> masteryByKp = masteryRepo.findByUserId(userId).stream()
                .filter(m -> kpIds.contains(m.getKnowledgePointId()))
                .collect(Collectors.toMap(KnowledgeMastery::getKnowledgePointId, m -> m));

        // 3. 每个 KP 的题目数
        Map<Long, Integer> questionCountByKp = qkpRepo.countQuestionsPerKp(subject).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).intValue()
                ));

        // 4. 用户收藏集合
        Set<Long> focusedKps = new HashSet<>(focusRepo.findKpIdsByUserId(userId));

        // 5. 组装节点
        List<GraphNodeDTO> nodes = allKps.stream()
                .map(kp -> toNode(kp, masteryByKp.get(kp.getId()),
                        questionCountByKp.getOrDefault(kp.getId(), 0),
                        focusedKps.contains(kp.getId())))
                .collect(Collectors.toList());

        // 6. 组装边
        List<GraphEdgeDTO> edges = new ArrayList<>();
        // 6a. 父子层级
        for (KnowledgePoint kp : allKps) {
            if (kp.getParentId() != null && kpIds.contains(kp.getParentId())) {
                edges.add(GraphEdgeDTO.builder()
                        .source(kp.getParentId())
                        .target(kp.getId())
                        .type("hierarchy")
                        .weight(1.0)
                        .build());
            }
        }
        // 6b. 共现（同题里出现 ≥ 2 次的两两 KP）
        for (Object[] row : qkpRepo.findCoOccurrenceEdges(subject)) {
            edges.add(GraphEdgeDTO.builder()
                    .source(((Number) row[0]).longValue())
                    .target(((Number) row[1]).longValue())
                    .type("co_occur")
                    .weight(((Number) row[2]).doubleValue())
                    .build());
        }

        // 7. 统计
        GraphResponseDTO.Stats stats = computeStats(nodes);

        return GraphResponseDTO.builder()
                .subject(subject)
                .nodes(nodes)
                .edges(edges)
                .stats(stats)
                .build();
    }

    // ==================================================
    //  节点详情
    // ==================================================

    public KpDetailDTO getKpDetail(Long userId, Long kpId) {
        KnowledgePoint kp = kpRepo.findById(kpId)
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在"));

        KnowledgeMastery mastery = masteryRepo.findByUserIdAndKnowledgePointId(userId, kpId).orElse(null);
        boolean focused = focusRepo.findKpIdsByUserId(userId).contains(kpId);
        String path = buildPath(kp);

        // 错过的题（最近 5 道）
        List<Long> wrongIds = wrongRepo.findWrongQuestionIdsByUserAndKp(userId, kpId, 5);
        List<KpDetailDTO.WrongQuestion> wrongs = questionRepo.findAllById(wrongIds).stream()
                .map(q -> KpDetailDTO.WrongQuestion.builder()
                        .id(q.getId())
                        .content(truncate(q.getContent(), 80))
                        .subject(q.getSubject())
                        .build())
                .collect(Collectors.toList());

        // 相关 KP（共现 top 5）
        List<KpDetailDTO.RelatedKp> related = qkpRepo.findTopRelatedKps(kpId, 5).stream()
                .map(row -> {
                    Long otherId = ((Number) row[0]).longValue();
                    Integer count = ((Number) row[1]).intValue();
                    KnowledgePoint other = kpRepo.findById(otherId).orElse(null);
                    if (other == null) return null;
                    return KpDetailDTO.RelatedKp.builder()
                            .id(otherId)
                            .name(other.getName())
                            .coOccurCount(count)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Integer attempted = mastery != null ? mastery.getTotalCount()   : 0;
        Integer correct   = mastery != null ? mastery.getCorrectCount() : 0;
        Double level      = (mastery != null && mastery.getMasteryLevel() != null)
                ? mastery.getMasteryLevel().doubleValue()
                : null;

        return KpDetailDTO.builder()
                .id(kp.getId())
                .name(kp.getName())
                .subject(kp.getSubject())
                .path(path)
                .attemptedCount(attempted)
                .correctCount(correct)
                .masteryLevel(level)
                .level(classifyLevel(level, attempted))
                .focused(focused)
                .wrongQuestions(wrongs)
                .relatedKps(related)
                .build();
    }

    // ==================================================
    //  AI 学习诊断
    // ==================================================

    public DiagnosisDTO diagnose(Long userId, String subject) {
        GraphResponseDTO graph = buildGraph(userId, subject);
        List<GraphNodeDTO> nodes = graph.getNodes();

        // 按 level 分桶
        List<GraphNodeDTO> weak = nodes.stream()
                .filter(n -> "weak".equals(n.getLevel()))
                .sorted(Comparator.comparing(
                        GraphNodeDTO::getMasteryLevel,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5)
                .toList();

        List<GraphNodeDTO> untouched = nodes.stream()
                .filter(n -> "untouched".equals(n.getLevel()))
                // 题目数多 = 重要程度高，优先列出
                .sorted(Comparator.comparing(
                        GraphNodeDTO::getQuestionCount,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();

        List<GraphNodeDTO> strong = nodes.stream()
                .filter(n -> "strong".equals(n.getLevel()))
                .sorted(Comparator.comparing(
                        GraphNodeDTO::getMasteryLevel,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();

        long attempted = nodes.stream()
                .filter(n -> n.getAttemptedCount() != null && n.getAttemptedCount() > 0)
                .count();

        Integer examDays = computeExamDays(userId);

        // 调用 LLM 生成自然语言建议
        String advice;
        try {
            advice = generateAdvice(subject, weak, untouched, strong, examDays);
        } catch (Exception ex) {
            log.warn("LLM 诊断生成失败，使用默认文案", ex);
            advice = fallbackAdvice(weak, untouched, strong);
        }

        return DiagnosisDTO.builder()
                .subject(subject)
                .examDays(examDays)
                .weakKps(weak)
                .untouchedKps(untouched)
                .strongKps(strong)
                .aiAdvice(advice)
                .attemptedCount((int) attempted)
                .totalCount(nodes.size())
                .build();
    }

    private String generateAdvice(String subject, List<GraphNodeDTO> weak,
                                   List<GraphNodeDTO> untouched, List<GraphNodeDTO> strong,
                                   Integer examDays) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名考研学习教练。用户当前");
        sb.append(subject == null ? "全部科目" : subject + "科目");
        sb.append("的知识点掌握情况：\n\n");
        sb.append("【已掌握的 KP（mastery≥0.8）】\n");
        if (strong.isEmpty()) sb.append("（暂无）\n");
        else strong.forEach(n -> sb.append("- ").append(n.getName())
                .append("（mastery=").append(fmt(n.getMasteryLevel())).append("）\n"));

        sb.append("\n【急需补强的 KP（mastery<0.5 且已答 >3）】\n");
        if (weak.isEmpty()) sb.append("（暂无明显薄弱）\n");
        else weak.forEach(n -> sb.append("- ").append(n.getName())
                .append("（mastery=").append(fmt(n.getMasteryLevel()))
                .append("，已答 ").append(n.getAttemptedCount()).append("）\n"));

        sb.append("\n【还没接触的高频 KP（按题目数 top 10）】\n");
        if (untouched.isEmpty()) sb.append("（覆盖完整）\n");
        else untouched.forEach(n -> sb.append("- ").append(n.getName())
                .append("（关联 ").append(n.getQuestionCount()).append(" 题）\n"));

        if (examDays != null) {
            sb.append("\n距离考试还有 ").append(examDays).append(" 天。\n");
        }

        sb.append("\n请用 2-3 段中文写出：\n");
        sb.append("① 用户当前的优势分析；\n");
        sb.append("② 急需补强的方向（按优先级排序，不超过 3 个）；\n");
        sb.append("③ 建议未来 7 天的学习重点。\n");
        sb.append("不要使用 markdown 标题，不要列表，使用流畅自然的叙述。直接输出建议本体，不要重复用户数据。");

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content",
                        "你是一名经验丰富的考研规划顾问，擅长基于学情数据给出务实可执行的建议。"),
                Map.of("role", "user", "content", sb.toString())
        );
        return llmService.chat(messages);
    }

    private String fallbackAdvice(List<GraphNodeDTO> weak,
                                   List<GraphNodeDTO> untouched,
                                   List<GraphNodeDTO> strong) {
        StringBuilder b = new StringBuilder();
        if (!strong.isEmpty()) {
            b.append("你已经较好地掌握了 ").append(strong.size())
             .append(" 个核心知识点，保持现有节奏即可。");
        } else {
            b.append("当前还没有牢固掌握的知识点，建议先打基础。");
        }
        if (!weak.isEmpty()) {
            b.append("\n\n建议优先突破以下薄弱点：");
            weak.forEach(n -> b.append("「").append(n.getName()).append("」、"));
            b.setLength(b.length() - 1);
            b.append("。每个安排 2-3 天专题练习。");
        }
        if (!untouched.isEmpty()) {
            b.append("\n\n另外，「").append(untouched.get(0).getName())
             .append("」等高频考点尚未练习，请尽快补上。");
        }
        return b.toString();
    }

    // ==================================================
    //  收藏 / 取消收藏
    // ==================================================

    @Transactional
    public boolean toggleFocus(Long userId, Long kpId, boolean focused) {
        if (focused) {
            KnowledgePointFocus f = new KnowledgePointFocus();
            f.setUserId(userId);
            f.setKnowledgePointId(kpId);
            focusRepo.save(f);
        } else {
            focusRepo.deleteByUserIdAndKpId(userId, kpId);
        }
        return focused;
    }

    // ==================================================
    //  专项练习题召回
    // ==================================================

    public List<Long> getKpPracticeQuestionIds(Long kpId, int limit) {
        return qkpRepo.findQuestionIdsByKnowledgePointIdIn(List.of(kpId)).stream()
                .limit(limit)
                .toList();
    }

    // ==================================================
    //  内部工具
    // ==================================================

    private GraphNodeDTO toNode(KnowledgePoint kp, KnowledgeMastery mastery,
                                 int questionCount, boolean focused) {
        Integer attempted = mastery != null ? mastery.getTotalCount()   : 0;
        Integer correct   = mastery != null ? mastery.getCorrectCount() : 0;
        Double level = (mastery != null && mastery.getMasteryLevel() != null)
                ? mastery.getMasteryLevel().doubleValue()
                : null;

        return GraphNodeDTO.builder()
                .id(kp.getId())
                .name(kp.getName())
                .subject(kp.getSubject())
                .parentId(kp.getParentId())
                .questionCount(questionCount)
                .attemptedCount(attempted)
                .correctCount(correct)
                .masteryLevel(level)
                .level(classifyLevel(level, attempted))
                .focused(focused)
                .build();
    }

    private String classifyLevel(Double level, Integer attempted) {
        if (attempted == null || attempted == 0) return "untouched";
        if (level == null) return "intermediate";
        if (level >= LEVEL_STRONG_MIN) return "strong";
        if (level < LEVEL_INTERMEDIATE_MIN && attempted >= WEAK_MIN_ATTEMPTS) return "weak";
        return "intermediate";
    }

    private GraphResponseDTO.Stats computeStats(List<GraphNodeDTO> nodes) {
        Map<String, Long> bucketed = nodes.stream()
                .collect(Collectors.groupingBy(GraphNodeDTO::getLevel, Collectors.counting()));
        return GraphResponseDTO.Stats.builder()
                .total(nodes.size())
                .weak(bucketed.getOrDefault("weak", 0L).intValue())
                .intermediate(bucketed.getOrDefault("intermediate", 0L).intValue())
                .strong(bucketed.getOrDefault("strong", 0L).intValue())
                .untouched(bucketed.getOrDefault("untouched", 0L).intValue())
                .build();
    }

    private GraphResponseDTO.Stats zeroStats() {
        return GraphResponseDTO.Stats.builder()
                .total(0).weak(0).intermediate(0).strong(0).untouched(0).build();
    }

    private String buildPath(KnowledgePoint kp) {
        Deque<String> stack = new ArrayDeque<>();
        KnowledgePoint cur = kp;
        int depth = 0;
        while (cur != null && depth < 10) {
            stack.push(cur.getName());
            if (cur.getParentId() == null) break;
            cur = kpRepo.findById(cur.getParentId()).orElse(null);
            depth++;
        }
        return String.join(" > ", stack);
    }

    private Integer computeExamDays(Long userId) {
        UserProfile profile = profileRepo.findByUserId(userId).orElse(null);
        if (profile == null || profile.getExamDate() == null) return null;
        long days = ChronoUnit.DAYS.between(LocalDate.now(), profile.getExamDate());
        return days < 0 ? 0 : (int) days;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    private static String fmt(Double d) {
        if (d == null) return "—";
        return String.format("%.2f", d);
    }
}
