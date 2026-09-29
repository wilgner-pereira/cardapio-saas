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
$env:DATABASE_URL="jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require"
$env:DATABASE_USERNAME="postgres.<project-ref>"
$env:DATABASE_PASSWORD="sua-senha-do-banco"
$env:JWT_SECRET="troque-por-um-segredo-forte-com-32-caracteres-ou-mais"
$env:SUPABASE_URL="https://<project-ref>.supabase.co"
$env:SUPABASE_KEY="sua-chave-do-supabase"
.\mvnw.cmd spring-boot:run
```

`DATABASE_URL`, `DATABASE_USERNAME` e `SUPABASE_URL` sao obrigatorias: nao ha projeto Supabase padrao. Configure tambem `SUPABASE_BUCKET` se o bucket tiver outro nome.

Se estiver usando o Session Pooler por causa de rede IPv4, copie o host, porta e usuario da aba Pooler do Supabase:

```powershell
$env:SPRING_PROFILES_ACTIVE="supabase"
$env:DATABASE_URL="jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require"
$env:DATABASE_USERNAME="usuario-do-pooler"
$env:SUPABASE_URL="https://<project-ref>.supabase.co"
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
  O bootstrap cria somente contas novas e aborta a inicialização se o username ou email já estiver cadastrado, inclusive para administradores. Após o primeiro cadastro, configure `APP_BOOTSTRAP_ADMIN_ENABLED=false` antes de reiniciar. A criação da conta e do estabelecimento administrativo é transacional.
  Login e refresh entregam os JWTs exclusivamente pelos cookies `HttpOnly`; o JSON contém apenas a mensagem e os metadados da sessão. Clientes que usam Bearer precisam obter o token pelo `Set-Cookie` em um cliente HTTP autorizado a ler esse cabeçalho; o frontend continua usando cookies com `credentials: "include"`.

## Endpoints principais

As roles sao `ROLE_PLATFORM_ADMIN` (plataforma) e `ROLE_TENANT_ADMIN` (painel do estabelecimento). A migration V10 renomeia as antigas `ROLE_ADMIN` e `ROLE_USER`, respectivamente, preservando IDs e vinculos; nenhuma role antiga e reutilizada com outro significado. Um administrador de plataforma so acessa o painel se tambem possuir explicitamente a role de tenant. O JWT continua contendo apenas a identidade e o tipo; as permissoes sao recarregadas do banco a cada requisicao.

Para implantar a V10, pare as instancias antigas antes de iniciar a nova versao com Flyway. Nao misture versoes antigas e novas durante a migration: as antigas deixarao de reconhecer as roles renomeadas. As migrations anteriores nao foram modificadas. Nao execute esta migration manualmente alem do Flyway.

O setup continua desabilitado por padrao, inclusive em `prod` e `supabase`. Ao habilitar `APP_SETUP_ADMIN_ENABLED=true`, configure um segredo aleatorio `APP_SETUP_ADMIN_TOKEN` com pelo menos 32 caracteres; sem ele, a inicializacao falha. O operador deve obter `/auth/csrf`, manter os cookies e enviar `POST /setup/admin` com o token no header indicado pelo JSON (`X-CSRF-TOKEN`) e o segredo em `X-Setup-Token`. Nao coloque o segredo em variaveis `VITE_*`, URLs ou storage do navegador. Depois do primeiro administrador, o endpoint responde 409 inclusive para chamadas concorrentes. Desabilite o setup depois de usa-lo.

Logout exige CSRF mesmo com um header Bearer. O cliente obtem o token com mascaramento XOR de `/auth/csrf` e o mantem em memoria, enviando-o em `X-CSRF-TOKEN`; isso permite API e frontend em hosts diferentes autorizados por CORS. O mecanismo existente de ler o cookie XSRF-TOKEN e enviar seu valor bruto em `X-XSRF-TOKEN` tambem permanece disponivel. Nao troque as duas representacoes de token entre os headers. Cookies HttpOnly, Secure e SameSite de autenticacao foram preservados. A integracao SPA segue o [modelo CSRF do Spring Security](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

Os limites padrao por IP, por instancia, em janelas de um minuto sao: login 5, refresh 30, setup 5 e leituras `/public/**` 300 (incluindo imagens). As variaveis `RATE_LIMIT_LOGIN`, `RATE_LIMIT_REFRESH`, `RATE_LIMIT_SETUP` e `RATE_LIMIT_PUBLIC` permitem ajuste positivo. Respostas limitadas usam 429 e `Retry-After`. Usuarios no mesmo IP compartilham o limite. O frontend nao encerra a sessao por um 429 temporario no refresh.

O limitador guarda no maximo `RATE_LIMIT_MAX_KEYS=10000` combinacoes de IP e grupo de rotas, sem expulsar limites ativos para admitir novas origens. Entradas expiradas sao limpas durante o trafego. Ele usa `remoteAddr`, nunca interpreta `X-Forwarded-For` diretamente. Atras de proxy, configure a infraestrutura para aceitar identidade de cliente somente de proxies confiaveis; sem isso, o IP observado pode ser o do proxy. Para varias replicas, o limite e por instancia e reinicia com o processo; um limite global exige controle compartilhado no gateway/infraestrutura.

| Metodo | Rota | Autenticacao | Descricao |
|---|---|---|---|
| `POST` | `/auth/login` | Publico | Autentica e emite cookies `HttpOnly` |
| `POST` | `/auth/refresh` | Publico (via cookie) | Renova o access token |
| `POST` | `/auth/logout` | CSRF obrigatorio | Limpa cookies de autenticacao |
| `GET` | `/auth/validate` | Autenticado | Valida token/sessao atual |
| `GET` | `/auth/csrf` | Publico | Retorna token CSRF |
| `POST` | `/setup/admin` | Flag + `X-Setup-Token` + CSRF | Cria primeiro admin inicial da plataforma |
| `GET` | `/painel/estabelecimento` | `ROLE_TENANT_ADMIN` | Obtem dados do estabelecimento do usuario |
| `PUT` | `/painel/estabelecimento` | `ROLE_TENANT_ADMIN` | Atualiza dados do estabelecimento |
| `POST` | `/painel/produtos` | `ROLE_TENANT_ADMIN` | Cadastra novo produto |
| `PUT` | `/painel/produtos/{id}` | `ROLE_TENANT_ADMIN` | Atualiza produto do estabelecimento |
| `PATCH` | `/painel/produtos/{id}/status` | `ROLE_TENANT_ADMIN` | Ativa ou desativa produto |
| `PATCH` | `/painel/produtos/{id}/ordem` | `ROLE_TENANT_ADMIN` | Altera ordem do produto |
| `DELETE` | `/painel/produtos/{id}` | `ROLE_TENANT_ADMIN` | Exclui produto |
| `GET` | `/painel/produtos` | `ROLE_TENANT_ADMIN` | Lista produtos do estabelecimento |
| `POST` | `/painel/storage/upload` | `ROLE_TENANT_ADMIN` | Envia imagem validada ao storage |
| `GET` | `/public/{slug}/cardapio` | Publico | Visualizacao do cardapio do estabelecimento |
| `GET` | `/public/{slug}/cardapio/info` | Publico | Informacoes publicas do estabelecimento |
| `GET` | `/public/storage/{fileName}` | Publico | Proxy seguro de download de imagem |
| `POST` | `/plataforma/estabelecimentos` | `ROLE_PLATFORM_ADMIN` | Cadastro administrativo de novos clientes |
| `GET` | `/plataforma/estabelecimentos` | `ROLE_PLATFORM_ADMIN` | Listagem geral de estabelecimentos |

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
