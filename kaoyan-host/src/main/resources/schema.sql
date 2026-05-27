-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    avatar VARCHAR(255),
    role VARCHAR(20) DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. 用户档案表
CREATE TABLE IF NOT EXISTS user_profile (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL REFERENCES users(id),
    target_school VARCHAR(100),
    target_major VARCHAR(100),
    exam_date DATE,
    study_start_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. 每日打卡表
CREATE TABLE IF NOT EXISTS check_in (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    check_date DATE NOT NULL,
    study_minutes INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, check_date)
);

-- 4. 知识点表（树形结构）
CREATE TABLE IF NOT EXISTS knowledge_point (
    id BIGSERIAL PRIMARY KEY,
    subject VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    parent_id BIGINT REFERENCES knowledge_point(id),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. 题目表
CREATE TABLE IF NOT EXISTS question (
    id BIGSERIAL PRIMARY KEY,
    subject VARCHAR(20) NOT NULL,
    type VARCHAR(20) NOT NULL,
    difficulty INTEGER NOT NULL CHECK (difficulty BETWEEN 1 AND 5),
    content TEXT NOT NULL,
    answer TEXT NOT NULL,
    analysis TEXT,
    knowledge_point_id BIGINT REFERENCES knowledge_point(id),
    year INTEGER,
    source VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. 题目选项表（选择题专用）
CREATE TABLE IF NOT EXISTS question_option (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    label VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    is_correct BOOLEAN DEFAULT FALSE
);

-- 7. 错题记录表
CREATE TABLE IF NOT EXISTS wrong_answer_record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    question_id BIGINT NOT NULL REFERENCES question(id),
    user_answer TEXT,
    is_resolved BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 8. 文档表
CREATE TABLE IF NOT EXISTS document (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size BIGINT,
    file_type VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PROCESSING',
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 9. 文档分块表（含向量列）
CREATE TABLE IF NOT EXISTS document_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    embedding vector(1536),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 向量索引（加速相似度检索）
CREATE INDEX IF NOT EXISTS idx_document_chunk_embedding
    ON document_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 10. 对话会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 11. 对话消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 16. AI 配置表（用户自主配置）
CREATE TABLE IF NOT EXISTS ai_config (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    api_key VARCHAR(500),
    api_url VARCHAR(500),
    chat_model VARCHAR(100),
    embedding_model VARCHAR(100),
    temperature DOUBLE PRECISION,
    max_tokens INTEGER,
    system_prompt TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 17. 知识点掌握度表
CREATE TABLE IF NOT EXISTS knowledge_mastery (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    knowledge_point_id BIGINT NOT NULL REFERENCES knowledge_point(id),
    correct_count INTEGER DEFAULT 0,
    total_count INTEGER DEFAULT 0,
    mastery_level DECIMAL(5,2) DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, knowledge_point_id)
);

-- 18. 用户题库表（记录用户通过拍照问答积累的题目及做题统计）
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

-- 题目向量嵌入列（用于相似题推荐；nullable，向量化失败时留空）
ALTER TABLE question ADD COLUMN IF NOT EXISTS embedding vector(1536);

-- 向量相似度索引（仅在有数据后生效）
CREATE INDEX IF NOT EXISTS idx_question_embedding
    ON question USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);

-- 消息图片列（若尚未存在）
ALTER TABLE chat_message ADD COLUMN IF NOT EXISTS image_base64 TEXT;

-- 19. 题目-知识点多对多关联表（支持一题多标签 + 权重/置信度 + 来源）
CREATE TABLE IF NOT EXISTS question_knowledge_point (
    question_id BIGINT NOT NULL REFERENCES question(id) ON DELETE CASCADE,
    knowledge_point_id BIGINT NOT NULL REFERENCES knowledge_point(id) ON DELETE CASCADE,
    weight DECIMAL(3,2) DEFAULT 1.00,           -- LLM 返回的置信度 0~1
    source VARCHAR(20) DEFAULT 'llm',           -- llm | manual | extracted
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (question_id, knowledge_point_id)
);

CREATE INDEX IF NOT EXISTS idx_qkp_question ON question_knowledge_point(question_id);
CREATE INDEX IF NOT EXISTS idx_qkp_kp ON question_knowledge_point(knowledge_point_id);

-- AI 配置表：增加 embedding 独立端点字段（embedding 通常与 chat 不同厂商，例如 chat=DeepSeek + embedding=Gemini）
ALTER TABLE ai_config ADD COLUMN IF NOT EXISTS embedding_api_url VARCHAR(500);
ALTER TABLE ai_config ADD COLUMN IF NOT EXISTS embedding_api_key VARCHAR(500);

-- question 表增加 embedding 状态列，便于批量补齐
ALTER TABLE question ADD COLUMN IF NOT EXISTS embedding_status VARCHAR(20) DEFAULT 'pending';
-- embedding_status: pending | success | failed | skipped

-- question 表增加标签状态列
ALTER TABLE question ADD COLUMN IF NOT EXISTS tagging_status VARCHAR(20) DEFAULT 'pending';
-- tagging_status: pending | success | failed

-- 20. 知识库表（一个用户可拥有多个知识库，每个知识库独立组织一组学习资料）
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_knowledge_base_user ON knowledge_base(user_id);

-- document 表：增加 knowledge_base_id 外键 + enabled 启用开关
ALTER TABLE document ADD COLUMN IF NOT EXISTS knowledge_base_id BIGINT
    REFERENCES knowledge_base(id) ON DELETE CASCADE;
ALTER TABLE document ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX IF NOT EXISTS idx_document_kb ON document(knowledge_base_id);

-- chat_session 表：增加 knowledge_base_id（对话绑定哪个 KB，null = 不启用 RAG）
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS knowledge_base_id BIGINT
    REFERENCES knowledge_base(id) ON DELETE SET NULL;

-- 21. 知识点收藏（用户长按图谱节点标记的"重点关注"）
CREATE TABLE IF NOT EXISTS knowledge_point_focus (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    knowledge_point_id BIGINT NOT NULL REFERENCES knowledge_point(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, knowledge_point_id)
);
CREATE INDEX IF NOT EXISTS idx_kp_focus_user ON knowledge_point_focus(user_id);

-- 22. 系统 API 配置表（管理员维护，按"环节"存储）
-- stage 取值: chat | multimodal | embedding | audio
-- 解析链：用户 ai_config → 本表 → application.yml
CREATE TABLE IF NOT EXISTS system_api_config (
    stage VARCHAR(32) PRIMARY KEY,
    api_url VARCHAR(500),
    api_key VARCHAR(500),
    model VARCHAR(100),
    temperature DOUBLE PRECISION,
    max_tokens INTEGER,
    system_prompt TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
