package com.example.kaoyan.service;

import com.example.kaoyan.entity.Document;
import com.example.kaoyan.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentProcessor documentProcessor;
    private final KnowledgeBaseService knowledgeBaseService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Transactional
    public Document uploadDocument(Long userId, Long knowledgeBaseId, MultipartFile file) throws IOException {
        knowledgeBaseService.requireOwned(userId, knowledgeBaseId);

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalName = file.getOriginalFilename();
        if (originalName != null) {
            originalName = decodeFilename(originalName);
        }
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }
        String filename = UUID.randomUUID() + extension;
        Path filePath = uploadPath.resolve(filename);
        file.transferTo(filePath.toFile());

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

        documentProcessor.processAsync(document, filePath.toString());
        return document;
    }

    public List<Document> getKnowledgeBaseDocuments(Long userId, Long knowledgeBaseId) {
        knowledgeBaseService.requireOwned(userId, knowledgeBaseId);
        return documentRepository.findByUserIdAndKnowledgeBaseIdOrderByUploadTimeDesc(userId, knowledgeBaseId);
    }

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
        // LangChain4j: 删除 langchain_chunks 中 metadata->>'document_id' 匹配的记录
        jdbcTemplate.update(
                "DELETE FROM langchain_chunks WHERE metadata::jsonb->>'document_id' = ?",
                String.valueOf(documentId));
        documentRepository.deleteById(documentId);
    }

    private String decodeFilename(String name) {
        if (name == null) return null;
        if (name.contains("%")) {
            try {
                String decoded = URLDecoder.decode(name, StandardCharsets.UTF_8);
                if (!decoded.contains("%") && decoded.length() > 0) return decoded;
            } catch (Exception ignored) {}
        }
        return name;
    }
}
