package com.example.kaoyan.service;

import com.example.kaoyan.entity.Document;
import com.example.kaoyan.entity.DocumentChunk;
import com.example.kaoyan.repository.DocumentChunkRepository;
import com.example.kaoyan.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
    private final LlmService llmService;
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
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 保存文件
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(filename);
        file.transferTo(filePath.toFile());

        // 保存文档记录
        Document document = new Document();
        document.setUserId(userId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setEnabled(true);
        document.setFilename(filename);
        document.setOriginalFilename(file.getOriginalFilename());
        document.setFileSize(file.getSize());
        document.setFileType(file.getContentType());
        document.setStatus("PROCESSING");
        documentRepository.save(document);

        // 异步处理
        processDocumentAsync(document, filePath.toString());

        return document;
    }

    @Async
    public void processDocumentAsync(Document document, String filePath) {
        try {
            String text = extractText(filePath, document.getFileType());
            List<String> chunks = splitText(text, 500, 50);

            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                float[] embedding = llmService.getEmbedding(chunkText, document.getUserId());

                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocumentId(document.getId());
                chunk.setContent(chunkText);
                chunk.setChunkIndex(i);
                chunk.setEmbedding(llmService.vectorToString(embedding));
                documentChunkRepository.save(chunk);
            }

            document.setStatus("COMPLETED");
            documentRepository.save(document);
            log.info("文档处理完成: {}", document.getOriginalFilename());

        } catch (Exception e) {
            log.error("文档处理失败: {}", document.getOriginalFilename(), e);
            document.setStatus("FAILED");
            documentRepository.save(document);
        }
    }

    private String extractText(String filePath, String fileType) throws IOException {
        if (fileType != null && fileType.contains("pdf")) {
            try (PDDocument pdf = Loader.loadPDF(new File(filePath))) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(pdf);
            }
        }
        return Files.readString(Path.of(filePath));
    }

    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize - overlap;
        }
        return chunks;
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
}
