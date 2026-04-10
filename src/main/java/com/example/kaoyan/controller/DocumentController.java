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
    @Operation(summary = "上传学习资料")
    public Result<Document> uploadDocument(Authentication auth,
                                            @RequestParam("file") MultipartFile file) throws IOException {
        Long userId = (Long) auth.getPrincipal();
        return Result.successWithMessage("上传成功，正在处理中", documentService.uploadDocument(userId, file));
    }

    @GetMapping
    @Operation(summary = "获取我的文档列表")
    public Result<List<Document>> getDocuments(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(documentService.getUserDocuments(userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return Result.success("删除成功");
    }
}
