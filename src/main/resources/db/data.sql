INSERT INTO sys_user (
    username,
    password_hash,
    display_name,
    role,
    status,
    created_at,
    updated_at
)
SELECT
    'admin',
    '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918',
    'admin',
    'ADMIN',
    'ENABLE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user WHERE username = 'admin'
);
