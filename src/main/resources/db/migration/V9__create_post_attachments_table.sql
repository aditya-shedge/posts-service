-- Create post_attachments table (one-to-one with posts)
CREATE TABLE post_attachments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL UNIQUE,
    url TEXT,
    public_id VARCHAR(255),
    filename VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    temp_path VARCHAR(500),
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_post_attachments_post
        FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
);

CREATE INDEX idx_post_attachments_post_id ON post_attachments(post_id);
CREATE INDEX idx_post_attachments_status ON post_attachments(status);

-- Remove attachment columns from posts table
ALTER TABLE posts DROP COLUMN IF EXISTS attachment;
ALTER TABLE posts DROP COLUMN IF EXISTS attachment_status;
ALTER TABLE posts DROP COLUMN IF EXISTS attachment_public_id;
ALTER TABLE posts DROP COLUMN IF EXISTS attachment_filename;
ALTER TABLE posts DROP COLUMN IF EXISTS attachment_temp_path;
ALTER TABLE posts DROP COLUMN IF EXISTS attachment_retry_count;
ALTER TABLE posts DROP COLUMN IF EXISTS attachment_next_retry_at;
