package com.example.kaoyan.controller;

import com.example.kaoyan.entity.Document;
import com.example.kaoyan.service.DocumentService;
import com.example.kaoyan.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 文档管理控制器
 */
@RestController
@RequestMapping("/api/document")
@RequiredArgsConstructor
@Tag(name = "文档管理", description = "学习资料上传与管理")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    @Operation(summary = "上传学习资料到指定知识库")
    public Result<Document> uploadDocument(Authentication auth,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam("knowledgeBaseId") Long knowledgeBaseId) throws IOException {
        Long userId = (Long) auth.getPrincipal();
        return Result.successWithMessage("上传成功，正在处理中",
                documentService.uploadDocument(userId, knowledgeBaseId, file));
    }

    @GetMapping
    @Operation(summary = "获取某个知识库下的文档列表")
    public Result<List<Document>> listByKb(Authentication auth,
                                            @RequestParam("knowledgeBaseId") Long knowledgeBaseId) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(documentService.getKnowledgeBaseDocuments(userId, knowledgeBaseId));
    }

    @PatchMapping("/{id}/enabled")
    @Operation(summary = "切换文档在知识库中的启用状态")
    public Result<Document> setEnabled(Authentication auth,
                                        @PathVariable Long id,
                                        @RequestBody Map<String, Boolean> body) {
        Long userId = (Long) auth.getPrincipal();
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return Result.success(documentService.setEnabled(userId, id, enabled));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档")
    public Result<Void> deleteDocument(Authentication auth, @PathVariable Long id) {
        Long userId = (Long) auth.getPrincipal();
        documentService.deleteDocument(userId, id);
        return Result.success("删除成功");
    }
}
