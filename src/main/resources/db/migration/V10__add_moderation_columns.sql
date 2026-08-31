-- Add moderation audit columns to posts table
ALTER TABLE posts ADD COLUMN moderation_status VARCHAR(20);
ALTER TABLE posts ADD COLUMN moderation_reason TEXT;
ALTER TABLE posts ADD COLUMN moderated_at TIMESTAMP;
