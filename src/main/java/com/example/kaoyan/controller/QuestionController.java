package com.example.kaoyan.controller;

import com.example.kaoyan.dto.AnswerRequest;
import com.example.kaoyan.dto.QuestionDTO;
import com.example.kaoyan.entity.KnowledgePoint;
import com.example.kaoyan.entity.Question;
import com.example.kaoyan.entity.WrongAnswerRecord;
import com.example.kaoyan.service.QuestionService;
import com.example.kaoyan.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 题库控制器
 */
@RestController
@RequestMapping("/api/question")
@RequiredArgsConstructor
@Tag(name = "智能题库", description = "题目管理和做题")
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping
    @Operation(summary = "创建题目")
    public Result<Question> createQuestion(@Valid @RequestBody QuestionDTO dto) {
        return Result.success(questionService.createQuestion(dto));
    }

    @GetMapping
    @Operation(summary = "分页查询题目")
    public Result<Page<Question>> getQuestions(
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Long knowledgePointId,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(questionService.getQuestions(subject, type, difficulty, knowledgePointId, year, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取题目详情")
    public Result<Question> getQuestion(@PathVariable Long id) {
        return Result.success(questionService.getQuestionDetail(id));
    }

    @PostMapping("/submit")
    @Operation(summary = "提交答案")
    public Result<Map<String, Object>> submitAnswer(Authentication auth,
                                                     @Valid @RequestBody AnswerRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(questionService.submitAnswer(userId, request));
    }

    @GetMapping("/random")
    @Operation(summary = "随机出题")
    public Result<List<Question>> getRandomQuestions(
            @RequestParam String subject,
            @RequestParam(required = false) Long knowledgePointId,
            @RequestParam(defaultValue = "10") int count) {
        return Result.success(questionService.getRandomQuestions(subject, knowledgePointId, count));
    }

    @GetMapping("/wrong")
    @Operation(summary = "获取错题本")
    public Result<Page<WrongAnswerRecord>> getWrongAnswers(
            Authentication auth,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(questionService.getWrongAnswers(userId, resolved, page, size));
    }

    @PutMapping("/wrong/{id}/resolve")
    @Operation(summary = "标记错题为已解决")
    public Result<Void> resolveWrongAnswer(@PathVariable Long id) {
        questionService.resolveWrongAnswer(id);
        return Result.success("已标记为解决");
    }

    @GetMapping("/knowledge-points")
    @Operation(summary = "获取知识点列表")
    public Result<List<KnowledgePoint>> getKnowledgePoints(
            @RequestParam(required = false) String subject) {
        return Result.success(questionService.getKnowledgePoints(subject));
    }
}
