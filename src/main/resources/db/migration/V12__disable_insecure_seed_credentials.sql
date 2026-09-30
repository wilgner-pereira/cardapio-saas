-- Preserve accounts, roles and business data, including databases already at V4-V11.
-- This deliberately is not a BCrypt hash: no password can authenticate this marker.
UPDATE usuario
SET password = '!disabled-v4-seed'
WHERE password = '$2a$10$E/qfWuRaVShSG0d5bgJxmOb4gMz27FX0oMALKTtlbpB9t0sDl/H4G'
  AND username IN ('restaurante', 'pizzaria', 'hamburgueria', 'espetaria', 'bar')
  AND email = 'dono@' || username || '.test'
  AND EXISTS (
      SELECT 1 FROM estabelecimento e
      WHERE e.id = usuario.estabelecimento_id AND e.slug = usuario.username
  );
