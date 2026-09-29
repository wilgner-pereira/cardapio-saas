-- Rename in place: keep role IDs and every existing user_roles association.
-- Never reuse ROLE_ADMIN for tenant access: old applications must fail closed.
UPDATE roles SET role_name = 'ROLE_PLATFORM_ADMIN' WHERE role_name = 'ROLE_ADMIN';
UPDATE roles SET role_name = 'ROLE_TENANT_ADMIN' WHERE role_name = 'ROLE_USER';
