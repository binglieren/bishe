package com.example.kaoyan.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class KpCatalogNodeDTO {
    private Long id;
    private String name;
    private String level;
    private BigDecimal mastery;
    private int questionCount;
    private int correctCount;
    private boolean focused;
    private List<KpCatalogNodeDTO> children;
}
