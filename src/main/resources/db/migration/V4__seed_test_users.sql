-- Seed test users (password for all: password123)
-- BCrypt hash generated with BCryptPasswordEncoder().encode("password123")
INSERT INTO users (id, username, password_hash, email, role, created_at, updated_at) VALUES
    ('11111111-1111-1111-1111-111111111111', 'teacher1', '$2a$10$EB/E22.Y7iOvjytyyDT8Ie1qJ4GxZ8RXNazhtLZFMU83kOB4JCGQ6', 'teacher1@school.edu', 'TEACHER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('22222222-2222-2222-2222-222222222222', 'teacher2', '$2a$10$EB/E22.Y7iOvjytyyDT8Ie1qJ4GxZ8RXNazhtLZFMU83kOB4JCGQ6', 'teacher2@school.edu', 'TEACHER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
