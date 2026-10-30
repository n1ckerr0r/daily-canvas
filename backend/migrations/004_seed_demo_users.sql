ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'user'
        CHECK (role IN ('admin', 'user'));

-- Local development accounts. Never use these credentials in a deployed environment.
UPDATE users
SET display_name = 'Администратор',
    password_hash = '$2a$10$6HmGrga2EB9vjYUXw6YID.ZGc50anF0UsO7NEgpz75CfDAW6LaWi6',
    role = 'admin'
WHERE id = 'user-1';

INSERT INTO users (id, email, display_name, password_hash, role)
VALUES (
    'user-example',
    'example@dailycanvas.local',
    'Пример пользователя',
    '$2a$10$DMrxzzlQz5O79PcsauBmLuHipUoc08SR28Exn5H7jUc4MdB3hVcGC',
    'user'
)
ON CONFLICT (email) DO UPDATE
SET display_name = EXCLUDED.display_name,
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role;

INSERT INTO user_settings (user_id, selected_collections, notifications_enabled, notification_time, notification_time_zone)
VALUES ('user-example', ARRAY['russian', 'world'], TRUE, '20:00', 'Europe/Moscow')
ON CONFLICT (user_id) DO NOTHING;
