package com.example.kaoyan.controller;

import com.example.kaoyan.entity.Question;
import com.example.kaoyan.service.AnalysisService;
import com.example.kaoyan.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 薄弱知识点分析控制器
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Tag(name = "薄弱知识点分析", description = "学习数据分析与推荐")
public class AnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/mastery")
    @Operation(summary = "获取所有知识点掌握度")
    public Result<List<Map<String, Object>>> getMasteryOverview(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(analysisService.getMasteryOverview(userId));
    }

    @GetMapping("/weak-points")
    @Operation(summary = "获取薄弱知识点")
    public Result<List<Map<String, Object>>> getWeakPoints(
            Authentication auth,
            @RequestParam(required = false) BigDecimal threshold) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(analysisService.getWeakPoints(userId, threshold));
    }

    @GetMapping("/recommend")
    @Operation(summary = "推荐练习题（基于薄弱知识点）")
    public Result<List<Question>> getRecommendedQuestions(
            Authentication auth,
            @RequestParam(defaultValue = "10") int count) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(analysisService.getRecommendedQuestions(userId, count));
    }

    @GetMapping("/subject")
    @Operation(summary = "按科目统计掌握度")
    public Result<Map<String, Object>> getSubjectAnalysis(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(analysisService.getSubjectAnalysis(userId));
    }
}
