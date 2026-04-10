package com.example.kaoyan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 题目 DTO
 */
@Data
public class QuestionDTO {

    @NotBlank(message = "科目不能为空")
    private String subject;

    @NotBlank(message = "题型不能为空")
    private String type;

    @Min(value = 1, message = "难度最小为1")
    @Max(value = 5, message = "难度最大为5")
    private Integer difficulty;

    @NotBlank(message = "题目内容不能为空")
    private String content;

    @NotBlank(message = "答案不能为空")
    private String answer;

    private String analysis;
    private Long knowledgePointId;
    private Integer year;
    private String source;

    /** 选择题选项 */
    private List<OptionDTO> options;

    @Data
    public static class OptionDTO {
        private String label;
        private String content;
        private Boolean isCorrect;
    }
}
