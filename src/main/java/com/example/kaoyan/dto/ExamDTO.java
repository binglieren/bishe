package com.example.kaoyan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 考试 DTO
 */
@Data
public class ExamDTO {

    @NotBlank(message = "考试标题不能为空")
    private String title;

    @NotBlank(message = "科目不能为空")
    private String subject;

    @NotNull(message = "考试时长不能为空")
    private Integer durationMinutes;

    @NotNull(message = "总分不能为空")
    private Integer totalScore;

    private String description;

    /** 题目列表：questionId + 分值 */
    private List<ExamQuestionItem> questions;

    @Data
    public static class ExamQuestionItem {
        private Long questionId;
        private Integer score;
        private Integer sortOrder;
    }
}
