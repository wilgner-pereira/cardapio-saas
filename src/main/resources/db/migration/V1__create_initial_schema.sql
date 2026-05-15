CREATE TABLE IF NOT EXISTS usuario (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_usuario FOREIGN KEY (user_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS produtos (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(60) NOT NULL,
    descricao VARCHAR(300) NOT NULL,
    preco NUMERIC(10, 2) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    categoria VARCHAR(50) NOT NULL,
    imagem_url VARCHAR(2048),
    usuario_id BIGINT NOT NULL,
    CONSTRAINT chk_produtos_preco_nao_negativo CHECK (preco >= 0),
    CONSTRAINT fk_produtos_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_produtos_usuario ON produtos (usuario_id);
CREATE INDEX IF NOT EXISTS idx_produtos_usuario_categoria ON produtos (usuario_id, categoria);
CREATE INDEX IF NOT EXISTS idx_produtos_usuario_ativo ON produtos (usuario_id, ativo);
CREATE INDEX IF NOT EXISTS idx_produtos_publico ON produtos (usuario_id, categoria, ativo);

INSERT INTO roles (role_name)
SELECT 'ROLE_USER'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_name = 'ROLE_USER');

INSERT INTO roles (role_name)
SELECT 'ROLE_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_name = 'ROLE_ADMIN');
