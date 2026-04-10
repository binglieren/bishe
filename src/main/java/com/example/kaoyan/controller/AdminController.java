package com.example.kaoyan.controller;

import com.example.kaoyan.entity.Exam;
import com.example.kaoyan.entity.KnowledgePoint;
import com.example.kaoyan.entity.Question;
import com.example.kaoyan.entity.User;
import com.example.kaoyan.service.AdminService;
import com.example.kaoyan.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员控制器
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ==================== 数据统计 ====================

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        return Result.success(adminService.getStatistics());
    }

    // ==================== 用户管理 ====================

    @GetMapping("/users")
    public Result<Page<User>> getUserList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminService.getUserList(page, size));
    }

    @PutMapping("/users/{userId}/role")
    public Result<User> updateUserRole(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || (!role.equals("USER") && !role.equals("ADMIN"))) {
            throw new IllegalArgumentException("角色必须为 USER 或 ADMIN");
        }
        return Result.success(adminService.updateUserRole(userId, role));
    }

    @PutMapping("/users/{userId}/password")
    public Result<Void> resetUserPassword(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        String newPassword = body.get("password");
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }
        adminService.resetUserPassword(userId, newPassword);
        return Result.success("密码重置成功");
    }

    @DeleteMapping("/users/{userId}")
    public Result<Void> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return Result.success("用户已删除");
    }

    // ==================== 题目管理 ====================

    @GetMapping("/questions")
    public Result<Page<Question>> getQuestionList(
            @RequestParam(required = false) String subject,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (subject != null && !subject.isEmpty()) {
            return Result.success(adminService.getQuestionListBySubject(subject, page, size));
        }
        return Result.success(adminService.getQuestionList(page, size));
    }

    @PostMapping("/questions")
    public Result<Question> createQuestion(@RequestBody Question question) {
        return Result.success(adminService.createQuestion(question));
    }

    @PutMapping("/questions/{questionId}")
    public Result<Question> updateQuestion(
            @PathVariable Long questionId,
            @RequestBody Question question) {
        return Result.success(adminService.updateQuestion(questionId, question));
    }

    @DeleteMapping("/questions/{questionId}")
    public Result<Void> deleteQuestion(@PathVariable Long questionId) {
        adminService.deleteQuestion(questionId);
        return Result.success("题目已删除");
    }

    // ==================== 考试管理 ====================

    @GetMapping("/exams")
    public Result<Page<Exam>> getExamList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminService.getExamList(page, size));
    }

    @PostMapping("/exams")
    public Result<Exam> createExam(@RequestBody Exam exam) {
        return Result.success(adminService.createExam(exam));
    }

    @PutMapping("/exams/{examId}")
    public Result<Exam> updateExam(
            @PathVariable Long examId,
            @RequestBody Exam exam) {
        return Result.success(adminService.updateExam(examId, exam));
    }

    @DeleteMapping("/exams/{examId}")
    public Result<Void> deleteExam(@PathVariable Long examId) {
        adminService.deleteExam(examId);
        return Result.success("考试已删除");
    }

    // ==================== 知识点管理 ====================

    @GetMapping("/knowledge-points")
    public Result<Page<KnowledgePoint>> getKnowledgePointList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(adminService.getKnowledgePointList(page, size));
    }

    @PostMapping("/knowledge-points")
    public Result<KnowledgePoint> createKnowledgePoint(@RequestBody KnowledgePoint kp) {
        return Result.success(adminService.createKnowledgePoint(kp));
    }

    @PutMapping("/knowledge-points/{kpId}")
    public Result<KnowledgePoint> updateKnowledgePoint(
            @PathVariable Long kpId,
            @RequestBody KnowledgePoint kp) {
        return Result.success(adminService.updateKnowledgePoint(kpId, kp));
    }

    @DeleteMapping("/knowledge-points/{kpId}")
    public Result<Void> deleteKnowledgePoint(@PathVariable Long kpId) {
        adminService.deleteKnowledgePoint(kpId);
        return Result.success("知识点已删除");
    }
}
