ALTER TABLE estabelecimento ADD COLUMN IF NOT EXISTS endereco VARCHAR(255);

INSERT INTO estabelecimento (nome, slug, descricao, horario_funcionamento, telefone, endereco, email_contato, ativo, criado_em, atualizado_em)
SELECT 'Restaurante Aurora', 'restaurante', 'Comida brasileira caseira com pratos executivos e sobremesas clássicas.', '11:00 - 15:00', '(34) 3000-1001', 'Rua das Flores, 120 - Centro', 'contato@restaurante.test', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM estabelecimento WHERE slug = 'restaurante');

INSERT INTO estabelecimento (nome, slug, descricao, horario_funcionamento, telefone, endereco, email_contato, ativo, criado_em, atualizado_em)
SELECT 'Pizzaria Forno Alto', 'pizzaria', 'Pizzas artesanais assadas no forno com massas de fermentação lenta.', '18:00 - 23:30', '(34) 3000-1002', 'Av. Principal, 450 - Centro', 'contato@pizzaria.test', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM estabelecimento WHERE slug = 'pizzaria');

INSERT INTO estabelecimento (nome, slug, descricao, horario_funcionamento, telefone, endereco, email_contato, ativo, criado_em, atualizado_em)
SELECT 'Hamburgueria Brasa Burger', 'hamburgueria', 'Hambúrgueres smash, artesanais e acompanhamentos crocantes.', '18:00 - 00:00', '(34) 3000-1003', 'Rua Sete, 87 - Bairro Novo', 'contato@hamburgueria.test', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM estabelecimento WHERE slug = 'hamburgueria');

INSERT INTO estabelecimento (nome, slug, descricao, horario_funcionamento, telefone, endereco, email_contato, ativo, criado_em, atualizado_em)
SELECT 'Espetaria Ponto da Brasa', 'espetaria', 'Espetos, porções e pratos rápidos preparados na churrasqueira.', '17:00 - 23:00', '(34) 3000-1004', 'Rua da Praça, 33 - Santa Maria', 'contato@espetaria.test', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM estabelecimento WHERE slug = 'espetaria');

INSERT INTO estabelecimento (nome, slug, descricao, horario_funcionamento, telefone, endereco, email_contato, ativo, criado_em, atualizado_em)
SELECT 'Bar Jardim', 'bar', 'Drinks, cervejas, petiscos e pratos para compartilhar.', '17:00 - 01:00', '(34) 3000-1005', 'Alameda dos Ipês, 710 - Jardim', 'contato@bar.test', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM estabelecimento WHERE slug = 'bar');

INSERT INTO usuario (username, password, email, estabelecimento_id)
SELECT 'restaurante', '$2a$10$E/qfWuRaVShSG0d5bgJxmOb4gMz27FX0oMALKTtlbpB9t0sDl/H4G', 'dono@restaurante.test', e.id
FROM estabelecimento e
WHERE e.slug = 'restaurante'
  AND NOT EXISTS (SELECT 1 FROM usuario WHERE username = 'restaurante' OR email = 'dono@restaurante.test');

INSERT INTO usuario (username, password, email, estabelecimento_id)
SELECT 'pizzaria', '$2a$10$E/qfWuRaVShSG0d5bgJxmOb4gMz27FX0oMALKTtlbpB9t0sDl/H4G', 'dono@pizzaria.test', e.id
FROM estabelecimento e
WHERE e.slug = 'pizzaria'
  AND NOT EXISTS (SELECT 1 FROM usuario WHERE username = 'pizzaria' OR email = 'dono@pizzaria.test');

INSERT INTO usuario (username, password, email, estabelecimento_id)
SELECT 'hamburgueria', '$2a$10$E/qfWuRaVShSG0d5bgJxmOb4gMz27FX0oMALKTtlbpB9t0sDl/H4G', 'dono@hamburgueria.test', e.id
FROM estabelecimento e
WHERE e.slug = 'hamburgueria'
  AND NOT EXISTS (SELECT 1 FROM usuario WHERE username = 'hamburgueria' OR email = 'dono@hamburgueria.test');

INSERT INTO usuario (username, password, email, estabelecimento_id)
SELECT 'espetaria', '$2a$10$E/qfWuRaVShSG0d5bgJxmOb4gMz27FX0oMALKTtlbpB9t0sDl/H4G', 'dono@espetaria.test', e.id
FROM estabelecimento e
WHERE e.slug = 'espetaria'
  AND NOT EXISTS (SELECT 1 FROM usuario WHERE username = 'espetaria' OR email = 'dono@espetaria.test');

INSERT INTO usuario (username, password, email, estabelecimento_id)
SELECT 'bar', '$2a$10$E/qfWuRaVShSG0d5bgJxmOb4gMz27FX0oMALKTtlbpB9t0sDl/H4G', 'dono@bar.test', e.id
FROM estabelecimento e
WHERE e.slug = 'bar'
  AND NOT EXISTS (SELECT 1 FROM usuario WHERE username = 'bar' OR email = 'dono@bar.test');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM usuario u
JOIN roles r ON r.role_name = 'ROLE_USER'
WHERE u.username IN ('restaurante', 'pizzaria', 'hamburgueria', 'espetaria', 'bar')
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Prato Executivo', 'Arroz, feijão, bife acebolado, batata frita e salada.', 32.90, TRUE, 'Pratos', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'restaurante' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Prato Executivo');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Frango Grelhado', 'Filé de frango grelhado com arroz, legumes e purê.', 29.90, TRUE, 'Pratos', NULL, u.id, 1, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'restaurante' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Frango Grelhado');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Feijoada Individual', 'Feijoada completa com arroz, couve, farofa e laranja.', 38.90, TRUE, 'Especiais', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'restaurante' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Feijoada Individual');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Pudim da Casa', 'Pudim de leite condensado com calda de caramelo.', 12.90, TRUE, 'Sobremesas', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'restaurante' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Pudim da Casa');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Pizza Margherita', 'Molho de tomate, muçarela, manjericão e azeite.', 54.90, TRUE, 'Pizzas', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'pizzaria' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Pizza Margherita');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Pizza Calabresa', 'Calabresa fatiada, cebola, muçarela e orégano.', 58.90, TRUE, 'Pizzas', NULL, u.id, 1, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'pizzaria' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Pizza Calabresa');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Pizza Quatro Queijos', 'Muçarela, provolone, parmesão e gorgonzola.', 64.90, TRUE, 'Pizzas', NULL, u.id, 2, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'pizzaria' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Pizza Quatro Queijos');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Refrigerante 2L', 'Refrigerante gelado para acompanhar a pizza.', 13.90, TRUE, 'Bebidas', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'pizzaria' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Refrigerante 2L');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Smash Clássico', 'Pão brioche, blend bovino, queijo cheddar e molho da casa.', 27.90, TRUE, 'Burgers', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'hamburgueria' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Smash Clássico');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Brasa Bacon', 'Burger artesanal com bacon, cheddar, cebola roxa e barbecue.', 34.90, TRUE, 'Burgers', NULL, u.id, 1, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'hamburgueria' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Brasa Bacon');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Batata Rústica', 'Batata temperada com páprica e maionese verde.', 18.90, TRUE, 'Acompanhamentos', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'hamburgueria' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Batata Rústica');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Milkshake Chocolate', 'Milkshake cremoso de chocolate com calda.', 19.90, TRUE, 'Bebidas', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'hamburgueria' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Milkshake Chocolate');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Espeto de Alcatra', 'Espeto de alcatra grelhado na brasa.', 16.90, TRUE, 'Espetos', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'espetaria' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Espeto de Alcatra');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Espeto de Frango', 'Frango temperado com ervas e grelhado na hora.', 12.90, TRUE, 'Espetos', NULL, u.id, 1, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'espetaria' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Espeto de Frango');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Mandioca Frita', 'Mandioca cozida e frita, crocante por fora.', 21.90, TRUE, 'Porções', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'espetaria' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Mandioca Frita');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Vinagrete da Casa', 'Vinagrete fresco para acompanhar os espetos.', 7.90, TRUE, 'Acompanhamentos', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'espetaria' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Vinagrete da Casa');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Caipirinha de Limão', 'Cachaça, limão, açúcar e gelo.', 18.90, TRUE, 'Drinks', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'bar' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Caipirinha de Limão');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Cerveja Long Neck', 'Cerveja long neck gelada.', 11.90, TRUE, 'Bebidas', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'bar' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Cerveja Long Neck');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Isca de Frango', 'Tiras de frango empanadas com molho especial.', 32.90, TRUE, 'Petiscos', NULL, u.id, 0, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'bar' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Isca de Frango');

INSERT INTO produtos (nome, descricao, preco, ativo, categoria, imagem_url, usuario_id, ordem, estabelecimento_id)
SELECT 'Bolinho de Mandioca', 'Bolinho recheado com carne seca e queijo.', 29.90, TRUE, 'Petiscos', NULL, u.id, 1, e.id
FROM estabelecimento e JOIN usuario u ON u.estabelecimento_id = e.id
WHERE e.slug = 'bar' AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.estabelecimento_id = e.id AND p.nome = 'Bolinho de Mandioca');
