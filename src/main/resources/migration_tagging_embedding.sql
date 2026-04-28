-- ============================================================
-- 迁移脚本：标签 + 向量化扩展
-- 幂等执行，可重复运行
-- ============================================================

-- 1. 确保 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. user_question 表（如尚未创建）
CREATE TABLE IF NOT EXISTS user_question (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    source_session_id BIGINT REFERENCES chat_session(id) ON DELETE SET NULL,
    last_attempt_at TIMESTAMP,
    correct_count INTEGER DEFAULT 0,
    total_attempts INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, question_id)
);

-- 3. question_knowledge_point 多对多表
CREATE TABLE IF NOT EXISTS question_knowledge_point (
    question_id BIGINT NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    knowledge_point_id BIGINT NOT NULL REFERENCES knowledge_point(id) ON DELETE CASCADE,
    weight DECIMAL(3,2) DEFAULT 1.00,
    source VARCHAR(20) DEFAULT 'llm',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (question_id, knowledge_point_id)
);

CREATE INDEX IF NOT EXISTS idx_qkp_question ON question_knowledge_point(question_id);
CREATE INDEX IF NOT EXISTS idx_qkp_kp      ON question_knowledge_point(knowledge_point_id);

-- 4. question 表扩展列
ALTER TABLE question ADD COLUMN IF NOT EXISTS embedding vector(1536);
ALTER TABLE question ADD COLUMN IF NOT EXISTS embedding_status VARCHAR(20) DEFAULT 'pending';
ALTER TABLE question ADD COLUMN IF NOT EXISTS tagging_status   VARCHAR(20) DEFAULT 'pending';

-- 向量检索索引（必须有数据才能构建 ivfflat，数据为空时可能报 warning 但不影响）
CREATE INDEX IF NOT EXISTS idx_question_embedding
    ON question USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);

-- 5. document_chunk 向量索引（确保存在）
CREATE INDEX IF NOT EXISTS idx_document_chunk_embedding
    ON document_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 6. ai_config 增加 embedding 独立端点字段
ALTER TABLE ai_config ADD COLUMN IF NOT EXISTS embedding_api_url VARCHAR(500);
ALTER TABLE ai_config ADD COLUMN IF NOT EXISTS embedding_api_key VARCHAR(500);

-- 7. chat_message 图片列
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS image_base64 TEXT;

-- ============================================================
-- 验证输出
-- ============================================================
\echo '=== question columns ==='
SELECT column_name, data_type FROM information_schema.columns
 WHERE table_name='question' AND column_name IN ('embedding','embedding_status','tagging_status')
 ORDER BY column_name;

\echo '=== ai_config columns ==='
SELECT column_name, data_type FROM information_schema.columns
 WHERE table_name='ai_config' AND column_name LIKE 'embedding_api%'
 ORDER BY column_name;

\echo '=== new tables ==='
SELECT table_name FROM information_schema.tables
 WHERE table_name IN ('user_question','question_knowledge_point')
 ORDER BY table_name;

\echo '=== new indexes ==='
SELECT indexname FROM pg_indexes
 WHERE indexname IN ('idx_qkp_question','idx_qkp_kp','idx_question_embedding','idx_document_chunk_embedding')
 ORDER BY indexname;
