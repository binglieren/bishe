package com.example.kaoyan.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用户档案 DTO
 */
@Data
public class UserProfileDTO {

    private String targetSchool;
    private String targetMajor;
    private LocalDate examDate;
    private LocalDate studyStartDate;
}
