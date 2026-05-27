package com.example.kaoyan.service;

import com.example.kaoyan.entity.KnowledgePoint;
import com.example.kaoyan.entity.Question;
import com.example.kaoyan.entity.UserQuestion;
import com.example.kaoyan.repository.KnowledgePointRepository;
import com.example.kaoyan.repository.QuestionRepository;
import com.example.kaoyan.repository.UserQuestionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionInternalService {

    private final QuestionRepository questionRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final UserQuestionRepository userQuestionRepository;

    @PersistenceContext
    private EntityManager em;

    public List<Map<String, Object>> searchQuestions(
            String subject, String type, Integer difficulty, Long kpId) {

        List<Question> questions = questionRepository.searchQuestions(subject, type, difficulty, kpId);
        return questions.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public List<Map<String, Object>> recommendQuestions(Long userId, int count) {
        List<KnowledgePoint> kps = knowledgePointRepository.findKnowledgePointsByUserId(userId);
        List<Question> result = new ArrayList<>();

        if (!kps.isEmpty()) {
            List<Long> kpIds = kps.stream().map(KnowledgePoint::getId).collect(Collectors.toList());
            List<Question> allQuestions = questionRepository.findByKnowledgePointIdIn(kpIds);
            List<UserQuestion> userQuestions = userQuestionRepository.findByUserIdOrderByCreatedAtDesc(userId);
            Set<Long> doneIds = userQuestions.stream()
                    .map(UserQuestion::getQuestionId)
                    .collect(Collectors.toSet());

            List<Question> newQuestions = allQuestions.stream()
                    .filter(q -> !doneIds.contains(q.getId()))
                    .sorted(Comparator.comparingInt(Question::getDifficulty))
                    .limit(count)
                    .collect(Collectors.toList());
            result.addAll(newQuestions);
        }

        if (result.size() < count && !kps.isEmpty()) {
            String subject = kps.get(0).getSubject();
            List<Question> coldStart = questionRepository.findColdStartBySubject(subject, count - result.size());
            result.addAll(coldStart);
        }

        result = result.stream().limit(count).collect(Collectors.toList());
        return result.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getSimilarQuestions(Long questionId) {
        String vectorStr = null;
        try {
            vectorStr = (String) em.createNativeQuery(
                    "SELECT embedding::text FROM question WHERE id = :id")
                    .setParameter("id", questionId)
                    .getSingleResult();
        } catch (Exception e) {
            log.warn("No embedding found for question {}, falling back to KP-based similarity", questionId);
        }

        if (vectorStr != null) {
            List<Question> similar = questionRepository.findSimilarByVector(questionId, vectorStr, 5);
            return similar.stream().map(this::toSummary).collect(Collectors.toList());
        }

        Question q = questionRepository.findById(questionId).orElse(null);
        if (q != null && q.getKnowledgePointId() != null) {
            List<Question> similar = questionRepository.findByKnowledgePointIdAndIdNot(
                    q.getKnowledgePointId(), questionId,
                    org.springframework.data.domain.PageRequest.of(0, 5));
            return similar.stream().map(this::toSummary).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    public Map<String, Object> extractQuestionFromAnswer(String answerText, Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "pending");
        result.put("message", "LLM extraction is not yet implemented.");
        result.put("answerText", answerText);
        result.put("userId", userId);
        return result;
    }

    private Map<String, Object> toSummary(Question q) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", q.getId());
        map.put("content", q.getContent());
        map.put("type", q.getType());
        map.put("difficulty", q.getDifficulty());
        map.put("subject", q.getSubject());
        return map;
    }
}
