package com.example.kaoyan.controller;

import com.example.kaoyan.dto.LoginRequest;
import com.example.kaoyan.dto.LoginResponse;
import com.example.kaoyan.dto.RegisterRequest;
import com.example.kaoyan.service.AuthService;
import com.example.kaoyan.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户注册和登录")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.successWithMessage("注册成功", authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.successWithMessage("登录成功", authService.login(request));
    }
}
