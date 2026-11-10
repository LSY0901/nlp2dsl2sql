-- 会话记忆分层存储：短期在 Redis（快照 + 轮次），本脚本建长期 PG 表。
-- 前置：数据库已执行 ai_agent.sql（agent schema 与 pgvector 已就绪）。
CREATE EXTENSION IF NOT EXISTS vector;

-- 会话流水：每轮一行，写穿
CREATE TABLE agent.memory_session_archive (
    id                  BIGSERIAL PRIMARY KEY,
    session_id          VARCHAR(64) NOT NULL,
    user_id             VARCHAR(64),
    question            TEXT NOT NULL,
    effective_question  TEXT,
    dsl_json            TEXT,
    question_embedding  vector(1024),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_memory_archive_session ON agent.memory_session_archive(session_id);
CREATE INDEX idx_memory_archive_user ON agent.memory_session_archive(user_id, created_at);

-- 会话摘要：每会话一条，覆盖式 upsert
CREATE TABLE agent.memory_session_summary (
    id            BIGSERIAL PRIMARY KEY,
    session_id    VARCHAR(64) NOT NULL UNIQUE,
    user_id       VARCHAR(64),
    summary       TEXT NOT NULL,
    turn_count    INT,
    first_turn_at TIMESTAMPTZ,
    last_turn_at  TIMESTAMPTZ,
    summarized_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_memory_summary_user ON agent.memory_session_summary(user_id);

-- 用户画像：每用户一条，覆盖式 upsert
CREATE TABLE agent.memory_user_profile (
    user_id      VARCHAR(64) PRIMARY KEY,
    profile_json TEXT NOT NULL,
    summary_seen BIGINT NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
