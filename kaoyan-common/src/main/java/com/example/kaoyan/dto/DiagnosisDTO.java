package com.example.kaoyan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 学习诊断报告
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisDTO {

    private String subject;
    private Integer examDays;          // 距离考试天数（无目标日期则 null）

    private List<GraphNodeDTO> weakKps;       // top 5 薄弱
    private List<GraphNodeDTO> untouchedKps;  // top 10 重要但未练习
    private List<GraphNodeDTO> strongKps;     // top 5 已掌握

    /** LLM 生成的自然语言建议 */
    private String aiAdvice;

    /** 整体进度：已练习节点数 / 总节点数 */
    private Integer attemptedCount;
    private Integer totalCount;
}
