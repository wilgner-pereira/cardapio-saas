ALTER TABLE produtos ADD COLUMN IF NOT EXISTS categoria_ordem INT;

UPDATE produtos p
SET categoria_ordem = CAST((
    SELECT COUNT(DISTINCT p2.categoria)
    FROM produtos p2
    WHERE p2.estabelecimento_id = p.estabelecimento_id
      AND p2.categoria < p.categoria
) AS INTEGER)
WHERE p.categoria_ordem IS NULL;

ALTER TABLE produtos ALTER COLUMN categoria_ordem SET DEFAULT 0;
ALTER TABLE produtos ALTER COLUMN categoria_ordem SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_produtos_estabelecimento_categoria_ordem
    ON produtos (estabelecimento_id, categoria_ordem, categoria, ordem);
