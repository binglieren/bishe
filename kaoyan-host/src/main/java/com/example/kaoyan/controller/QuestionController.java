package com.example.kaoyan.controller;

import com.example.kaoyan.dto.AnswerRequest;
import com.example.kaoyan.dto.ImageAnswerRequest;
import com.example.kaoyan.dto.QuestionDTO;
import com.example.kaoyan.dto.QuestionRecommendationDTO;
import com.example.kaoyan.entity.KnowledgePoint;
import com.example.kaoyan.entity.Question;
import com.example.kaoyan.entity.UserQuestion;
import com.example.kaoyan.entity.WrongAnswerRecord;
import com.example.kaoyan.service.QuestionEmbeddingService;
import com.example.kaoyan.service.QuestionService;
import com.example.kaoyan.service.QuestionTaggingService;
import com.example.kaoyan.service.RecommendationService;
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
    private final RecommendationService recommendationService;
    private final QuestionTaggingService questionTaggingService;
    private final QuestionEmbeddingService questionEmbeddingService;

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

    // ===================== 用户个人题库 =====================

    @GetMapping("/my")
    @Operation(summary = "获取用户个人题库（含做题统计）")
    public Result<List<UserQuestion>> getMyQuestions(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(questionService.getUserQuestions(userId));
    }

    @GetMapping("/my/knowledge-points")
    @Operation(summary = "获取用户积累的知识点列表")
    public Result<List<KnowledgePoint>> getMyKnowledgePoints(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(questionService.getUserKnowledgePoints(userId));
    }

    @PostMapping("/{id}/attempt")
    @Operation(summary = "记录一次做题，更新正确率和上次做题时间")
    public Result<Map<String, Object>> recordAttempt(Authentication auth,
                                                       @PathVariable Long id,
                                                       @RequestBody AnswerRequest request) {
        Long userId = (Long) auth.getPrincipal();
        request.setQuestionId(id);
        return Result.success(questionService.recordAttemptForUserQuestion(userId, request));
    }

    @PostMapping("/{id}/attempt-image")
    @Operation(summary = "简答题提交手写图片答案，LLM 判定对错")
    public Result<Map<String, Object>> recordImageAttempt(Authentication auth,
                                                            @PathVariable Long id,
                                                            @RequestBody ImageAnswerRequest request) {
        Long userId = (Long) auth.getPrincipal();
        request.setQuestionId(id);
        return Result.success(questionService.submitImageAnswer(userId, request));
    }

    @GetMapping("/{id}/similar")
    @Operation(summary = "获取相似题目推荐")
    public Result<List<Question>> getSimilarQuestions(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "5") int limit) {
        return Result.success(questionService.getSimilarQuestions(id, limit));
    }

    @GetMapping("/recommend")
    @Operation(summary = "个性化推荐题目（薄弱点 + 知识点树 + 多标签 Jaccard + 向量语义）")
    public Result<List<QuestionRecommendationDTO>> getRecommendations(Authentication auth,
                                                                       @RequestParam(defaultValue = "10") int limit) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(recommendationService.recommend(userId, limit));
    }

    @PostMapping("/{id}/retag")
    @Operation(summary = "对单题重新执行 LLM 多标签打标")
    public Result<Void> retagQuestion(Authentication auth, @PathVariable Long id) {
        Long userId = (Long) auth.getPrincipal();
        questionTaggingService.tagQuestion(id, userId);
        return Result.success("打标完成");
    }

    @PostMapping("/{id}/reembed")
    @Operation(summary = "对单题重新生成 embedding")
    public Result<Boolean> reembedQuestion(Authentication auth, @PathVariable Long id) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(questionEmbeddingService.embedQuestion(id, userId));
    }

    @PostMapping("/batch-tag")
    @Operation(summary = "批量为 pending 状态题目打标（建议后台异步调用）")
    public Result<Integer> batchTag(Authentication auth,
                                     @RequestParam(defaultValue = "20") int limit) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(questionTaggingService.tagPending(userId, limit));
    }

    @PostMapping("/batch-embed")
    @Operation(summary = "批量为 pending 状态题目生成 embedding")
    public Result<Integer> batchEmbed(Authentication auth,
                                       @RequestParam(defaultValue = "20") int limit) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(questionEmbeddingService.embedPending(userId, limit));
    }
}
