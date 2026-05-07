package com.example.kaoyan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 知识图谱总响应：节点 + 边 + 统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphResponseDTO {

    private String subject;
    private List<GraphNodeDTO> nodes;
    private List<GraphEdgeDTO> edges;
    private Stats stats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stats {
        private Integer total;        // 节点总数
        private Integer weak;         // mastery < 0.5 且 attempts > 3
        private Integer intermediate; // 0.5 <= mastery < 0.8
        private Integer strong;       // mastery >= 0.8
        private Integer untouched;    // attempts = 0
    }
}
