-- Preserve the configured sequence and repair duplicate/inconsistent ranks across categories.
CREATE TEMPORARY TABLE category_order_backfill AS
SELECT estabelecimento_id, categoria,
       CAST(ROW_NUMBER() OVER (
           PARTITION BY estabelecimento_id ORDER BY MIN(categoria_ordem), categoria
       ) - 1 AS INTEGER) AS nova_ordem
FROM produtos
GROUP BY estabelecimento_id, categoria;

UPDATE produtos
SET categoria_ordem = (
    SELECT b.nova_ordem FROM category_order_backfill b
    WHERE b.estabelecimento_id = produtos.estabelecimento_id
      AND b.categoria = produtos.categoria
);

DROP TABLE category_order_backfill;
