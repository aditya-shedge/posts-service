-- Migrate existing roles from users table to user_roles table
INSERT INTO user_roles (user_id, role)
SELECT id, role FROM users
ON CONFLICT DO NOTHING;
