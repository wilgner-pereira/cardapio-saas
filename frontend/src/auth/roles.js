export const PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";
export const TENANT_ADMIN = "ROLE_TENANT_ADMIN";

export function getAdminHome(roles = []) {
  if (roles.includes(PLATFORM_ADMIN)) return "/plataforma";
  if (roles.includes(TENANT_ADMIN)) return "/painel/produtos";
  return null;
}
