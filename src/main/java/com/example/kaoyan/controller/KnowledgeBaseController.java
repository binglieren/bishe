package com.example.kaoyan.controller;

import com.example.kaoyan.dto.KnowledgeBaseDTO;
import com.example.kaoyan.dto.KnowledgeBaseRequest;
import com.example.kaoyan.service.KnowledgeBaseService;
import com.example.kaoyan.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理控制器
 */
@RestController
@RequestMapping("/api/knowledge-base")
@RequiredArgsConstructor
@Tag(name = "知识库管理", description = "多知识库创建、更新、删除与文档组织")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    @Operation(summary = "获取我的全部知识库（含文档统计；首次调用会自动迁移老数据）")
    public Result<List<KnowledgeBaseDTO>> list(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(knowledgeBaseService.listForUser(userId));
    }

    @PostMapping
    @Operation(summary = "新建知识库")
    public Result<KnowledgeBaseDTO> create(Authentication auth,
                                           @Valid @RequestBody KnowledgeBaseRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return Result.successWithMessage("创建成功", knowledgeBaseService.create(userId, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新知识库（重命名/修改描述）")
    public Result<KnowledgeBaseDTO> update(Authentication auth,
                                           @PathVariable Long id,
                                           @Valid @RequestBody KnowledgeBaseRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return Result.successWithMessage("更新成功", knowledgeBaseService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识库（会级联删除其下全部文档）")
    public Result<Void> delete(Authentication auth, @PathVariable Long id) {
        Long userId = (Long) auth.getPrincipal();
        knowledgeBaseService.delete(userId, id);
        return Result.success("删除成功");
    }
}
