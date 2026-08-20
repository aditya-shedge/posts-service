-- Seed moderator user (password: password123)
INSERT INTO users (id, username, password_hash, email, role, created_at, updated_at) VALUES
    ('33333333-3333-3333-3333-333333333333', 'moderator1', '$2a$10$EB/E22.Y7iOvjytyyDT8Ie1qJ4GxZ8RXNazhtLZFMU83kOB4JCGQ6', 'moderator1@school.edu', 'MODERATOR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Add moderator role to user_roles
INSERT INTO user_roles (user_id, role) VALUES
    ('33333333-3333-3333-3333-333333333333', 'MODERATOR')
ON CONFLICT DO NOTHING;

-- Give teacher1 both TEACHER and MODERATOR roles for testing
INSERT INTO user_roles (user_id, role) VALUES
    ('11111111-1111-1111-1111-111111111111', 'MODERATOR')
ON CONFLICT DO NOTHING;
