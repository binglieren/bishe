package com.example.kaoyan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个知识点详情（点击节点弹出抽屉用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpDetailDTO {

    private Long id;
    private String name;
    private String subject;
    /** 类似 "数学 > 高数 > 极限 > 洛必达法则" */
    private String path;

    private Integer attemptedCount;
    private Integer correctCount;
    private Double masteryLevel;
    private String level;
    private Boolean focused;

    /** 最近做错的题（最多 5 题） */
    private List<WrongQuestion> wrongQuestions;

    /** 共现 top 5 知识点（按共现次数排序） */
    private List<RelatedKp> relatedKps;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WrongQuestion {
        private Long id;
        private String content;   // 截断后的题面
        private String subject;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedKp {
        private Long id;
        private String name;
        private Integer coOccurCount;
    }
}
