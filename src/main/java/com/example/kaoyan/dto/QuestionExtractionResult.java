package com.example.kaoyan.dto;

import lombok.Data;

import java.util.List;

/**
 * LLM 从图片中提取的结构化题目信息
 */
@Data
public class QuestionExtractionResult {

    /** 题型：单选 | 多选 | 填空 | 简答 */
    private String type;

    /** 科目：数学 | 英语 | 专业课（保存时由 QuestionService.normalizeSubject 归一化） */
    private String subject;

    /** 题目正文（不含选项列表） */
    private String content;

    /** 选择题选项（非选择题为空） */
    private List<OptionItem> options;

    /** 正确答案（选择题用字母如 A 或 AB，简答题用文字） */
    private String answer;

    /** 解题分析 */
    private String analysis;

    /** 涉及的知识点名称列表 */
    private List<String> knowledgePoints;

    @Data
    public static class OptionItem {
        private String label;    // A / B / C / D
        private String content;
        private Boolean isCorrect;
    }
}
