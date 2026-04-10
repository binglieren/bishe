package com.example.kaoyan.service;

import com.example.kaoyan.dto.ExamDTO;
import com.example.kaoyan.dto.SubmitExamRequest;
import com.example.kaoyan.entity.*;
import com.example.kaoyan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 考试服务
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamRecordRepository examRecordRepository;
    private final ExamAnswerRepository examAnswerRepository;
    private final QuestionRepository questionRepository;
    private final KnowledgeMasteryRepository knowledgeMasteryRepository;

    /**
     * 创建考试
     */
    @Transactional
    public Exam createExam(ExamDTO dto) {
        Exam exam = new Exam();
        exam.setTitle(dto.getTitle());
        exam.setSubject(dto.getSubject());
        exam.setDurationMinutes(dto.getDurationMinutes());
        exam.setTotalScore(dto.getTotalScore());
        exam.setDescription(dto.getDescription());
        examRepository.save(exam);

        if (dto.getQuestions() != null) {
            int order = 0;
            for (ExamDTO.ExamQuestionItem item : dto.getQuestions()) {
                ExamQuestion eq = new ExamQuestion();
                eq.setExamId(exam.getId());
                eq.setQuestionId(item.getQuestionId());
                eq.setScore(item.getScore());
                eq.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : order++);
                examQuestionRepository.save(eq);
            }
        }

        return exam;
    }

    /**
     * 获取考试列表
     */
    public List<Exam> getExams(String subject) {
        if (subject != null) {
            return examRepository.findBySubject(subject);
        }
        return examRepository.findAll();
    }

    /**
     * 获取考试详情（含题目列表）
     */
    public Map<String, Object> getExamDetail(Long examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("考试不存在"));
        List<ExamQuestion> questions = examQuestionRepository.findByExamIdOrderBySortOrder(examId);

        Map<String, Object> result = new HashMap<>();
        result.put("exam", exam);
        result.put("questions", questions);
        return result;
    }

    /**
     * 开始考试
     */
    @Transactional
    public ExamRecord startExam(Long userId, Long examId) {
        // 检查是否有未完成的考试
        Optional<ExamRecord> existing = examRecordRepository
                .findByUserIdAndExamIdAndStatus(userId, examId, "IN_PROGRESS");
        if (existing.isPresent()) {
            return existing.get();
        }

        ExamRecord record = new ExamRecord();
        record.setUserId(userId);
        record.setExamId(examId);
        record.setStartTime(LocalDateTime.now());
        record.setStatus("IN_PROGRESS");
        return examRecordRepository.save(record);
    }

    /**
     * 提交考试答案并评分
     */
    @Transactional
    public Map<String, Object> submitExam(Long userId, SubmitExamRequest request) {
        ExamRecord record = examRecordRepository.findById(request.getRecordId())
                .orElseThrow(() -> new IllegalArgumentException("考试记录不存在"));

        if (!"IN_PROGRESS".equals(record.getStatus())) {
            throw new IllegalArgumentException("考试已结束");
        }

        // 获取考试题目及分值
        List<ExamQuestion> examQuestions = examQuestionRepository.findByExamIdOrderBySortOrder(record.getExamId());
        Map<Long, Integer> questionScoreMap = examQuestions.stream()
                .collect(Collectors.toMap(ExamQuestion::getQuestionId, ExamQuestion::getScore));

        int totalScore = 0;
        int correctCount = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (SubmitExamRequest.ExamAnswerItem answerItem : request.getAnswers()) {
            Question question = questionRepository.findById(answerItem.getQuestionId()).orElse(null);
            if (question == null) continue;

            boolean isCorrect = question.getAnswer().trim().equalsIgnoreCase(
                    answerItem.getUserAnswer() != null ? answerItem.getUserAnswer().trim() : "");

            int score = isCorrect ? questionScoreMap.getOrDefault(question.getId(), 0) : 0;
            totalScore += score;
            if (isCorrect) correctCount++;

            ExamAnswer examAnswer = new ExamAnswer();
            examAnswer.setRecordId(record.getId());
            examAnswer.setQuestionId(question.getId());
            examAnswer.setUserAnswer(answerItem.getUserAnswer());
            examAnswer.setIsCorrect(isCorrect);
            examAnswer.setScore(score);
            examAnswerRepository.save(examAnswer);

            // 更新知识点掌握度
            if (question.getKnowledgePointId() != null) {
                updateMastery(userId, question.getKnowledgePointId(), isCorrect);
            }

            Map<String, Object> detail = new HashMap<>();
            detail.put("questionId", question.getId());
            detail.put("isCorrect", isCorrect);
            detail.put("score", score);
            detail.put("correctAnswer", question.getAnswer());
            detail.put("analysis", question.getAnalysis());
            details.add(detail);
        }

        // 更新考试记录
        record.setScore(totalScore);
        record.setEndTime(LocalDateTime.now());
        record.setStatus("COMPLETED");
        examRecordRepository.save(record);

        // 构建结果
        Exam exam = examRepository.findById(record.getExamId()).orElse(null);
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", record.getId());
        result.put("totalScore", totalScore);
        result.put("fullScore", exam != null ? exam.getTotalScore() : 0);
        result.put("correctCount", correctCount);
        result.put("totalQuestions", request.getAnswers().size());
        result.put("accuracy", request.getAnswers().isEmpty() ? 0 :
                (correctCount * 100.0 / request.getAnswers().size()));
        result.put("details", details);

        return result;
    }

    /**
     * 获取用户的考试记录
     */
    public List<ExamRecord> getUserExamRecords(Long userId) {
        return examRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 获取考试结果详情
     */
    public Map<String, Object> getExamResult(Long recordId) {
        ExamRecord record = examRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("考试记录不存在"));
        Exam exam = examRepository.findById(record.getExamId()).orElse(null);
        List<ExamAnswer> answers = examAnswerRepository.findByRecordId(recordId);

        Map<String, Object> result = new HashMap<>();
        result.put("record", record);
        result.put("exam", exam);
        result.put("answers", answers);
        return result;
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
                new BigDecimal(mastery.getCorrectCount())
                        .divide(new BigDecimal(mastery.getTotalCount()), 2, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(100))
        );
        knowledgeMasteryRepository.save(mastery);
    }
}
