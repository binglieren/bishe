package com.example.kaoyan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识图谱边
 *
 * type:
 *   - hierarchy 父子层级关系（实线）
 *   - co_occur  共现关系（同题出现 ≥ 2 次，虚线，weight = 共现次数）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdgeDTO {
    private Long source;
    private Long target;
    private String type;
    private Double weight;
}
