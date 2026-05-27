package com.example.kaoyan.service;

import com.example.kaoyan.entity.KnowledgeMastery;
import com.example.kaoyan.entity.KnowledgePoint;
import com.example.kaoyan.repository.KnowledgeMasteryRepository;
import com.example.kaoyan.repository.KnowledgePointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeInternalService {

    private final KnowledgePointRepository knowledgePointRepository;
    private final KnowledgeMasteryRepository knowledgeMasteryRepository;

    public List<Map<String, Object>> getKnowledgePointTree(String subject) {
        List<KnowledgePoint> allPoints;
        if (subject != null && !subject.isEmpty()) {
            allPoints = knowledgePointRepository.findBySubject(subject);
        } else {
            allPoints = knowledgePointRepository.findAll();
        }
        return buildTree(allPoints, null);
    }

    private List<Map<String, Object>> buildTree(List<KnowledgePoint> allPoints, Long parentId) {
        return allPoints.stream()
                .filter(kp -> Objects.equals(kp.getParentId(), parentId))
                .sorted(Comparator.comparingInt(kp -> kp.getSortOrder() != null ? kp.getSortOrder() : 0))
                .map(kp -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", kp.getId());
                    node.put("name", kp.getName());
                    node.put("subject", kp.getSubject());
                    List<Map<String, Object>> children = buildTree(allPoints, kp.getId());
                    node.put("children", children);
                    return node;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getKpDetail(Long kpId, Long userId) {
        KnowledgePoint kp = knowledgePointRepository.findById(kpId)
                .orElseThrow(() -> new RuntimeException("Knowledge point not found: " + kpId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", kp.getId());
        result.put("name", kp.getName());
        result.put("subject", kp.getSubject());

        if (userId != null) {
            KnowledgeMastery mastery = knowledgeMasteryRepository
                    .findByUserIdAndKnowledgePointId(userId, kpId).orElse(null);
            if (mastery != null) {
                result.put("mastery", mastery.getMasteryLevel());
                result.put("correctCount", mastery.getCorrectCount());
                result.put("totalCount", mastery.getTotalCount());
                result.put("wrongCount", mastery.getTotalCount() - mastery.getCorrectCount());
            } else {
                result.put("mastery", BigDecimal.ZERO);
                result.put("correctCount", 0);
                result.put("totalCount", 0);
                result.put("wrongCount", 0);
            }
        }

        List<KnowledgePoint> siblings;
        if (kp.getParentId() != null) {
            siblings = knowledgePointRepository.findByParentId(kp.getParentId());
        } else {
            siblings = knowledgePointRepository.findByParentIdIsNull();
        }
        List<Map<String, Object>> relatedKps = siblings.stream()
                .filter(s -> !s.getId().equals(kp.getId()))
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("name", s.getName());
                    return m;
                })
                .collect(Collectors.toList());
        result.put("relatedKps", relatedKps);

        return result;
    }

    public List<Map<String, Object>> getWeakPoints(Long userId, Double threshold) {
        BigDecimal thr = threshold != null
                ? BigDecimal.valueOf(threshold)
                : new BigDecimal("0.6");

        List<KnowledgeMastery> weakPoints = knowledgeMasteryRepository.findWeakPoints(userId, thr);

        return weakPoints.stream().map(wp -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kpId", wp.getKnowledgePointId());
            String kpName = knowledgePointRepository.findById(wp.getKnowledgePointId())
                    .map(KnowledgePoint::getName).orElse("Unknown");
            map.put("kpName", kpName);
            map.put("mastery", wp.getMasteryLevel());
            map.put("correctCount", wp.getCorrectCount());
            map.put("totalCount", wp.getTotalCount());
            return map;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> diagnoseLearning(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "pending");
        result.put("userId", userId);
        result.put("message", "Learning diagnosis via LLM is not yet implemented.");
        return result;
    }
}
