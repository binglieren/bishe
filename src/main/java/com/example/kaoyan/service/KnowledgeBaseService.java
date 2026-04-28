package com.example.kaoyan.service;

import com.example.kaoyan.dto.KnowledgeBaseDTO;
import com.example.kaoyan.dto.KnowledgeBaseRequest;
import com.example.kaoyan.entity.Document;
import com.example.kaoyan.entity.KnowledgeBase;
import com.example.kaoyan.repository.DocumentChunkRepository;
import com.example.kaoyan.repository.DocumentRepository;
import com.example.kaoyan.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务：多知识库管理 + 老数据自动迁移
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;

    /**
     * 获取用户的全部知识库（含文档统计）。
     * 首次调用时：若用户无知识库但有历史文档，自动建"默认知识库"并迁移。
     */
    @Transactional
    public List<KnowledgeBaseDTO> listForUser(Long userId) {
        List<KnowledgeBase> kbs = knowledgeBaseRepository.findByUserIdOrderByCreatedAtAsc(userId);

        // 老数据迁移：如果没有任何 KB，但有文档（knowledge_base_id IS NULL），
        // 自动创建"默认知识库"并迁入。
        if (kbs.isEmpty()) {
            List<Document> orphans = documentRepository.findByUserIdAndKnowledgeBaseIdIsNull(userId);
            if (!orphans.isEmpty()) {
                KnowledgeBase defaultKb = new KnowledgeBase();
                defaultKb.setUserId(userId);
                defaultKb.setName("默认知识库");
                defaultKb.setDescription("系统自动创建，包含迁移自早期版本的全部资料");
                knowledgeBaseRepository.save(defaultKb);
                documentRepository.migrateOrphansToKb(userId, defaultKb.getId());
                kbs = knowledgeBaseRepository.findByUserIdOrderByCreatedAtAsc(userId);
            }
        }

        List<KnowledgeBaseDTO> result = new ArrayList<>(kbs.size());
        for (KnowledgeBase kb : kbs) {
            result.add(toDto(kb));
        }
        return result;
    }

    @Transactional
    public KnowledgeBaseDTO create(Long userId, KnowledgeBaseRequest request) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUserId(userId);
        kb.setName(request.getName().trim());
        kb.setDescription(request.getDescription());
        knowledgeBaseRepository.save(kb);
        return toDto(kb);
    }

    @Transactional
    public KnowledgeBaseDTO update(Long userId, Long kbId, KnowledgeBaseRequest request) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(kbId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作此知识库");
        }
        kb.setName(request.getName().trim());
        kb.setDescription(request.getDescription());
        knowledgeBaseRepository.save(kb);
        return toDto(kb);
    }

    /**
     * 删除知识库：同时删除其下所有文档 + chunk。
     */
    @Transactional
    public void delete(Long userId, Long kbId) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(kbId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作此知识库");
        }
        List<Document> docs = documentRepository.findByUserIdAndKnowledgeBaseIdOrderByUploadTimeDesc(userId, kbId);
        for (Document doc : docs) {
            documentChunkRepository.deleteByDocumentId(doc.getId());
        }
        documentRepository.deleteAll(docs);
        knowledgeBaseRepository.delete(kb);
    }

    public KnowledgeBase requireOwned(Long userId, Long kbId) {
        KnowledgeBase kb = knowledgeBaseRepository.findById(kbId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在"));
        if (!kb.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作此知识库");
        }
        return kb;
    }

    private KnowledgeBaseDTO toDto(KnowledgeBase kb) {
        KnowledgeBaseDTO dto = new KnowledgeBaseDTO();
        dto.setId(kb.getId());
        dto.setName(kb.getName());
        dto.setDescription(kb.getDescription());
        dto.setDocumentCount(documentRepository.countByKnowledgeBaseId(kb.getId()));
        dto.setEnabledCount(documentRepository.countByKnowledgeBaseIdAndEnabledTrue(kb.getId()));
        dto.setCreatedAt(kb.getCreatedAt());
        dto.setUpdatedAt(kb.getUpdatedAt());
        return dto;
    }
}
