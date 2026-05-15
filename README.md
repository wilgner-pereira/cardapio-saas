# Cardapio API

API Spring Boot para cadastro de cardapios por usuario, com area administrativa autenticada, cardapio publico e upload de imagens para Supabase Storage.

## Stack

- Java 21
- Spring Boot
- Spring MVC
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- Supabase Storage
- Swagger/OpenAPI

## Como rodar localmente

### Opcao rapida, sem Docker

O perfil `dev` usa H2 em memoria por padrao, entao a API sobe sem depender de Postgres/Supabase:

```bash
mvn spring-boot:run
```

### Com Docker e Postgres local

1. Copie `.env.example` para `.env` se quiser customizar valores.
2. Suba banco e API:

```bash
docker compose up --build
```

3. Acesse:

- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui/index.html`
- Health check: `http://localhost:8080/actuator/health`

O Docker usa o perfil `local-postgres`, apontando a API para o Postgres do compose.

## Front-end React

O front-end fica em `frontend` e foi criado com React + Vite.

```bash
cd frontend
npm install
npm run dev
```

Rotas principais:

- `http://localhost:5173/cardapio/demo` exibe um cardapio demonstrativo.
- `http://localhost:5173/cardapio/{username}` consome `GET /public/{username}/cardapio`.
- `http://localhost:5173/login` autentica o estabelecimento.
- `http://localhost:5173/admin/produtos` abre o editor visual do cardapio autenticado.

Por padrao o front chama a API em `http://localhost:8080`. Para mudar isso, crie `frontend/.env` com:

```env
VITE_API_BASE_URL=http://localhost:8080
```

O painel administrativo usa cookies `HttpOnly` emitidos pelo back-end e envia CSRF automaticamente nas chamadas mutaveis. O Swagger continua podendo usar Bearer token para testes tecnicos.

### Com Supabase

No PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE="supabase"
$env:DATABASE_PASSWORD="sua-senha-do-banco"
$env:JWT_SECRET="troque-por-um-segredo-forte-com-32-caracteres-ou-mais"
$env:SUPABASE_KEY="sua-chave-do-supabase"
.\mvnw.cmd spring-boot:run
```

Se preferir informar tudo manualmente, configure tambem `DATABASE_URL`, `DATABASE_USERNAME`, `SUPABASE_URL` e `SUPABASE_BUCKET`.

Se estiver usando o Session Pooler por causa de rede IPv4, copie o host, porta e usuario da aba Pooler do Supabase:

```powershell
$env:SPRING_PROFILES_ACTIVE="supabase"
$env:DATABASE_URL="jdbc:postgresql://aws-1-us-west-1.pooler.supabase.com:5432/postgres"
$env:DATABASE_USERNAME="usuario-do-pooler"
$env:DATABASE_PASSWORD="sua-senha-do-banco"
$env:JWT_SECRET="troque-por-um-segredo-forte-com-32-caracteres-ou-mais"
$env:SUPABASE_KEY="sua-chave-do-supabase"
.\mvnw.cmd spring-boot:run
```

Nao use `DATABASE_URL` do Supabase junto com o perfil `dev`, porque `dev` e reservado para H2 em memoria.

## Seguranca

- Tokens JWT sao emitidos como cookies `HttpOnly`.
- A API tambem aceita `Authorization: Bearer <token>` para clientes tecnicos e Swagger.
- Endpoints administrativos exigem usuario com `ROLE_USER`.
- Requisicoes mutaveis protegidas por cookie precisam enviar token CSRF. Obtenha-o em `GET /auth/admin/csrf` e envie o valor no header `X-XSRF-TOKEN`.
- Em producao, use `COOKIE_SECURE=true`, HTTPS e um `JWT_SECRET` forte.

## Endpoints principais

| Metodo | Rota | Descricao |
|---|---|---|
| `POST` | `/auth/admin/register` | cria usuario |
| `POST` | `/auth/admin/login` | autentica e cria cookies |
| `POST` | `/auth/admin/refresh` | renova access token |
| `POST` | `/auth/admin/logout` | limpa cookies |
| `GET` | `/auth/admin/validate` | valida sessao |
| `GET` | `/auth/admin/csrf` | retorna token CSRF |
| `POST` | `/admin/produto` | cria produto |
| `PUT` | `/admin/produto/{id}` | atualiza produto |
| `PATCH` | `/admin/produto/{id}/status` | ativa ou desativa produto |
| `DELETE` | `/admin/produto/{id}` | exclui produto |
| `GET` | `/admin/produto` | lista produtos do usuario |
| `GET` | `/public/{username}/cardapio` | lista produtos ativos publicos |
| `POST` | `/storage/upload` | envia imagem ao Supabase Storage |

## Variaveis de ambiente

Veja `.env.example`.

Variaveis obrigatorias para producao:

- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SECRET`
- `SUPABASE_URL`
- `SUPABASE_KEY`
- `SUPABASE_BUCKET`
- `CORS_ALLOWED_ORIGINS`

Exemplo de banco Supabase:

```env
DATABASE_URL=jdbc:postgresql://db.hjigxbjeeecuehlbxxny.supabase.co:5432/postgres
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=sua-senha-do-banco
```

O painel do Supabase tambem mostra uma URL no formato `postgresql://postgres:[PASSWORD]@...`. Para esta API Spring Boot, use o formato JDBC acima. Se sua rede for apenas IPv4, use o Session Pooler do Supabase no lugar do host direto `db.hjigxbjeeecuehlbxxny.supabase.co`.

## Banco de dados

O schema e versionado com Flyway em `src/main/resources/db/migration`. O Hibernate valida o schema, mas nao altera tabelas automaticamente.

## Testes

```bash
mvn test
```

Os testes usam perfil `test`, H2 em memoria e migrations Flyway.

## Front-end

Nao ha front-end React versionado neste repositorio. Para integrar um SPA, configure `CORS_ALLOWED_ORIGINS`, use `credentials: "include"` para cookies e leia `GET /auth/admin/csrf` antes de chamadas `POST`, `PUT`, `PATCH` e `DELETE` autenticadas.
