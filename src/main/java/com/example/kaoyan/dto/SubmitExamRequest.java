package com.example.kaoyan.dto;

import lombok.Data;

import java.util.List;

/**
 * 提交考试答案请求 DTO
 */
@Data
public class SubmitExamRequest {

    private Long recordId;

    private List<ExamAnswerItem> answers;

    @Data
    public static class ExamAnswerItem {
        private Long questionId;
        private String userAnswer;
    }
}
