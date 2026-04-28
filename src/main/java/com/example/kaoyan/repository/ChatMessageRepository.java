package com.example.kaoyan.repository;

import com.example.kaoyan.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    /**
     * 获取最近的 N 条消息（用于构建上下文）
     */
    List<ChatMessage> findTop10BySessionIdOrderByCreatedAtDesc(Long sessionId);

    /** 最新一条消息（用于会话列表预览） */
    Optional<ChatMessage> findFirstBySessionIdOrderByCreatedAtDesc(Long sessionId);

    /** 统计会话消息数 */
    Integer countBySessionId(Long sessionId);
}
