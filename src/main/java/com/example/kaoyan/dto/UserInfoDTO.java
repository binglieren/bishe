package com.example.kaoyan.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户完整信息 DTO（包含档案）
 */
@Data
public class UserInfoDTO {

    private Long id;
    private String username;
    private String email;
    private String avatar;
    private LocalDateTime createdAt;

    // 档案信息
    private String targetSchool;
    private String targetMajor;
    private LocalDate examDate;
    private LocalDate studyStartDate;

    // 学习统计
    private Integer checkInDays;
    private Integer totalStudyMinutes;
}
