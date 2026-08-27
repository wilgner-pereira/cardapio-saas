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

## Seguranca & Producao

- **Autenticacao Segura:** Tokens JWT emitidos em cookies `HttpOnly` com flags `Secure` e `SameSite` configuradas.
- **Headers HTTP:** HSTS obrigatório, Anti-Clickjacking (`X-Frame-Options: DENY`), Anti-MIME Sniffing (`X-Content-Type-Options: nosniff`) e `Referrer-Policy`.
- **Validacao de Uploads:** Inspecao de *Magic Bytes* (assinatura binaria) para impedir upload de executaveis/scripts disfarçados de imagem.
- **Multi-tenancy:** Consultas e mutacoes amarradas exclusivamente ao `estabelecimento_id` do usuario autenticado, prevenindo IDOR.
- **Bootstrap de Administrador:** Criação segura via variáveis de ambiente no boot (`APP_BOOTSTRAP_ADMIN_ENABLED=true`) ou via endpoint protegido condicionalmente por `APP_SETUP_ADMIN_ENABLED=true`.

## Endpoints principais

| Metodo | Rota | Autenticacao | Descricao |
|---|---|---|---|
| `POST` | `/auth/login` | Publico | Autentica e emite cookies `HttpOnly` |
| `POST` | `/auth/refresh` | Publico (via cookie) | Renova o access token |
| `POST` | `/auth/logout` | Publico | Invalida sessao e limpa cookies |
| `GET` | `/auth/validate` | Autenticado | Valida token/sessao atual |
| `GET` | `/auth/csrf` | Publico | Retorna token CSRF |
| `POST` | `/setup/admin` | Flag `APP_SETUP_ADMIN_ENABLED` | Cria primeiro admin inicial da plataforma |
| `GET` | `/painel/estabelecimento` | `ROLE_USER` / `ROLE_ADMIN` | Obtem dados do estabelecimento do usuario |
| `PUT` | `/painel/estabelecimento` | `ROLE_USER` / `ROLE_ADMIN` | Atualiza dados do estabelecimento |
| `POST` | `/painel/produtos` | `ROLE_USER` / `ROLE_ADMIN` | Cadastra novo produto |
| `PUT` | `/painel/produtos/{id}` | `ROLE_USER` / `ROLE_ADMIN` | Atualiza produto do estabelecimento |
| `PATCH` | `/painel/produtos/{id}/status` | `ROLE_USER` / `ROLE_ADMIN` | Ativa ou desativa produto |
| `PATCH` | `/painel/produtos/{id}/ordem` | `ROLE_USER` / `ROLE_ADMIN` | Altera ordem do produto |
| `DELETE` | `/painel/produtos/{id}` | `ROLE_USER` / `ROLE_ADMIN` | Exclui produto |
| `GET` | `/painel/produtos` | `ROLE_USER` / `ROLE_ADMIN` | Lista produtos do estabelecimento |
| `POST` | `/painel/storage/upload` | `ROLE_USER` / `ROLE_ADMIN` | Envia imagem validada ao storage |
| `GET` | `/public/{slug}/cardapio` | Publico | Visualizacao do cardapio do estabelecimento |
| `GET` | `/public/{slug}/cardapio/info` | Publico | Informacoes publicas do estabelecimento |
| `GET` | `/public/storage/{fileName}` | Publico | Proxy seguro de download de imagem |
| `POST` | `/plataforma/estabelecimentos` | `ROLE_ADMIN` | Cadastro administrativo de novos clientes |
| `GET` | `/plataforma/estabelecimentos` | `ROLE_ADMIN` | Listagem geral de estabelecimentos |

## Variaveis de ambiente para Producao

Veja `.env.example`.

Variaveis obrigatorias para producao:

- `SPRING_PROFILES_ACTIVE=supabase` (ou `prod`)
- `DATABASE_URL=jdbc:postgresql://host:5432/postgres?sslmode=require`
- `DATABASE_USERNAME=usuario`
- `DATABASE_PASSWORD=senha-forte`
- `JWT_SECRET=chave-criptografica-hex-longa-e-segura`
- `COOKIE_SECURE=true`
- `COOKIE_SAME_SITE=Strict`
- `CORS_ALLOWED_ORIGINS=https://seu-front-end.com.br`
- `SUPABASE_URL=https://seu-projeto.supabase.co`
- `SUPABASE_KEY=sua-service-key`
- `SUPABASE_BUCKET=cardapio-imagens`
- `APP_SETUP_ADMIN_ENABLED=false`

## Testes

```bash
.\mvnw.cmd test
```

Os testes automatizados cobrem autenticacao, autorizacao, headers HTTP de seguranca, CRUD multi-tenant e migrations Flyway.
