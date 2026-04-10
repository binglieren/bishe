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

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * 上传文档
     */
    @Transactional
    public Document uploadDocument(Long userId, MultipartFile file) throws IOException {
        // 创建上传目录
        Path uploadPath = Paths.get(uploadDir);
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
        document.setFilename(filename);
        document.setOriginalFilename(file.getOriginalFilename());
        document.setFileSize(file.getSize());
        document.setFileType(file.getContentType());
        document.setStatus("PROCESSING");
        documentRepository.save(document);

        // 异步处理文档
        processDocumentAsync(document, filePath.toString());

        return document;
    }

    /**
     * 异步处理文档：提取文本 → 分块 → 向量化
     */
    @Async
    public void processDocumentAsync(Document document, String filePath) {
        try {
            // 1. 提取文本
            String text = extractText(filePath, document.getFileType());

            // 2. 分块
            List<String> chunks = splitText(text, 500, 50);

            // 3. 向量化并存储
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

    /**
     * 提取文本内容
     */
    private String extractText(String filePath, String fileType) throws IOException {
        if (fileType != null && fileType.contains("pdf")) {
            try (PDDocument pdf = Loader.loadPDF(new File(filePath))) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(pdf);
            }
        }
        // 默认按纯文本处理
        return Files.readString(Path.of(filePath));
    }

    /**
     * 文本分块：按字符数切分，保留上下文重叠
     */
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

    /**
     * 获取用户的文档列表
     */
    public List<Document> getUserDocuments(Long userId) {
        return documentRepository.findByUserIdOrderByUploadTimeDesc(userId);
    }

    /**
     * 删除文档
     */
    @Transactional
    public void deleteDocument(Long documentId) {
        documentChunkRepository.deleteByDocumentId(documentId);
        documentRepository.deleteById(documentId);
    }
}
