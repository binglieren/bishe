package com.example.kaoyan.service;

import com.example.kaoyan.entity.*;
import com.example.kaoyan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理员服务
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final ExamRepository examRepository;
    private final ExamRecordRepository examRecordRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final PasswordEncoder passwordEncoder;

    // ==================== 数据统计 ====================

    /**
     * 获取系统概览统计
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("userCount", userRepository.count());
        stats.put("questionCount", questionRepository.count());
        stats.put("examCount", examRepository.count());
        stats.put("knowledgePointCount", knowledgePointRepository.count());
        stats.put("documentCount", documentRepository.count());
        stats.put("chatSessionCount", chatSessionRepository.count());
        stats.put("examRecordCount", examRecordRepository.count());
        return stats;
    }

    // ==================== 用户管理 ====================

    /**
     * 分页查询用户列表
     */
    public Page<User> getUserList(int page, int size) {
        return userRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * 修改用户角色
     */
    @Transactional
    public User updateUserRole(Long userId, String role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setRole(role);
        return userRepository.save(user);
    }

    /**
     * 重置用户密码
     */
    @Transactional
    public void resetUserPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("用户不存在");
        }
        userRepository.deleteById(userId);
    }

    // ==================== 题目管理 ====================

    /**
     * 分页查询题目列表
     */
    public Page<Question> getQuestionList(int page, int size) {
        return questionRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * 按科目分页查询题目
     */
    public Page<Question> getQuestionListBySubject(String subject, int page, int size) {
        return questionRepository.findBySubject(subject,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * 新增题目
     */
    @Transactional
    public Question createQuestion(Question question) {
        return questionRepository.save(question);
    }

    /**
     * 修改题目
     */
    @Transactional
    public Question updateQuestion(Long questionId, Question updated) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在"));
        question.setSubject(updated.getSubject());
        question.setType(updated.getType());
        question.setDifficulty(updated.getDifficulty());
        question.setContent(updated.getContent());
        question.setAnswer(updated.getAnswer());
        question.setAnalysis(updated.getAnalysis());
        question.setKnowledgePointId(updated.getKnowledgePointId());
        question.setYear(updated.getYear());
        question.setSource(updated.getSource());
        return questionRepository.save(question);
    }

    /**
     * 删除题目
     */
    @Transactional
    public void deleteQuestion(Long questionId) {
        if (!questionRepository.existsById(questionId)) {
            throw new IllegalArgumentException("题目不存在");
        }
        questionRepository.deleteById(questionId);
    }

    // ==================== 考试管理 ====================

    /**
     * 分页查询考试列表
     */
    public Page<Exam> getExamList(int page, int size) {
        return examRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * 新增考试
     */
    @Transactional
    public Exam createExam(Exam exam) {
        return examRepository.save(exam);
    }

    /**
     * 修改考试
     */
    @Transactional
    public Exam updateExam(Long examId, Exam updated) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("考试不存在"));
        exam.setTitle(updated.getTitle());
        exam.setSubject(updated.getSubject());
        exam.setDurationMinutes(updated.getDurationMinutes());
        exam.setTotalScore(updated.getTotalScore());
        exam.setDescription(updated.getDescription());
        return examRepository.save(exam);
    }

    /**
     * 删除考试
     */
    @Transactional
    public void deleteExam(Long examId) {
        if (!examRepository.existsById(examId)) {
            throw new IllegalArgumentException("考试不存在");
        }
        examRepository.deleteById(examId);
    }

    // ==================== 知识点管理 ====================

    /**
     * 分页查询知识点
     */
    public Page<KnowledgePoint> getKnowledgePointList(int page, int size) {
        return knowledgePointRepository.findAll(
                PageRequest.of(page, size, Sort.by("subject", "sortOrder")));
    }

    /**
     * 新增知识点
     */
    @Transactional
    public KnowledgePoint createKnowledgePoint(KnowledgePoint kp) {
        return knowledgePointRepository.save(kp);
    }

    /**
     * 修改知识点
     */
    @Transactional
    public KnowledgePoint updateKnowledgePoint(Long kpId, KnowledgePoint updated) {
        KnowledgePoint kp = knowledgePointRepository.findById(kpId)
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在"));
        kp.setSubject(updated.getSubject());
        kp.setName(updated.getName());
        kp.setParentId(updated.getParentId());
        kp.setSortOrder(updated.getSortOrder());
        return knowledgePointRepository.save(kp);
    }

    /**
     * 删除知识点
     */
    @Transactional
    public void deleteKnowledgePoint(Long kpId) {
        if (!knowledgePointRepository.existsById(kpId)) {
            throw new IllegalArgumentException("知识点不存在");
        }
        knowledgePointRepository.deleteById(kpId);
    }
}
