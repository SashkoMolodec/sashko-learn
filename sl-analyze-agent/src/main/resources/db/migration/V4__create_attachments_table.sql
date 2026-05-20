CREATE TABLE attachments (
    file_name VARCHAR(500) PRIMARY KEY,
    note_id UUID NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    file_path VARCHAR(1000) NOT NULL,
    description TEXT,
    embedding vector(1536),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_attachments_note_id ON attachments(note_id);
CREATE INDEX idx_attachments_embedding ON attachments USING hnsw (embedding vector_cosine_ops);
