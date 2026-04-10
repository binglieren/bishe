-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    avatar VARCHAR(255),
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

-- 12. 考试表
CREATE TABLE IF NOT EXISTS exam (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    subject VARCHAR(20) NOT NULL,
    duration_minutes INTEGER NOT NULL,
    total_score INTEGER NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 13. 考试-题目关联表
CREATE TABLE IF NOT EXISTS exam_question (
    id BIGSERIAL PRIMARY KEY,
    exam_id BIGINT NOT NULL REFERENCES exam(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES question(id),
    score INTEGER NOT NULL,
    sort_order INTEGER DEFAULT 0
);

-- 14. 考试记录表
CREATE TABLE IF NOT EXISTS exam_record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    exam_id BIGINT NOT NULL REFERENCES exam(id),
    score INTEGER,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(20) DEFAULT 'IN_PROGRESS',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 15. 考试答题表
CREATE TABLE IF NOT EXISTS exam_answer (
    id BIGSERIAL PRIMARY KEY,
    record_id BIGINT NOT NULL REFERENCES exam_record(id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES question(id),
    user_answer TEXT,
    is_correct BOOLEAN,
    score INTEGER DEFAULT 0
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
