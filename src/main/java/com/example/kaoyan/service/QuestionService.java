package com.example.kaoyan.service;

import com.example.kaoyan.dto.AnswerRequest;
import com.example.kaoyan.dto.QuestionDTO;
import com.example.kaoyan.entity.*;
import com.example.kaoyan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 创建题目
     */
    @Transactional
    public Question createQuestion(QuestionDTO dto) {
        Question question = new Question();
        question.setSubject(dto.getSubject());
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

    private boolean checkAnswer(Question question, String userAnswer) {
        if (userAnswer == null) return false;
        String type = question.getType();
        if ("单选".equals(type) || "多选".equals(type)) {
            return question.getAnswer().trim().equalsIgnoreCase(userAnswer.trim());
        }
        // 填空和简答用包含判断（简化处理）
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
}
