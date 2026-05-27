package com.example.kaoyan.config;

import com.example.kaoyan.entity.KnowledgePoint;
import com.example.kaoyan.repository.KnowledgeMasteryRepository;
import com.example.kaoyan.repository.KnowledgePointFocusRepository;
import com.example.kaoyan.repository.KnowledgePointRepository;
import com.example.kaoyan.repository.QuestionKnowledgePointRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class KpSeedInitializer implements CommandLineRunner {

    private final KnowledgePointRepository kpRepo;
    private final KnowledgeMasteryRepository masteryRepo;
    private final KnowledgePointFocusRepository focusRepo;
    private final QuestionKnowledgePointRepository qkpRepo;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) {
        // 如果数量不足种子数据量（55+），视为需要重播
        long count = kpRepo.count();
        if (count > 50) {
            log.info("知识点种子已播种（{}条），跳过", count);
            return;
        }

        log.info("首次启动 / 数据不足，清空旧知识点并播种高数大纲...");

        qkpRepo.deleteAll();
        focusRepo.deleteAll();
        masteryRepo.deleteAll();
        kpRepo.deleteAll();

        try {
            InputStream is = getClass().getClassLoader()
                    .getResourceAsStream("kp-seed-advanced-math.json");
            JsonNode root = objectMapper.readTree(is);
            for (JsonNode tree : root.get("trees")) {
                Long rootId = saveKp(tree.get("name").asText(), null);
                for (JsonNode ch : tree.get("children")) {
                    Long chId = saveKp(ch.get("name").asText(), rootId);
                    for (JsonNode topic : ch.get("children")) {
                        saveKp(topic.get("name").asText(), chId);
                    }
                }
            }
            log.info("高数知识点播种完成");
        } catch (Exception e) {
            log.error("知识点种子加载失败", e);
        }
    }

    private Long saveKp(String name, Long parentId) {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setSubject("数学");
        kp.setName(name);
        kp.setParentId(parentId);
        kp.setSortOrder(0);
        return kpRepo.save(kp).getId();
    }
}
