package com.example.kaoyan.controller;

import com.example.kaoyan.dto.ExamDTO;
import com.example.kaoyan.dto.SubmitExamRequest;
import com.example.kaoyan.entity.Exam;
import com.example.kaoyan.entity.ExamRecord;
import com.example.kaoyan.service.ExamService;
import com.example.kaoyan.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模拟考试控制器
 */
@RestController
@RequestMapping("/api/exam")
@RequiredArgsConstructor
@Tag(name = "模拟考试", description = "考试管理和评分")
public class ExamController {

    private final ExamService examService;

    @PostMapping
    @Operation(summary = "创建考试")
    public Result<Exam> createExam(@Valid @RequestBody ExamDTO dto) {
        return Result.success(examService.createExam(dto));
    }

    @GetMapping
    @Operation(summary = "获取考试列表")
    public Result<List<Exam>> getExams(@RequestParam(required = false) String subject) {
        return Result.success(examService.getExams(subject));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取考试详情")
    public Result<Map<String, Object>> getExamDetail(@PathVariable Long id) {
        return Result.success(examService.getExamDetail(id));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "开始考试")
    public Result<ExamRecord> startExam(Authentication auth, @PathVariable Long id) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(examService.startExam(userId, id));
    }

    @PostMapping("/submit")
    @Operation(summary = "提交考试答案")
    public Result<Map<String, Object>> submitExam(Authentication auth,
                                                   @RequestBody SubmitExamRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(examService.submitExam(userId, request));
    }

    @GetMapping("/records")
    @Operation(summary = "获取我的考试记录")
    public Result<List<ExamRecord>> getMyRecords(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(examService.getUserExamRecords(userId));
    }

    @GetMapping("/record/{recordId}")
    @Operation(summary = "获取考试结果详情")
    public Result<Map<String, Object>> getExamResult(@PathVariable Long recordId) {
        return Result.success(examService.getExamResult(recordId));
    }
}
