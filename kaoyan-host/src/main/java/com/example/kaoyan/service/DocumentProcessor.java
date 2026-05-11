package com.example.kaoyan.service;

import com.example.kaoyan.entity.Document;
import com.example.kaoyan.repository.DocumentRepository;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessor {

    private final DocumentRepository documentRepository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final LlmService llmService;

    private static final int OCR_DPI = 100;
    private static final int MAX_PAGE_WIDTH = 1000;
    private static final int MAX_PAGE_HEIGHT = 1500;
    private static final int PAGES_PER_BATCH = 15;
    private static final int TEXT_THRESHOLD = 500;  // 低于500字视为扫描版PDF，启动OCR
    private final DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);

    @Async
    public void processAsync(Document document, String filePath) {
        try {
            String text = extractText(filePath, document.getFileType());
            String lowerType = document.getFileType() != null ? document.getFileType().toLowerCase() : "";
            String fileName = new File(filePath).getName().toLowerCase();

            boolean isImage = lowerType.contains("image")
                    || fileName.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$");
            boolean isPdf = lowerType.contains("pdf") || fileName.endsWith(".pdf");
            boolean textTooShort = text == null || text.trim().length() < TEXT_THRESHOLD;

            if (isImage) {
                log.info("图片文件，启动LLM OCR: {}", document.getOriginalFilename());
                text = llmOcrImage(filePath, document.getUserId());
            } else if (isPdf && textTooShort) {
                log.info("PDF文本过短({}字)，启动LLM OCR: {}",
                        text == null ? 0 : text.trim().length(), document.getOriginalFilename());
                text = llmOcrPdf(filePath, document.getUserId());
            } else if (!isPdf && textTooShort && lowerType.contains("epub")) {
                log.info("EPUB文本过短({}字)，启动LLM OCR: {}",
                        text == null ? 0 : text.trim().length(), document.getOriginalFilename());
                text = llmOcrPdf(filePath, document.getUserId());
            } else {
                log.info("文本提取成功({}字)，跳过OCR: {}",
                        text != null ? text.trim().length() : 0, document.getOriginalFilename());
            }

            if (text == null || text.trim().isEmpty()) {
                log.warn("未能提取到任何文本: {}", document.getOriginalFilename());
                updateStatus(document.getId(), "FAILED", "未提取到文本内容");
                return;
            }

            // LangChain4j 分块
            List<TextSegment> segments = splitter.split(
                    dev.langchain4j.data.document.Document.from(text));

            // 分批向量化（DashScope 限制每批最多 10 条）
            List<String> segmentTexts = segments.stream().map(TextSegment::text).toList();
            List<Embedding> allEmbeddings = new ArrayList<>();
            int batchSize = 10;
            for (int i = 0; i < segmentTexts.size(); i += batchSize) {
                int end = Math.min(i + batchSize, segmentTexts.size());
                List<TextSegment> batch = new ArrayList<>();
                for (int j = i; j < end; j++) {
                    batch.add(TextSegment.from(segmentTexts.get(j)));
                }
                List<Embedding> batchEmbeddings = embeddingModel.embedAll(batch).content();
                allEmbeddings.addAll(batchEmbeddings);
            }

            // 写入 metadata 并入库
            for (int i = 0; i < segments.size(); i++) {
                TextSegment seg = segments.get(i);
                seg.metadata().put("kb_id", String.valueOf(document.getKnowledgeBaseId()));
                seg.metadata().put("document_id", String.valueOf(document.getId()));
                seg.metadata().put("filename", document.getOriginalFilename());
                seg.metadata().put("chunk_index", String.valueOf(i));
                seg.metadata().put("enabled", "true");
                embeddingStore.add(allEmbeddings.get(i), seg);
            }

            updateStatus(document.getId(), "COMPLETED", null);
            log.info("文档处理完成 ({} chunks): {}", segments.size(), document.getOriginalFilename());

        } catch (Exception e) {
            log.error("文档处理失败: {}", document.getOriginalFilename(), e);
            String errMsg = e.getMessage();
            if (errMsg == null || errMsg.isBlank()) errMsg = e.getClass().getSimpleName();
            if (errMsg.length() > 500) errMsg = errMsg.substring(0, 500);
            updateStatus(document.getId(), "FAILED", errMsg);
        }
    }

    private String llmOcrImage(String filePath, Long userId) {
        try {
            BufferedImage image = ImageIO.read(new File(filePath));
            if (image == null) { log.error("无法读取图片: {}", filePath); return null; }
            image = resizeIfNeeded(image);
            String base64 = bufferedImageToBase64(image);
            return ocrBatch(List.of(base64), 0, 1, 1, userId);
        } catch (IOException e) { log.error("图片 OCR 失败", e); return null; }
    }

    private String llmOcrPdf(String filePath, Long userId) {
        try (PDDocument pdf = Loader.loadPDF(new File(filePath))) {
            int totalPages = pdf.getNumberOfPages();
            PDFRenderer renderer = new PDFRenderer(pdf);
            StringBuilder fullText = new StringBuilder();

            for (int batchStart = 0; batchStart < totalPages; batchStart += PAGES_PER_BATCH) {
                int batchEnd = Math.min(batchStart + PAGES_PER_BATCH, totalPages);
                List<String> pageImages = new ArrayList<>();
                for (int p = batchStart; p < batchEnd; p++) {
                    BufferedImage image = renderer.renderImageWithDPI(p, OCR_DPI);
                    image = resizeIfNeeded(image);
                    pageImages.add(bufferedImageToBase64(image));
                }
                String batchText = ocrBatch(pageImages, batchStart, batchEnd, totalPages, userId);
                if (batchText != null && !batchText.isBlank()) {
                    fullText.append(batchText).append("\n\n");
                }
                log.info("OCR 进度: {}/{} 页", batchEnd, totalPages);
            }
            return fullText.toString().trim();
        } catch (IOException e) { log.error("LLM OCR 失败", e); return null; }
    }

    private String ocrBatch(List<String> pageImages, int startPage, int endPage, int totalPages, Long userId) {
        String prompt = pageImages.size() == 1
                ? "OCR page " + (startPage + 1) + " of " + totalPages + ". Output only the text, nothing else."
                : "OCR these " + pageImages.size() + " PDF page images (pages "
                  + (startPage + 1) + "-" + endPage + " of " + totalPages
                  + "). Output only the text, one page after another, separated by '--- Page N ---'. No explanations.";

        List<Map<String, Object>> contentParts = new ArrayList<>();
        contentParts.add(Map.of("type", "text", "text", prompt));
        for (String img : pageImages) {
            contentParts.add(Map.of("type", "image_url", "image_url",
                    Map.of("url", "data:image/jpeg;base64," + img)));
        }
        try {
            return llmService.chatMultimodal(List.of(Map.of("role", "user", "content", contentParts)), userId);
        } catch (Exception e) { log.error("OCR 批次 {}-{} 失败", startPage + 1, endPage, e); return null; }
    }

    private BufferedImage resizeIfNeeded(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        if (w <= MAX_PAGE_WIDTH && h <= MAX_PAGE_HEIGHT) return image;
        double scale = Math.min((double) MAX_PAGE_WIDTH / w, (double) MAX_PAGE_HEIGHT / h);
        int newW = (int) (w * scale), newH = (int) (h * scale);
        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, newW, newH, null);
        g.dispose();
        return resized;
    }

    private String bufferedImageToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void updateStatus(Long documentId, String status, String errorMessage) {
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc != null) { doc.setStatus(status); doc.setErrorMessage(errorMessage); documentRepository.save(doc); }
    }

    private String extractText(String filePath, String fileType) throws IOException {
        String lower = fileType != null ? fileType.toLowerCase() : "";
        String fileName = new File(filePath).getName().toLowerCase();
        if (lower.contains("pdf") || fileName.endsWith(".pdf")) return extractPdfText(filePath);
        if (lower.contains("docx") || lower.contains("officedocument") || fileName.endsWith(".docx")) return extractDocxText(filePath);
        if (lower.contains("epub") || fileName.endsWith(".epub")) return extractEpubText(filePath);
        if (lower.contains("image") || fileName.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp)$")) return null;
        return Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
    }

    private String extractPdfText(String filePath) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setAddMoreFormatting(false);
            return stripper.getText(pdf);
        }
    }

    private String extractDocxText(String filePath) throws IOException {
        try (InputStream is = Files.newInputStream(Path.of(filePath));
             XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String extractEpubText(String filePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (ZipFile zip = new ZipFile(filePath, StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".xhtml") || name.endsWith(".html") || name.endsWith(".htm")) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        sb.append(stripHtml(html)).append("\n\n");
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&").replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">").replaceAll("&quot;", "\"")
                .replaceAll("\\s+", " ");
    }
}
