package com.example.kaoyan.service;

import com.example.kaoyan.entity.Document;
import com.example.kaoyan.repository.DocumentChunkRepository;
import com.example.kaoyan.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * 文档服务：负责文件上传、文本提取、分块、向量化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentProcessor documentProcessor;
    private final KnowledgeBaseService knowledgeBaseService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * 上传文档到指定知识库
     */
    @Transactional
    public Document uploadDocument(Long userId, Long knowledgeBaseId, MultipartFile file) throws IOException {
        // 校验 KB 归属
        knowledgeBaseService.requireOwned(userId, knowledgeBaseId);

        // 创建上传目录
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 保存文件（仅保留UUID + 扩展名，避免Windows长路径问题）
        String originalName = file.getOriginalFilename();
        if (originalName != null) {
            // 浏览器可能发送URL编码的中文文件名，解码
            originalName = decodeFilename(originalName);
        }
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }
        String filename = UUID.randomUUID() + extension;
        Path filePath = uploadPath.resolve(filename);
        file.transferTo(filePath.toFile());

        // 保存文档记录
        Document document = new Document();
        document.setUserId(userId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setEnabled(true);
        document.setFilename(filename);
        document.setOriginalFilename(originalName);
        document.setFileSize(file.getSize());
        document.setFileType(file.getContentType());
        document.setStatus("PROCESSING");
        documentRepository.save(document);

        // 异步处理
        documentProcessor.processAsync(document, filePath.toString());

        return document;
    }

    /** 获取某个知识库下的文档列表 */
    public List<Document> getKnowledgeBaseDocuments(Long userId, Long knowledgeBaseId) {
        knowledgeBaseService.requireOwned(userId, knowledgeBaseId);
        return documentRepository.findByUserIdAndKnowledgeBaseIdOrderByUploadTimeDesc(userId, knowledgeBaseId);
    }

    /** 切换单个文档的启用状态 */
    @Transactional
    public Document setEnabled(Long userId, Long documentId, boolean enabled) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));
        if (!doc.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作此文档");
        }
        doc.setEnabled(enabled);
        return documentRepository.save(doc);
    }

    @Transactional
    public void deleteDocument(Long userId, Long documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));
        if (!doc.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作此文档");
        }
        documentChunkRepository.deleteByDocumentId(documentId);
        documentRepository.deleteById(documentId);
    }

    /** 解码浏览器可能URL编码的非ASCII文件名 */
    private String decodeFilename(String name) {
        if (name == null) return null;
        // 如果包含 %XX 编码，尝试解码
        if (name.contains("%")) {
            try {
                String decoded = URLDecoder.decode(name, StandardCharsets.UTF_8);
                // 如果解码后不包含 % 且长度合理，使用解码结果
                if (!decoded.contains("%") && decoded.length() > 0) {
                    return decoded;
                }
            } catch (Exception ignored) {
            }
        }
        return name;
    }
}
