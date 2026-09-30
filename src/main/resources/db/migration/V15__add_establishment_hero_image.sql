-- Existing establishments keep the theme's visual fallback; logos remain independent.
ALTER TABLE estabelecimento ADD COLUMN IF NOT EXISTS hero_image_url VARCHAR(2048);
