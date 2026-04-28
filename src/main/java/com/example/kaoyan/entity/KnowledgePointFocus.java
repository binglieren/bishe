package com.example.kaoyan.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户对知识点的"重点关注"标记。复合主键 (user_id, knowledge_point_id)。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "knowledge_point_focus")
@IdClass(KnowledgePointFocus.PK.class)
public class KnowledgePointFocus {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "knowledge_point_id")
    private Long knowledgePointId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PK implements Serializable {
        private Long userId;
        private Long knowledgePointId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(userId, pk.userId)
                    && Objects.equals(knowledgePointId, pk.knowledgePointId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, knowledgePointId);
        }
    }
}
