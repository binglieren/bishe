package com.example.kaoyan.repository;

import com.example.kaoyan.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    /**
     * 获取最近的 N 条消息（用于构建上下文）
     */
    List<ChatMessage> findTop10BySessionIdOrderByCreatedAtDesc(Long sessionId);
}
