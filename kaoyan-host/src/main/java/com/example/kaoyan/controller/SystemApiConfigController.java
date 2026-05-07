package com.example.kaoyan.controller;

import com.example.kaoyan.dto.SystemApiConfigDTO;
import com.example.kaoyan.dto.SystemApiConfigRequest;
import com.example.kaoyan.service.SystemApiConfigService;
import com.example.kaoyan.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员 - 全局 API 配置（按环节维度）
 *
 * 仅 ADMIN 角色可访问（与其他 /api/admin/** 接口共用 SecurityConfig 过滤规则）。
 *
 * 安全：
 *   - 任何 GET 都不会返回 apiKey 原文，只返回 apiKeySet + apiKeyPreview
 *   - PUT 时 apiKey 三态：null=保持 / ""=清除 / 非空=替换
 */
@RestController
@RequestMapping("/api/admin/system-config")
@RequiredArgsConstructor
public class SystemApiConfigController {

    private final SystemApiConfigService service;

    /** 列出全部 4 个环节的配置（脱敏） */
    @GetMapping("/api")
    public Result<List<SystemApiConfigDTO>> list() {
        return Result.success(service.listAll());
    }

    /** 更新单个环节的配置 */
    @PutMapping("/api/{stage}")
    public Result<SystemApiConfigDTO> update(
            @PathVariable String stage,
            @RequestBody SystemApiConfigRequest request) {
        return Result.success(service.update(stage, request));
    }
}
