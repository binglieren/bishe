package com.example.kaoyan.controller;

import com.example.kaoyan.dto.DiagnosisDTO;
import com.example.kaoyan.dto.GraphResponseDTO;
import com.example.kaoyan.dto.KpDetailDTO;
import com.example.kaoyan.service.KnowledgeGraphService;
import com.example.kaoyan.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱接口（用户级，与 admin 题库管理无关）
 */
@RestController
@RequestMapping("/api/knowledge-graph")
@RequiredArgsConstructor
@Tag(name = "知识图谱", description = "学习者知识地图与诊断")
public class KnowledgeGraphController {

    private final KnowledgeGraphService graphService;

    @GetMapping
    @Operation(summary = "取知识图谱（节点 + 边 + 统计）")
    public Result<GraphResponseDTO> getGraph(
            Authentication auth,
            @RequestParam(required = false) String subject) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(graphService.buildGraph(userId, normalize(subject)));
    }

    @GetMapping("/kp/{kpId}/detail")
    @Operation(summary = "节点详情（错题 + 相关 KP）")
    public Result<KpDetailDTO> getKpDetail(Authentication auth, @PathVariable Long kpId) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(graphService.getKpDetail(userId, kpId));
    }

    @GetMapping("/diagnosis")
    @Operation(summary = "AI 学习诊断报告")
    public Result<DiagnosisDTO> diagnose(
            Authentication auth,
            @RequestParam(required = false) String subject) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(graphService.diagnose(userId, normalize(subject)));
    }

    @PostMapping("/kp/{kpId}/focus")
    @Operation(summary = "收藏知识点")
    public Result<Map<String, Object>> focus(Authentication auth, @PathVariable Long kpId) {
        Long userId = (Long) auth.getPrincipal();
        graphService.toggleFocus(userId, kpId, true);
        return Result.success(Map.of("focused", true));
    }

    @DeleteMapping("/kp/{kpId}/focus")
    @Operation(summary = "取消收藏")
    public Result<Map<String, Object>> unfocus(Authentication auth, @PathVariable Long kpId) {
        Long userId = (Long) auth.getPrincipal();
        graphService.toggleFocus(userId, kpId, false);
        return Result.success(Map.of("focused", false));
    }

    @GetMapping("/kp/{kpId}/practice")
    @Operation(summary = "取该 KP 下的练习题 id（最多 limit 道）")
    public Result<Map<String, Object>> getPracticeQuestions(
            Authentication auth,
            @PathVariable Long kpId,
            @RequestParam(defaultValue = "10") int limit) {
        List<Long> ids = graphService.getKpPracticeQuestionIds(kpId, Math.min(limit, 50));
        return Result.success(Map.of("questionIds", ids));
    }

    /** 把 "all"/空字符串归一化为 null（= 全部科目） */
    private String normalize(String subject) {
        if (subject == null) return null;
        String s = subject.trim();
        if (s.isEmpty() || "all".equalsIgnoreCase(s) || "全部".equals(s)) return null;
        return s;
    }
}
