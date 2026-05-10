package com.example.kaoyan.question;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/mcp/question")
@RequiredArgsConstructor
public class QuestionMcpTools {

    private final QuestionRepository questionRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final UserQuestionRepository userQuestionRepository;
    private final WebClient.Builder webClientBuilder;

    @PersistenceContext
    private EntityManager em;

    @PostMapping("/search")
    public List<Map<String, Object>> searchQuestionBank(@RequestBody Map<String, Object> request) {
        String subject = (String) request.get("subject");
        String type = (String) request.get("type");
        Integer difficulty = null;
        if (request.get("difficulty") != null) {
            difficulty = ((Number) request.get("difficulty")).intValue();
        }
        Long kpId = null;
        Object kpObj = request.get("kp");
        if (kpObj != null) {
            if (kpObj instanceof Number) {
                kpId = ((Number) kpObj).longValue();
            } else {
                try {
                    kpId = Long.parseLong(kpObj.toString());
                } catch (NumberFormatException e) {
                    log.warn("Invalid kp value: {}", kpObj);
                }
            }
        }

        List<Question> questions = questionRepository.searchQuestions(subject, type, difficulty, kpId);
        return questions.stream().map(this::toSummary).collect(Collectors.toList());
    }

    @PostMapping("/recommend")
    public List<Map<String, Object>> recommendQuestions(@RequestBody Map<String, Object> request) {
        Long userId = ((Number) request.get("userId")).longValue();
        int count = request.get("count") != null ? ((Number) request.get("count")).intValue() : 10;

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

    @PostMapping("/similar")
    public List<Map<String, Object>> getSimilarQuestions(@RequestBody Map<String, Object> request) {
        Long questionId = ((Number) request.get("questionId")).longValue();

        String vectorStr = null;
        try {
            vectorStr = (String) em.createNativeQuery("SELECT embedding::text FROM question WHERE id = :id")
                    .setParameter("id", questionId)
                    .getSingleResult();
        } catch (Exception e) {
            log.warn("No embedding found for question {}, falling back to KP-based similarity", questionId);
        }

        if (vectorStr != null) {
            List<Question> similar = questionRepository.findSimilarByVector(questionId, vectorStr, 5);
            return similar.stream().map(this::toSummary).collect(Collectors.toList());
        }

        // Fallback: same knowledge point
        Question q = questionRepository.findById(questionId).orElse(null);
        if (q != null && q.getKnowledgePointId() != null) {
            List<Question> similar = questionRepository.findByKnowledgePointIdAndIdNot(
                    q.getKnowledgePointId(), questionId,
                    org.springframework.data.domain.PageRequest.of(0, 5));
            return similar.stream().map(this::toSummary).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @PostMapping("/extract")
    public Map<String, Object> extractQuestionFromAnswer(@RequestBody Map<String, Object> request) {
        String answerText = (String) request.get("answerText");
        Long userId = request.get("userId") != null ? ((Number) request.get("userId")).longValue() : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "pending");
        result.put("message", "LLM extraction is not yet implemented. This endpoint will use the host LLM service to extract question information from answer text.");
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
