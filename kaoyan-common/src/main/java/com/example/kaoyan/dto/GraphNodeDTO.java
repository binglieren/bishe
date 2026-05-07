package com.example.kaoyan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识图谱节点（一个知识点）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphNodeDTO {

    private Long id;
    private String name;
    private String subject;
    private Long parentId;

    /** 该 KP 关联的题目数（节点大小用） */
    private Integer questionCount;

    /** 用户已答数 / 答对数 */
    private Integer attemptedCount;
    private Integer correctCount;

    /** 0.0-1.0；attempts=0 时为 null */
    private Double masteryLevel;

    /** untouched | weak | intermediate | strong （颜色用） */
    private String level;

    /** 用户是否标记为重点 */
    private Boolean focused;
}
