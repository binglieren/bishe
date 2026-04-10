package com.example.kaoyan.controller;

import com.example.kaoyan.dto.UserInfoDTO;
import com.example.kaoyan.dto.UserProfileDTO;
import com.example.kaoyan.entity.CheckIn;
import com.example.kaoyan.service.UserService;
import com.example.kaoyan.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户信息和打卡")
public class UserController {

    private final UserService userService;

    @GetMapping("/info")
    @Operation(summary = "获取当前用户信息")
    public Result<UserInfoDTO> getUserInfo(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(userService.getUserInfo(userId));
    }

    @PutMapping("/profile")
    @Operation(summary = "更新用户档案")
    public Result<Void> updateProfile(Authentication auth, @RequestBody UserProfileDTO dto) {
        Long userId = (Long) auth.getPrincipal();
        userService.updateProfile(userId, dto);
        return Result.success("更新成功");
    }

    @PostMapping("/check-in")
    @Operation(summary = "每日打卡")
    public Result<CheckIn> checkIn(Authentication auth,
                                   @RequestParam(required = false) Integer studyMinutes) {
        Long userId = (Long) auth.getPrincipal();
        return Result.successWithMessage("打卡成功", userService.checkIn(userId, studyMinutes));
    }

    @GetMapping("/check-in/history")
    @Operation(summary = "获取打卡记录")
    public Result<List<CheckIn>> getCheckInHistory(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(userService.getCheckInHistory(userId, start, end));
    }
}
