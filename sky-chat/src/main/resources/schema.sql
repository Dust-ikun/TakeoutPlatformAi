-- sky-chat 业务表（PGVector 向量表由 Spring AI 自动建：vector_store）
CREATE TABLE IF NOT EXISTS knowledge_doc (
    id            BIGSERIAL PRIMARY KEY,
    doc_name      VARCHAR(128) NOT NULL UNIQUE,
    doc_type      VARCHAR(16)  NOT NULL,          -- dish | rule
    fingerprint   VARCHAR(64)  NOT NULL,          -- 内容 SHA-256，重复导入幂等
    status        VARCHAR(16)  NOT NULL,          -- PROCESSING | SUCCESS | FAILED
    chunk_count   INT          NOT NULL DEFAULT 0,
    vector_ids    TEXT,                           -- 本文档产生的 chunk id JSON 数组，重导入时先删
    error_msg     TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);
