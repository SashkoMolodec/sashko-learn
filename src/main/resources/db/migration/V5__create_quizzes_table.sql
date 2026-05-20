CREATE TABLE quizzes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    embedding vector(1536),
    created_by_chat_id BIGINT NOT NULL,
    question_count INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_quizzes_embedding_hnsw ON quizzes
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_quizzes_created_by ON quizzes(created_by_chat_id);
