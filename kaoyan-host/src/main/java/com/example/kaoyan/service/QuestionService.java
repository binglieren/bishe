package com.example.kaoyan.service;

import com.example.kaoyan.dto.AnswerRequest;
import com.example.kaoyan.dto.ImageAnswerRequest;
import com.example.kaoyan.dto.QuestionDTO;
import com.example.kaoyan.entity.*;
import com.example.kaoyan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题库服务
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final WrongAnswerRepository wrongAnswerRepository;
    private final KnowledgeMasteryRepository knowledgeMasteryRepository;
    private final UserQuestionRepository userQuestionRepository;
    private final com.example.kaoyan.repository.QuestionKnowledgePointRepository questionKnowledgePointRepository;
    private final LlmService llmService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建题目
     */
    @Transactional
    public Question createQuestion(QuestionDTO dto) {
        Question question = new Question();
        question.setSubject(normalizeSubject(dto.getSubject()));
        question.setType(dto.getType());
        question.setDifficulty(dto.getDifficulty());
        question.setContent(dto.getContent());
        question.setAnswer(dto.getAnswer());
        question.setAnalysis(dto.getAnalysis());
        question.setKnowledgePointId(dto.getKnowledgePointId());
        question.setYear(dto.getYear());
        question.setSource(dto.getSource());
        questionRepository.save(question);

        // 保存选项
        if (dto.getOptions() != null) {
            for (QuestionDTO.OptionDTO optDto : dto.getOptions()) {
                QuestionOption option = new QuestionOption();
                option.setQuestionId(question.getId());
                option.setLabel(optDto.getLabel());
                option.setContent(optDto.getContent());
                option.setIsCorrect(optDto.getIsCorrect());
                questionOptionRepository.save(option);
            }
        }

        return question;
    }

    /**
     * 分页查询题目（支持多条件筛选）
     */
    public Page<Question> getQuestions(String subject, String type, Integer difficulty,
                                       Long knowledgePointId, Integer year,
                                       int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        if (knowledgePointId != null) {
            return questionRepository.findByKnowledgePointId(knowledgePointId, pageable);
        }
        if (subject != null && year != null) {
            return questionRepository.findBySubjectAndYear(subject, year, pageable);
        }
        if (subject != null && type != null) {
            return questionRepository.findBySubjectAndType(subject, type, pageable);
        }
        if (subject != null && difficulty != null) {
            return questionRepository.findBySubjectAndDifficulty(subject, difficulty, pageable);
        }
        if (subject != null) {
            return questionRepository.findBySubject(subject, pageable);
        }
        return questionRepository.findAll(pageable);
    }

    /**
     * 获取题目详情（含选项）
     */
    public Question getQuestionDetail(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在"));
    }

    /**
     * 提交答案并判断对错
     */
    @Transactional
    public Map<String, Object> submitAnswer(Long userId, AnswerRequest request) {
        Question question = questionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new IllegalArgumentException("题目不存在"));

        if ("__viewed__".equals(request.getUserAnswer())) {
            Map<String, Object> result = new HashMap<>();
            result.put("isCorrect", true);
            result.put("correctAnswer", question.getAnswer());
            result.put("analysis", question.getAnalysis());
            result.put("viewed", true);
            return result;
        }

        boolean isCorrect = checkAnswer(question, request.getUserAnswer());

        Map<String, Object> result = new HashMap<>();
        result.put("isCorrect", isCorrect);
        result.put("correctAnswer", question.getAnswer());
        result.put("analysis", question.getAnalysis());

        // 如果答错，加入错题本
        if (!isCorrect && !wrongAnswerRepository.existsByUserIdAndQuestionId(userId, question.getId())) {
            WrongAnswerRecord record = new WrongAnswerRecord();
            record.setUserId(userId);
            record.setQuestionId(question.getId());
            record.setUserAnswer(request.getUserAnswer());
            wrongAnswerRepository.save(record);
        }

        // 更新知识点掌握度
        if (question.getKnowledgePointId() != null) {
            updateMastery(userId, question.getKnowledgePointId(), isCorrect);
        }

        return result;
    }

    /**
     * 获取错题本
     */
    public Page<WrongAnswerRecord> getWrongAnswers(Long userId, Boolean resolved, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (resolved != null) {
            return wrongAnswerRepository.findByUserIdAndIsResolvedOrderByCreatedAtDesc(userId, resolved, pageable);
        }
        return wrongAnswerRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * 标记错题为已解决
     */
    @Transactional
    public void resolveWrongAnswer(Long wrongAnswerId) {
        WrongAnswerRecord record = wrongAnswerRepository.findById(wrongAnswerId)
                .orElseThrow(() -> new IllegalArgumentException("错题记录不存在"));
        record.setIsResolved(true);
        wrongAnswerRepository.save(record);
    }

    /**
     * 获取知识点列表
     */
    public List<KnowledgePoint> getKnowledgePoints(String subject) {
        if (subject != null) {
            return knowledgePointRepository.findBySubject(subject);
        }
        return knowledgePointRepository.findAll();
    }

    /**
     * 随机出题
     */
    public List<Question> getRandomQuestions(String subject, Long knowledgePointId, int count) {
        Pageable pageable = PageRequest.of(0, count);
        if (knowledgePointId != null) {
            return questionRepository.findRandomByKnowledgePointId(knowledgePointId, pageable);
        }
        return questionRepository.findRandomBySubject(subject, pageable);
    }

    // ===================== 用户个人题库 =====================

    /**
     * 获取用户个人题库列表（含题目详情和做题统计）
     */
    public List<UserQuestion> getUserQuestions(Long userId) {
        return userQuestionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 获取用户积累的知识点列表
     */
    public List<KnowledgePoint> getUserKnowledgePoints(Long userId) {
        return knowledgePointRepository.findKnowledgePointsByUserId(userId);
    }

    /**
     * 记录一次做题并更新 UserQuestion 统计（正确率、上次做题时间）
     */
    @Transactional
    public Map<String, Object> recordAttemptForUserQuestion(Long userId, AnswerRequest request) {
        Map<String, Object> result = submitAnswer(userId, request);

        if (Boolean.TRUE.equals(result.get("viewed"))) {
            return result;
        }

        UserQuestion uq = userQuestionRepository.findByUserIdAndQuestionId(userId, request.getQuestionId())
                .orElseGet(() -> {
                    UserQuestion nu = new UserQuestion();
                    nu.setUserId(userId);
                    nu.setQuestionId(request.getQuestionId());
                    nu.setCorrectCount(0);
                    nu.setTotalAttempts(0);
                    return nu;
                });
        uq.setTotalAttempts((uq.getTotalAttempts() == null ? 0 : uq.getTotalAttempts()) + 1);
        if (Boolean.TRUE.equals(result.get("isCorrect"))) {
            uq.setCorrectCount((uq.getCorrectCount() == null ? 0 : uq.getCorrectCount()) + 1);
        }
        uq.setLastAttemptAt(LocalDateTime.now());
        userQuestionRepository.save(uq);
        return result;
    }

    /**
     * 获取相似题目推荐。
     *
     * 流程：
     *   A. 向量召回：用 pgvector 余弦距离取 top-N（N = limit * 3）
     *   B. 标签召回：取和本题多标签有交集的题目
     *   C. 合并候选，按"向量相似度 0.6 + Jaccard 标签重合度 0.4"重排
     *   D. 若向量不可用则降级到标签召回 → 再降级到单主知识点召回
     */
    public List<Question> getSimilarQuestions(Long questionId, int limit) {
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在"));

        // 本题的标签集合
        List<com.example.kaoyan.entity.QuestionKnowledgePoint> myTags =
                questionKnowledgePointRepository.findByQuestionId(questionId);
        java.util.Set<Long> myTagIds = myTags.stream()
                .map(com.example.kaoyan.entity.QuestionKnowledgePoint::getKnowledgePointId)
                .collect(java.util.stream.Collectors.toSet());

        // A. 向量召回
        List<Question> byVector = java.util.Collections.emptyList();
        try {
            byVector = questionRepository.findSimilarByVector(questionId,
                    "(SELECT embedding::text FROM question WHERE id = " + questionId + ")",
                    limit * 3);
        } catch (Exception ignored) { /* 向量不可用 */ }

        // B. 标签召回
        List<Question> byTag = java.util.Collections.emptyList();
        if (!myTagIds.isEmpty()) {
            java.util.List<Long> qIds = questionKnowledgePointRepository
                    .findQuestionIdsByKnowledgePointIdIn(myTagIds);
            qIds.remove(questionId);
            if (!qIds.isEmpty()) {
                byTag = questionRepository.findAllById(qIds);
            }
        }

        // C. 合并 + 重排
        java.util.Map<Long, Question> merged = new java.util.LinkedHashMap<>();
        for (Question x : byVector) merged.putIfAbsent(x.getId(), x);
        for (Question x : byTag)    merged.putIfAbsent(x.getId(), x);

        if (merged.isEmpty()) {
            // D. 降级到单主知识点
            if (q.getKnowledgePointId() != null) {
                Pageable pageable = PageRequest.of(0, limit);
                return questionRepository.findByKnowledgePointIdAndIdNot(
                        q.getKnowledgePointId(), questionId, pageable);
            }
            return List.of();
        }

        // 向量顺序给相似度分（越前越高）
        java.util.Map<Long, Double> vectorScore = new java.util.HashMap<>();
        for (int i = 0; i < byVector.size(); i++) {
            vectorScore.put(byVector.get(i).getId(), 1.0 - (double) i / byVector.size());
        }

        // 预加载候选标签
        java.util.List<Long> candidateIds = new java.util.ArrayList<>(merged.keySet());
        java.util.Map<Long, java.util.Set<Long>> candTags = new java.util.HashMap<>();
        for (com.example.kaoyan.entity.QuestionKnowledgePoint qkp :
                questionKnowledgePointRepository.findByQuestionIdIn(candidateIds)) {
            candTags.computeIfAbsent(qkp.getQuestionId(), k -> new java.util.HashSet<>())
                    .add(qkp.getKnowledgePointId());
        }

        // 综合打分
        return merged.values().stream()
                .map(x -> {
                    double vec = vectorScore.getOrDefault(x.getId(), 0.0);
                    java.util.Set<Long> tags = candTags.getOrDefault(x.getId(), java.util.Collections.emptySet());
                    double jac = 0.0;
                    if (!tags.isEmpty() && !myTagIds.isEmpty()) {
                        java.util.Set<Long> inter = new java.util.HashSet<>(tags);
                        inter.retainAll(myTagIds);
                        java.util.Set<Long> union = new java.util.HashSet<>(tags);
                        union.addAll(myTagIds);
                        jac = (double) inter.size() / Math.max(1, union.size());
                    }
                    double composite = vec * 0.6 + jac * 0.4;
                    return new AbstractMap.SimpleEntry<>(x, composite);
                })
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }

    // ======================================================

    /**
     * 题目分类归一化：保存时统一收敛到 [数学, 英语, 专业课] 三个分类。
     *   - "数学" → 数学
     *   - "英语" → 英语
     *   - 其他（含 政治/历史/原专业课/未识别）→ 专业课
     *
     * 题库筛选与统计基于此分类，新题保存与从图片提取均会经过此函数。
     */
    public static String normalizeSubject(String raw) {
        if (raw == null) return "专业课";
        String s = raw.trim();
        if (s.isEmpty()) return "专业课";
        if (s.contains("数学") || s.equalsIgnoreCase("math") || s.equalsIgnoreCase("maths")) return "数学";
        if (s.contains("英语") || s.equalsIgnoreCase("english") || s.equalsIgnoreCase("eng")) return "英语";
        // 政治/专业课/其他统一归为"专业课"
        return "专业课";
    }

    private boolean checkAnswer(Question question, String userAnswer) {
        if (userAnswer == null) return false;
        String type = question.getType();
        if ("单选".equals(type) || "多选".equals(type)) {
            // 多选题：排序后比较（AB == BA）
            String normalizedAnswer = question.getAnswer().trim().toUpperCase()
                    .chars().sorted()
                    .collect(StringBuilder::new, (sb, c) -> sb.append((char) c), StringBuilder::append)
                    .toString();
            String normalizedUser = userAnswer.trim().toUpperCase()
                    .chars().sorted()
                    .collect(StringBuilder::new, (sb, c) -> sb.append((char) c), StringBuilder::append)
                    .toString();
            return normalizedAnswer.equals(normalizedUser);
        }
        return question.getAnswer().trim().equalsIgnoreCase(userAnswer.trim());
    }

    private void updateMastery(Long userId, Long knowledgePointId, boolean isCorrect) {
        KnowledgeMastery mastery = knowledgeMasteryRepository
                .findByUserIdAndKnowledgePointId(userId, knowledgePointId)
                .orElseGet(() -> {
                    KnowledgeMastery m = new KnowledgeMastery();
                    m.setUserId(userId);
                    m.setKnowledgePointId(knowledgePointId);
                    return m;
                });

        mastery.setTotalCount(mastery.getTotalCount() + 1);
        if (isCorrect) {
            mastery.setCorrectCount(mastery.getCorrectCount() + 1);
        }
        mastery.setMasteryLevel(
                new java.math.BigDecimal(mastery.getCorrectCount())
                        .divide(new java.math.BigDecimal(mastery.getTotalCount()), 2, java.math.RoundingMode.HALF_UP)
                        .multiply(new java.math.BigDecimal(100))
        );
        knowledgeMasteryRepository.save(mastery);
    }

    /**
     * 简答题提交手写图片答案，经 LLM 判定对错
     */
    public Map<String, Object> submitImageAnswer(Long userId, ImageAnswerRequest request) {
        Question question = questionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new IllegalArgumentException("题目不存在"));

        String transcribed = null;
        Map<String, Object> evaluation;

        try {
            if (request.getImageBase64() != null && !request.getImageBase64().isBlank()) {
                transcribed = llmService.transcribeHandwriting(request.getImageBase64(), userId);
                transcribed = transcribed != null ? transcribed.trim() : "";
            } else {
                transcribed = request.getUserAnswer() != null ? request.getUserAnswer() : "";
            }

            if (transcribed.isEmpty() || "[无法识别]".equals(transcribed)) {
                evaluation = new HashMap<>();
                evaluation.put("isCorrect", false);
                evaluation.put("score", 0);
                evaluation.put("feedback", "未能识别手写内容，请确认图片清晰并重新上传");
            } else {
                evaluation = llmService.evaluateShortAnswer(
                        question.getContent(), question.getAnswer(), transcribed,
                        request.getUserAnswer(), userId);
            }
        } catch (Exception e) {
            evaluation = new HashMap<>();
            evaluation.put("isCorrect", false);
            evaluation.put("score", 0);
            evaluation.put("feedback", "LLM 判定服务暂不可用，请稍后重试");
        }

        final String finalTranscribed = transcribed;
        final boolean isCorrect = Boolean.TRUE.equals(evaluation.get("isCorrect"));

        transactionTemplate.executeWithoutResult(status -> {
            if (!isCorrect && !wrongAnswerRepository.existsByUserIdAndQuestionId(userId, question.getId())) {
                WrongAnswerRecord record = new WrongAnswerRecord();
                record.setUserId(userId);
                record.setQuestionId(question.getId());
                record.setUserAnswer(finalTranscribed != null ? finalTranscribed : "");
                wrongAnswerRepository.save(record);
            }

            if (question.getKnowledgePointId() != null) {
                updateMastery(userId, question.getKnowledgePointId(), isCorrect);
            }

            UserQuestion uq = userQuestionRepository.findByUserIdAndQuestionId(userId, question.getId())
                    .orElseGet(() -> {
                        UserQuestion nu = new UserQuestion();
                        nu.setUserId(userId);
                        nu.setQuestionId(question.getId());
                        nu.setCorrectCount(0);
                        nu.setTotalAttempts(0);
                        return nu;
                    });
            uq.setTotalAttempts((uq.getTotalAttempts() == null ? 0 : uq.getTotalAttempts()) + 1);
            if (isCorrect) {
                uq.setCorrectCount((uq.getCorrectCount() == null ? 0 : uq.getCorrectCount()) + 1);
            }
            uq.setLastAttemptAt(LocalDateTime.now());
            userQuestionRepository.save(uq);
        });

        Map<String, Object> result = new HashMap<>(evaluation);
        result.put("correctAnswer", question.getAnswer());
        result.put("analysis", question.getAnalysis());
        return result;
    }
}
