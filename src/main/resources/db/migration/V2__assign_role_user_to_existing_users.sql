INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM usuario u
JOIN roles r ON r.role_name = 'ROLE_USER'
WHERE NOT EXISTS (
    SELECT 1
    FROM user_roles ur
    WHERE ur.user_id = u.id
      AND ur.role_id = r.id
);
