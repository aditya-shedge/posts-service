-- Add columns for attachment metadata and retry mechanism
ALTER TABLE posts ADD COLUMN attachment_status VARCHAR(20);
ALTER TABLE posts ADD COLUMN attachment_public_id VARCHAR(255);
ALTER TABLE posts ADD COLUMN attachment_filename VARCHAR(255);
ALTER TABLE posts ADD COLUMN attachment_temp_path VARCHAR(500);
ALTER TABLE posts ADD COLUMN attachment_retry_count INTEGER DEFAULT 0;
ALTER TABLE posts ADD COLUMN attachment_next_retry_at TIMESTAMP;
