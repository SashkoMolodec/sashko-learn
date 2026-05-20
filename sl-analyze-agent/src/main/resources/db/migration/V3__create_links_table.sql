CREATE TABLE links (
    from_id UUID NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    to_id UUID NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
    label VARCHAR(50) NOT NULL DEFAULT 'RELATED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (from_id, to_id, label)
);

CREATE INDEX idx_links_from_id ON links(from_id);
CREATE INDEX idx_links_to_id ON links(to_id);

ALTER TABLE links ADD CONSTRAINT check_no_self_reference
    CHECK (from_id != to_id);
