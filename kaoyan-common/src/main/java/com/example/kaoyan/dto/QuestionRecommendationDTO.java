package com.example.kaoyan.dto;

import com.example.kaoyan.entity.Question;
import lombok.Data;

/**
 * 题目推荐结果 DTO。
 *
 * 返回给前端的推荐条目，除题目本身外，还附带：
 *  - reason:   面向用户的可读推荐理由（如"针对薄弱点 XX"）
 *  - category: 推荐类别，前端用于展示不同徽标颜色
 *  - score:    推荐分（0-1 之间），前端可选显示
 */
@Data
public class QuestionRecommendationDTO {

    /** 题目本体（含 options 等） */
    private Question question;

    /** 推荐类别：weak | related | sibling | cold_start | revisit */
    private String category;

    /** 推荐理由（面向用户） */
    private String reason;

    /** 命中的知识点名称（可选，用于展示） */
    private String knowledgePointName;

    /** 推荐分（越大越推荐，0~1） */
    private Double score;
}
