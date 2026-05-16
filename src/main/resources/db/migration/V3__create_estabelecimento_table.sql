-- Criar tabela estabelecimento
CREATE TABLE IF NOT EXISTS estabelecimento (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    slug VARCHAR(50) NOT NULL UNIQUE,
    descricao VARCHAR(500),
    horario_funcionamento VARCHAR(100),
    telefone VARCHAR(20),
    email_contato VARCHAR(255),
    logo_url VARCHAR(2048),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Adicionar coluna estabelecimento_id em usuario
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS estabelecimento_id BIGINT;

-- Criar um estabelecimento padrao para usuarios ja existentes antes da migracao
INSERT INTO estabelecimento (nome, slug, descricao, ativo, criado_em, atualizado_em)
SELECT u.username,
       LOWER(u.username),
       'Cardápio de ' || u.username,
       TRUE,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM usuario u
WHERE u.estabelecimento_id IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM estabelecimento e
      WHERE e.slug = LOWER(u.username)
  );

UPDATE usuario
SET estabelecimento_id = (
    SELECT e.id
    FROM estabelecimento e
    WHERE e.slug = LOWER(usuario.username)
)
WHERE estabelecimento_id IS NULL;

-- Adicionar constraint FK
ALTER TABLE usuario
ADD CONSTRAINT fk_usuario_estabelecimento
FOREIGN KEY (estabelecimento_id) REFERENCES estabelecimento (id) ON DELETE CASCADE;

ALTER TABLE usuario ALTER COLUMN estabelecimento_id SET NOT NULL;

-- Adicionar índice
CREATE INDEX IF NOT EXISTS idx_usuario_estabelecimento ON usuario (estabelecimento_id);

-- Adicionar coluna ordem em produtos
ALTER TABLE produtos ADD COLUMN IF NOT EXISTS ordem INT DEFAULT 0;
ALTER TABLE produtos ADD COLUMN IF NOT EXISTS estabelecimento_id BIGINT;
ALTER TABLE produtos ALTER COLUMN ordem SET NOT NULL;

UPDATE produtos
SET estabelecimento_id = (
    SELECT u.estabelecimento_id
    FROM usuario u
    WHERE u.id = produtos.usuario_id
)
WHERE estabelecimento_id IS NULL;

-- Adicionar constraint FK para produtos
ALTER TABLE produtos
ADD CONSTRAINT fk_produtos_estabelecimento
FOREIGN KEY (estabelecimento_id) REFERENCES estabelecimento (id) ON DELETE CASCADE;

ALTER TABLE produtos ALTER COLUMN estabelecimento_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_produtos_estabelecimento ON produtos (estabelecimento_id);
CREATE INDEX IF NOT EXISTS idx_produtos_estabelecimento_categoria ON produtos (estabelecimento_id, categoria);
CREATE INDEX IF NOT EXISTS idx_produtos_estabelecimento_ativo ON produtos (estabelecimento_id, ativo);
CREATE INDEX IF NOT EXISTS idx_produtos_estabelecimento_ordem ON produtos (estabelecimento_id, categoria, ordem);
