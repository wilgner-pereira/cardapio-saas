# AUDITORIA DE SEGURANÇA E LGPD — cardapio-saas

**Data da auditoria:** 2026-09-29
**Escopo:** Análise completa do repositório (backend, frontend, infraestrutura, configuração, testes)
**Objetivo:** Documentar achados para implementação pelo Codex — nenhuma alteração foi realizada neste repositório.

---

## Sumário

1. [Resumo Executivo](#1-resumo-executivo)
2. [Matriz de Achados](#2-matriz-de-achados)
3. [Arquitetura Atual Identificada](#3-arquitetura-atual-identificada)
4. [Mapa de Todas as Rotas](#4-mapa-de-todas-as-rotas)
5. [Achados Detalhados (SEC-001 a SEC-028)](#5-achados-detalhados)
6. [Controles Já Implementados Corretamente](#6-controles-já-implementados-corretamente)
7. [Estado Atual vs Estado Desejado](#7-estado-atual-vs-estado-desejado)
8. [LGPD — Análise Completa](#8-lgpd--análise-completa)
9. [Plano de Implementação para o Codex](#9-plano-de-implementação-para-o-codex)
10. [Checklist Final](#10-checklist-final)
11. [Testes de Segurança Necessários](#11-testes-de-segurança-necessários)

---

## 1. Resumo Executivo

O projeto **cardapio-saas** é um SaaS de cardápio online construído com Spring Boot 3.3 (Java 21) no backend e React 18 (Vite) no frontend, utilizando PostgreSQL (via Supabase em produção) como banco de dados e Supabase Storage para armazenamento de imagens.

### Pontos positivos encontrados

O projeto já implementa diversas boas práticas de segurança:

- **Isolamento de tenant em produtos** (`findByEstabelecimentoAndId`) bem implementado em `AdminProdutoServiceImpl`
- **JWT com separação de tipo** (access/refresh) e validação de secret mínimo de 32 caracteres
- **Cookies HttpOnly/Secure** para tokens de autenticação
- **CSRF ativo** com suporte SPA (cookie-to-header pattern)
- **Validação de entrada** robusta via Bean Validation em DTOs
- **Upload seguro** com validação de magic bytes e whitelist de MIME types
- **Log sanitization** contra CRLF injection
- **Security headers** (HSTS, X-Frame-Options DENY, nosniff, referrer-policy)
- **Swagger desabilitado em produção**
- **Dockerfile com non-root user** e multi-stage build
- **Error messages sanitizadas** (sem stack traces expostos)

### Problemas principais encontrados

| Severidade | Quantidade |
|---|---|
| CRÍTICO | 2 |
| ALTO | 7 |
| MÉDIO | 10 |
| BAIXO | 5 |
| MELHORIA | 4 |
| **Total** | **28** |

Os dois achados críticos são:

1. **SEC-001**: `BootstrapAdminRunner` pode promover qualquer usuário existente a `ROLE_ADMIN` se o username configurado já existir no banco
2. **SEC-003**: `accessToken` retornado no corpo da resposta de login, expondo o JWT a possível captura via XSS no frontend

---

## 2. Matriz de Achados

| ID | Severidade | Categoria | Resumo | Arquivo Principal |
|---|---|---|---|---|
| SEC-001 | CRÍTICO | Autenticação | BootstrapAdminRunner promove usuário existente a ROLE_ADMIN | `BootstrapAdminRunner.java` |
| SEC-002 | ALTO | Multi-tenancy | Nomenclatura confusa ROLE_ADMIN vs ROLE_USER invertida | `SecurityConfig.java` |
| SEC-003 | CRÍTICO | Autenticação | accessToken retornado no corpo da resposta (risco XSS) | `AuthenticationController.java` |
| SEC-004 | ALTO | Spring Security | `/painel/**` permite ROLE_ADMIN acessar rotas de tenant | `SecurityConfig.java` |
| SEC-005 | ALTO | Segurança | `SecurityUtils.getUsuarioAutenticado()` pode gerar NPE | `SecurityUtils.java` |
| SEC-006 | ALTO | Spring Security | Logout CSRF ignorado — Cross-Site Logout possível | `SecurityConfig.java` |
| SEC-007 | MÉDIO | Autenticação | Refresh token não tem revogação server-side | `AuthenticationController.java` |
| SEC-008 | ALTO | Segurança | Setup endpoint `/setup/admin` público e sem CSRF | `SetupController.java`, `SecurityConfig.java` |
| SEC-009 | MÉDIO | Multi-tenancy | `EstabelecimentoController` restringe a `ROLE_USER` — exclui ADMIN | `EstabelecimentoController.java` |
| SEC-010 | MÉDIO | Segurança | Falta `@PreAuthorize` no `AdminProdutoController` e `AdminCategoriaController` | `AdminProdutoController.java` |
| SEC-011 | ALTO | Infra | Supabase project ID hardcoded em properties | `application-supabase.properties` |
| SEC-012 | MÉDIO | Segurança | Senha hardcoded em migration seed (V4) | `V4__add_endereco_and_seed_sample_data.sql` |
| SEC-013 | ALTO | Segurança | Falta rate limiting em login e endpoints públicos | Global |
| SEC-014 | MÉDIO | Segurança | WebClient com `followRedirect(true)` — risco SSRF | `WebClientConfig.java` |
| SEC-015 | MÉDIO | Frontend | `localStorage` armazena metadados do usuário | `client.js` |
| SEC-016 | MÉDIO | Segurança | Sem cabeçalho `Content-Security-Policy` | `SecurityConfig.java` |
| SEC-017 | MÉDIO | Upload | Sem limite de uploads por tenant (DoS de armazenamento) | `SupabaseStorageController.java` |
| SEC-018 | BAIXO | Segurança | `CustomUserDetails` não verifica flag `ativo` do estabelecimento | `CustomUserDetails.java` |
| SEC-019 | BAIXO | Logs | Falta audit logging de operações sensíveis | Global |
| SEC-020 | BAIXO | Segurança | Docker Compose expõe porta 5432 em 0.0.0.0 | `docker-compose.yml` |
| SEC-021 | MÉDIO | Infra | CI/CD não roda testes frontend nem SAST | `ci.yml` |
| SEC-022 | BAIXO | Segurança | Sem `Permissions-Policy` header | `SecurityConfig.java` |
| SEC-023 | MÉDIO | LGPD | Logs de acesso podem conter dados pessoais implícitos | Global |
| SEC-024 | BAIXO | Testes | Falta teste de isolamento multi-tenant (IDOR) | Testes |
| SEC-025 | MELHORIA | Código | `js-cookie` declarado como dependência mas não utilizado | `package.json` |
| SEC-026 | MELHORIA | Segurança | `ProductEditorModal` não valida tamanho de imagem no frontend | `ProductEditorModal.jsx` |
| SEC-027 | MELHORIA | Código | `MenuShell.jsx` recebe prop `products` mas declara via destructuring | `MenuShell.jsx` |
| SEC-028 | MELHORIA | Infra | Falta health check endpoint protegido com detalhes | `application.properties` |

---

## 3. Arquitetura Atual Identificada

### Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21 + Spring Boot 3.3.0 |
| Segurança | Spring Security 6 + JWT (auth0 java-jwt 4.5.0) |
| Banco de dados | PostgreSQL 16 (Supabase em produção, H2 em dev/testes) |
| Migrations | Flyway (V1–V9) |
| Frontend | React 18 + Vite 8 |
| Storage de imagens | Supabase Storage (proxy pelo backend) |
| Testes | JUnit 5 + MockMvc + Spring Security Test + Vitest + Testing Library |
| CI/CD | GitHub Actions (Maven test only) |
| Container | Docker (multi-stage, non-root) + Docker Compose |

### Modelo de Dados

```
roles (id, role_name)
   ↕ ManyToMany
usuario (id, username, password, email, estabelecimento_id)
   ↓ ManyToOne
estabelecimento (id, nome, slug, descricao, tema, ativo, ...)
   ↑ OneToMany
produtos (id, nome, descricao, preco, ativo, categoria, categoria_ordem, ordem, imagem_url, usuario_id, estabelecimento_id)
```

### Modelo de Roles

| Role no Banco | Significado Real | Acesso |
|---|---|---|
| `ROLE_ADMIN` | Super-administrador da plataforma | `/plataforma/**` |
| `ROLE_USER` | Dono/administrador de um restaurante | `/painel/**` |

> **ATENÇÃO**: A nomenclatura está **invertida** em relação ao que o usuário espera. No contexto de negócio, `ROLE_USER` é na verdade o "ADMIN do restaurante" e `ROLE_ADMIN` é o "SUPER_ADMIN da plataforma".

### Fluxo de Autenticação

1. Frontend chama `GET /auth/csrf` para obter cookie XSRF-TOKEN
2. Frontend chama `POST /auth/login` com username/password
3. Backend valida credenciais via `AuthenticationManager` + BCrypt
4. Backend gera access token (15 min) e refresh token (7 dias)
5. Tokens são entregues como cookies HttpOnly **E** no corpo da resposta JSON
6. Frontend armazena metadados do usuário em `localStorage` (não o token)
7. Requests autenticados enviam cookies automaticamente com `credentials: "include"`
8. `SecurityFilter` extrai token do cookie ou header `Authorization: Bearer`
9. Token expirado → Frontend tenta `POST /auth/refresh` automaticamente
10. Refresh expirado → Dispara evento `cardapio:session-expired`, redireciona para login

### Profiles

| Profile | Banco | Swagger | Cookie Config |
|---|---|---|---|
| `dev` | H2 in-memory | Habilitado | Configurável |
| `local-postgres` | PostgreSQL local | Habilitado | Configurável |
| `supabase` | Supabase PostgreSQL | Desabilitado | Secure=true, SameSite=None |
| `prod` | PostgreSQL externo | Desabilitado | Secure=true, SameSite=Strict |
| `test` | H2 in-memory | N/A | Secure=false, SameSite=Lax |

---

## 4. Mapa de Todas as Rotas

| Método | Endpoint | Público/Privado | Role Necessária | Restaurante Isolado? | Observações |
|---|---|---|---|---|---|
| GET | `/public/{slug}/cardapio` | Público | Nenhuma | Por slug (público) | Filtra somente produtos ativos e estabelecimento ativo |
| GET | `/public/{slug}/cardapio/info` | Público | Nenhuma | Por slug (público) | Retorna info pública do estabelecimento |
| GET | `/public/storage/{fileName}` | Público | Nenhuma | N/A | Proxy para Supabase Storage com proteção path traversal |
| POST | `/auth/login` | Público | Nenhuma | N/A | Login com username/email + password |
| POST | `/auth/refresh` | Público | Nenhuma | N/A | Renova tokens via refresh_token cookie |
| POST | `/auth/logout` | Público | Nenhuma | N/A | Limpa cookies. **Sem CSRF** |
| GET | `/auth/csrf` | Público | Nenhuma | N/A | Obtém CSRF token cookie |
| GET | `/auth/validate` | Privado | Qualquer autenticado | N/A | Valida sessão e retorna dados do usuário |
| POST | `/setup/admin` | Público (condicional) | Nenhuma | N/A | Cria primeiro admin. **Sem CSRF**. Controlado por property |
| POST | `/painel/produtos` | Privado | USER ou ADMIN | ✅ Sim (auth) | Cria produto no estabelecimento do usuário autenticado |
| PUT | `/painel/produtos/{id}` | Privado | USER ou ADMIN | ✅ Sim (auth) | Atualiza com `findByEstabelecimentoAndId` |
| PATCH | `/painel/produtos/{id}/status` | Privado | USER ou ADMIN | ✅ Sim (auth) | Ativa/desativa com verificação de tenant |
| PATCH | `/painel/produtos/{id}/ordem` | Privado | USER ou ADMIN | ✅ Sim (auth) | Reordena com verificação de tenant |
| DELETE | `/painel/produtos/{id}` | Privado | USER ou ADMIN | ✅ Sim (auth) | Exclui com verificação de tenant |
| GET | `/painel/produtos/{id}` | Privado | USER ou ADMIN | ✅ Sim (auth) | Busca com verificação de tenant |
| GET | `/painel/produtos` | Privado | USER ou ADMIN | ✅ Sim (auth) | Lista produtos do estabelecimento autenticado |
| PATCH | `/painel/categorias/ordem` | Privado | USER ou ADMIN | ✅ Sim (auth) | Troca ordem de categorias do tenant |
| GET | `/painel/estabelecimento` | Privado | USER apenas | ✅ Sim (auth) | Retorna estabelecimento do usuário autenticado |
| PUT | `/painel/estabelecimento` | Privado | USER apenas | ✅ Sim (auth) | Atualiza com dados do auth |
| PUT | `/painel/estabelecimento/logo` | Privado | USER apenas | ✅ Sim (auth) | Atualiza logo com dados do auth |
| PUT | `/painel/estabelecimento/tema` | Privado | USER apenas | ✅ Sim (auth) | Atualiza tema com validação de enum |
| POST | `/painel/storage/upload` | Privado | USER ou ADMIN | N/A | Upload de imagem — validação de magic bytes |
| POST | `/plataforma/estabelecimentos` | Privado | ADMIN apenas | N/A | Cria estabelecimento + usuário proprietário |
| GET | `/plataforma/estabelecimentos` | Privado | ADMIN apenas | N/A | Lista todos os estabelecimentos |
| GET | `/plataforma/estabelecimentos/{id}` | Privado | ADMIN apenas | N/A | Obtém estabelecimento por ID |
| PATCH | `/plataforma/estabelecimentos/{id}/ativar` | Privado | ADMIN apenas | N/A | Ativa estabelecimento |
| PATCH | `/plataforma/estabelecimentos/{id}/desativar` | Privado | ADMIN apenas | N/A | Desativa estabelecimento |
| DELETE | `/plataforma/estabelecimentos/{id}` | Privado | ADMIN apenas | N/A | Exclui estabelecimento + cascata |
| POST | `/plataforma/admins` | Privado | ADMIN apenas | N/A | Cria novo administrador da plataforma |
| GET | `/actuator/health/**` | Público | Nenhuma | N/A | Health probes |
| GET | `/swagger-ui/**` | Público | Nenhuma | N/A | Só disponível em dev/local-postgres |
| GET | `/v3/api-docs/**` | Público | Nenhuma | N/A | Só disponível em dev/local-postgres |

---

## 5. Achados Detalhados

---

### SEC-001 — BootstrapAdminRunner promove usuário existente a ROLE_ADMIN

**Severidade:** CRÍTICO

**Arquivo:** `src/main/java/com/wilgner/cardapio/config/BootstrapAdminRunner.java`

**Método:** `run(String... args)` — linhas 56-65

**Problema:**

Se `app.bootstrap-admin.username` for configurado com o username de um usuário já existente no banco (por exemplo, um dono de restaurante com `ROLE_USER`), o runner silenciosamente adiciona `ROLE_ADMIN` ao conjunto de roles desse usuário. Isso constitui uma escalação de privilégios.

```java
Usuario usuario = usuarioRepository.findWithRolesAndEstabelecimentoByUsername(normalizedUsername)
        .orElseGet(() -> criarUsuarioAdmin(normalizedUsername, normalizedEmail));

var roles = new HashSet<>(usuario.getRoles());
roles.add(roleAdmin);
usuario.setRoles(roles);
usuarioRepository.save(usuario);
```

**Cenário de exploração:**

1. Atacante conhece o username de um dono de restaurante (ex: `restaurante`)
2. Se o atacante obtém acesso à configuração de variáveis de ambiente (CI/CD comprometido, `.env` exposto, painel cloud)
3. Define `APP_BOOTSTRAP_ADMIN_USERNAME=restaurante` e `APP_BOOTSTRAP_ADMIN_ENABLED=true`
4. No próximo restart, o usuário `restaurante` ganha `ROLE_ADMIN`

**Alteração recomendada:**

Quando o usuário já existir, o runner deve **recusar** a execução em vez de promover silenciosamente:

```java
@Override
public void run(String... args) {
    // ... validações existentes ...

    if (usuarioRepository.findByUsername(normalizedUsername).isPresent()) {
        throw new IllegalStateException(
            "Bootstrap admin abortado: usuário '" + normalizedUsername + "' já existe. " +
            "Remova ou altere APP_BOOTSTRAP_ADMIN_USERNAME."
        );
    }

    Usuario usuario = criarUsuarioAdmin(normalizedUsername, normalizedEmail);
    var roles = new HashSet<>(usuario.getRoles());
    roles.add(roleAdmin);
    usuario.setRoles(roles);
    usuarioRepository.save(usuario);
}
```

**Teste necessário:**

1. Criar um usuário `testuser` com `ROLE_USER` no banco
2. Configurar `APP_BOOTSTRAP_ADMIN_USERNAME=testuser` e `APP_BOOTSTRAP_ADMIN_ENABLED=true`
3. Reiniciar a aplicação
4. **Esperado:** aplicação deve falhar na inicialização com `IllegalStateException`
5. **Verificar:** o usuário `testuser` NÃO deve ter `ROLE_ADMIN`

---

### SEC-002 — Nomenclatura confusa de roles (ROLE_ADMIN vs ROLE_USER invertida)

**Severidade:** ALTO

**Arquivos:**
- `src/main/java/com/wilgner/cardapio/config/SecurityConfig.java` — linhas 97-99
- `src/main/java/com/wilgner/cardapio/service/AdminEstabelecimentoServiceImpl.java` — linhas 70-76, 110-114
- `src/main/resources/db/migration/V1__create_initial_schema.sql` — linhas 32-38

**Problema:**

No sistema atual:
- `ROLE_ADMIN` = Super-administrador da plataforma (quem gerencia todos os restaurantes)
- `ROLE_USER` = Dono/administrador de um restaurante (quem gerencia seus produtos/cardápio)

Essa nomenclatura é o oposto da convenção usual e do que o stakeholder espera. Aumenta o risco de erros de implementação futura.

```java
// SecurityConfig.java
.requestMatchers("/plataforma/**").hasRole("ADMIN")     // SUPER_ADMIN da plataforma
.requestMatchers("/painel/**").hasAnyRole("USER", "ADMIN") // ADMIN do restaurante + SUPER_ADMIN
```

**Risco:**

Desenvolvedores futuros podem atribuir `ROLE_ADMIN` a donos de restaurante pensando que é a role correta, concedendo acesso à plataforma inteira.

**Alteração recomendada:**

Renomear as roles para refletir a semântica correta:

| Atual | Proposto |
|---|---|
| `ROLE_ADMIN` | `ROLE_SUPER_ADMIN` |
| `ROLE_USER` | `ROLE_ADMIN` |

Essa alteração requer:

1. Migration SQL para renomear na tabela `roles`
2. Atualizar `SecurityConfig.java` matchers
3. Atualizar `@PreAuthorize` em `AdminEstabelecimentoController`, `PlatformAdminController`, `EstabelecimentoController`
4. Atualizar `AdminEstabelecimentoServiceImpl.cadastrarEstabelecimento()` e `cadastrarAdministrador()`
5. Atualizar `BootstrapAdminRunner`
6. Atualizar testes

**Teste necessário:**

Após a renomeação:
1. Login como SUPER_ADMIN → deve acessar `/plataforma/**` e `/painel/**`
2. Login como ADMIN (restaurante) → deve acessar `/painel/**`, ser bloqueado em `/plataforma/**`
3. Todas as operações CRUD de produtos devem funcionar para ADMIN do restaurante
4. Criação de estabelecimento via `/plataforma/estabelecimentos` deve funcionar somente para SUPER_ADMIN

---

### SEC-003 — accessToken retornado no corpo da resposta de login

**Severidade:** CRÍTICO

**Arquivo:** `src/main/java/com/wilgner/cardapio/controller/AuthenticationController.java`

**Método:** `login()` — linhas 48-67; `toResponse()` — linhas 117-134

**Problema:**

O endpoint `/auth/login` e `/auth/refresh` retornam o `accessToken` tanto nos cookies HttpOnly **quanto** no corpo JSON da resposta:

```java
return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, tokenCookieService.accessToken(...).toString())
        .header(HttpHeaders.SET_COOKIE, tokenCookieService.refreshToken(...).toString())
        .body(toResponse("Autenticado", accessToken, authenticatedUser));
        //                               ^^^^^^^^^^^ token no corpo
```

O `AuthenticationResponseDTO` inclui:

```java
public record AuthenticationResponseDTO(
    String message,
    String accessToken, // ← JWT exposto no corpo
    String tokenType,
    Long userId,
    String username,
    String email,
    String estabelecimentoSlug,
    List<String> roles,
    long accessExpiresInMs
) {}
```

Embora os cookies sejam HttpOnly e inacessíveis via JavaScript, o token no corpo da resposta pode ser capturado por:
- Scripts XSS que interceptam a resposta do fetch
- Extensions de navegador maliciosas
- Log de resposta em ferramentas de debug

O frontend atual (`client.js`) utiliza `credentials: "include"` e os cookies são automaticamente enviados. O token do corpo é utilizado apenas em `registerAndLoginWithBearer()` nos testes (para teste de autenticação via header Bearer).

**Alteração recomendada:**

Remover o `accessToken` do corpo da resposta. Os metadados do usuário (`userId`, `username`, `email`, `roles`, etc.) podem permanecer:

```java
// AuthenticationController.java — método toResponse:
private AuthenticationResponseDTO toResponse(String message, Usuario user) {
    List<String> roles = user.getRoles().stream()
            .map(role -> role.getRoleName())
            .sorted()
            .toList();

    return new AuthenticationResponseDTO(
            message,
            null,           // ← não expor accessToken
            "Bearer",
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getEstabelecimento() != null ? user.getEstabelecimento().getSlug() : null,
            roles,
            tokenService.getAccessTokenDuration().toMillis()
    );
}
```

Ou melhor ainda, remover o campo `accessToken` do DTO completamente e criar um DTO de resposta sem o token.

**Teste necessário:**

1. Chamar `POST /auth/login` com credenciais válidas
2. Verificar que a resposta JSON **NÃO** contém o campo `accessToken` (ou contém `null`)
3. Verificar que os cookies `access_token` e `refresh_token` estão presentes nos headers `Set-Cookie`
4. Verificar que requisições autenticadas via cookie continuam funcionando normalmente
5. Verificar que a aplicação frontend (React) continua funcionando (ela já usa cookies, não o token do corpo)

---

### SEC-004 — `/painel/**` permite ROLE_ADMIN acessar rotas de tenant

**Severidade:** ALTO

**Arquivo:** `src/main/java/com/wilgner/cardapio/config/SecurityConfig.java` — linha 99

**Problema:**

```java
.requestMatchers("/painel/**").hasAnyRole("USER", "ADMIN")
```

`ROLE_ADMIN` (super-admin da plataforma) tem acesso a todas as rotas `/painel/**`. Porém, `EstabelecimentoController` tem `@PreAuthorize("hasRole('USER')")` — o que bloqueia ADMIN.

O problema é que ADMIN pode acessar:
- `AdminProdutoController` (`/painel/produtos/**`) — que NÃO tem `@PreAuthorize`
- `AdminCategoriaController` (`/painel/categorias/**`) — que NÃO tem `@PreAuthorize`
- `SupabaseStorageController` (`/painel/storage/**`) — que NÃO tem `@PreAuthorize`

Quando um ADMIN acessa estas rotas, `SecurityUtils.getUsuarioAutenticado()` retorna o usuário ADMIN com o estabelecimento dummy "Administrador da Plataforma". As operações CRUD de produtos serão executadas no contexto desse estabelecimento dummy.

**Risco:** Baixo para segurança direta (ADMIN não consegue acessar dados de outros tenants), mas confuso e pode gerar dados indesejados no estabelecimento administrativo.

**Alteração recomendada:**

Adicionar `@PreAuthorize("hasRole('USER')")` em `AdminProdutoController` e `AdminCategoriaController` para bloquear explicitamente o ADMIN de operar nesses controllers:

```java
@RestController
@RequestMapping("/painel/produtos")
@PreAuthorize("hasRole('USER')")  // ← adicionar
public class AdminProdutoController { ... }
```

```java
@RestController
@RequestMapping("/painel/categorias")
@PreAuthorize("hasRole('USER')")  // ← adicionar
public class AdminCategoriaController { ... }
```

**Teste necessário:**

1. Autenticar como ROLE_ADMIN (super-admin)
2. Tentar `POST /painel/produtos` com payload válido
3. **Esperado:** 403 Forbidden
4. Autenticar como ROLE_USER (dono de restaurante)
5. Tentar `POST /painel/produtos` com payload válido
6. **Esperado:** 201 Created

---

### SEC-005 — SecurityUtils.getUsuarioAutenticado() pode gerar NullPointerException

**Severidade:** ALTO

**Arquivo:** `src/main/java/com/wilgner/cardapio/service/SecurityUtils.java` — linha 9

**Problema:**

```java
public static Usuario getUsuarioAutenticado() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    //                                                   ^^^^^^^^^^^^^^^^^^^
    //                                                   Pode ser null em contextos não autenticados
    if(principal instanceof CustomUserDetails customUserDetails) {
        return customUserDetails.getUser();
    }
    return null;
}
```

`getAuthentication()` retorna `null` para requisições anônimas ou em threads assíncronas sem contexto de segurança propagado. Isso causa `NullPointerException` ao invés de retornar `null`.

**Cenário:** Se um futuro desenvolvedor chamar `SecurityUtils.getUsuarioAutenticado()` em um contexto inesperado (ex: scheduled task, async handler), a aplicação lança NPE sem mensagem clara.

**Alteração recomendada:**

```java
public static Usuario getUsuarioAutenticado() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof CustomUserDetails customUserDetails) {
        return customUserDetails.getUser();
    }
    return null;
}
```

**Teste necessário:**

1. Chamar `SecurityUtils.getUsuarioAutenticado()` em um contexto sem autenticação (por exemplo, em um teste unitário sem `@WithMockUser`)
2. **Esperado:** retorna `null` sem exceção

---

### SEC-006 — CSRF ignorado no logout (Cross-Site Logout)

**Severidade:** ALTO

**Arquivo:** `src/main/java/com/wilgner/cardapio/config/SecurityConfig.java` — linhas 69-71

**Problema:**

```java
.csrf(csrf -> csrf
    ...
    .ignoringRequestMatchers(
        "/auth/login",
        "/auth/refresh",
        "/auth/logout",  // ← CSRF ignorado no logout
        ...
    ))
```

O endpoint `/auth/logout` ignora a proteção CSRF. Um site malicioso pode forçar o logout do usuário (Cross-Site Logout / DoS) com um formulário hidden:

```html
<form action="https://api.cardapio.com/auth/logout" method="POST">
  <input type="submit" value="Clique aqui">
</form>
```

O navegador anexa automaticamente os cookies (incluindo SameSite=None) e o logout acontece.

**Risco:** Nível de severidade funcional (DoS para o usuário, não vazamento de dados), mas pode ser usado para forçar re-autenticação como parte de um ataque de phishing.

**Alteração recomendada:**

Remover `/auth/logout` da lista de ignoringRequestMatchers do CSRF. O frontend já envia o `X-XSRF-TOKEN` header em chamadas POST:

```java
.ignoringRequestMatchers(
    "/auth/login",
    "/auth/refresh",
    // Remover "/auth/logout"
    "/setup/admin",
    "/public/**",
    ...
)
```

**Teste necessário:**

1. Autenticar via login (obter cookies)
2. Obter CSRF token via `GET /auth/csrf`
3. Chamar `POST /auth/logout` **sem** header `X-XSRF-TOKEN`
4. **Esperado:** 403 Forbidden (CSRF falhou)
5. Chamar `POST /auth/logout` **com** header `X-XSRF-TOKEN` correto
6. **Esperado:** 200 OK (logout bem-sucedido)

---

### SEC-007 — Refresh token sem revogação server-side

**Severidade:** MÉDIO

**Arquivo:** `src/main/java/com/wilgner/cardapio/controller/AuthenticationController.java` — linhas 69-94

**Problema:**

O sistema gera novos tokens a cada refresh e invalida os cookies antigos, mas não mantém uma lista de revogação (blocklist/denylist) no servidor. Se um refresh token for comprometido antes de expirar (7 dias), ele pode ser usado para gerar novos access tokens até sua expiração.

O logout atual apenas limpa os cookies no navegador do cliente, mas o refresh token continua válido no servidor até expirar naturalmente.

**Mitigação parcial existente:** O `SecurityFilter` revalida o usuário no banco a cada requisição (`findWithRolesAndEstabelecimentoByUsername`), então se o usuário for desativado no banco, o access token para de funcionar na próxima requisição. Porém, o refresh token ainda funciona no `/auth/refresh`.

**Alteração recomendada:**

Implementar um mecanismo simples de revogação. Duas opções:

**Opção A — Token family (jti claim):**
1. Adicionar claim `jti` (JWT ID) ao refresh token
2. Armazenar o `jti` atual no campo do usuário no banco
3. No refresh, verificar se o `jti` corresponde ao armazenado
4. No logout, limpar o `jti` do banco

**Opção B — Blocklist em cache:**
1. No logout, adicionar o `jti` do refresh token a um cache (ex: `ConcurrentHashMap` ou Redis)
2. No refresh, verificar se o `jti` está na blocklist
3. Limpar entradas expiradas periodicamente

**Teste necessário:**

1. Login e obter refresh token
2. Chamar `POST /auth/logout`
3. Tentar `POST /auth/refresh` com o refresh token antigo
4. **Esperado:** 401 Unauthorized (token revogado)

---

### SEC-008 — Endpoint /setup/admin público e sem CSRF

**Severidade:** ALTO

**Arquivos:**
- `src/main/java/com/wilgner/cardapio/controller/SetupController.java`
- `src/main/java/com/wilgner/cardapio/config/SecurityConfig.java` — linhas 73-77, 96

**Problema:**

O endpoint `POST /setup/admin` é:
1. Público (`permitAll` no SecurityConfig)
2. Sem CSRF (na lista de `ignoringRequestMatchers`)
3. Controlado por `@ConditionalOnProperty` — se `app.setup-admin.enabled=true`, o bean é registrado

**Mitigação existente:** O service `cadastrarPrimeiroAdministrador()` verifica se já existe um `ROLE_ADMIN` no banco e rejeita a operação com 409 se existir.

**Risco residual:**
- Se `app.setup-admin.enabled=true` estiver configurado em produção E nenhum admin existir ainda, qualquer pessoa na internet pode criar o primeiro admin
- No ambiente de teste (`application-test.properties`), `app.setup-admin.enabled=true` está habilitado

**Alteração recomendada:**

1. **Garantir** que `app.setup-admin.enabled=false` esteja explicitamente nos profiles `prod` e `supabase`
2. **Adicionar** log de auditoria quando o endpoint for chamado
3. **Considerar** adicionar um token/secret de setup que precisa ser fornecido no request:

```java
@PostMapping("/admin")
public ResponseEntity<RegisterResponseDTO> criarPrimeiroAdministrador(
        @RequestBody @Valid RegisterRequestDTO dto,
        @RequestHeader("X-Setup-Token") String setupToken) {

    if (!expectedSetupToken.equals(setupToken)) {
        throw new BadCredentialsException("Token de setup inválido");
    }

    RegisterResponseDTO response = estabelecimentoService.cadastrarPrimeiroAdministrador(dto);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

**Teste necessário:**

1. Com `app.setup-admin.enabled=true` e nenhum admin no banco:
   - Chamar `POST /setup/admin` sem `X-Setup-Token` → 401/403
   - Chamar `POST /setup/admin` com `X-Setup-Token` válido → 201
2. Com admin já existente:
   - Chamar `POST /setup/admin` → 409 Conflict
3. Com `app.setup-admin.enabled=false`:
   - Chamar `POST /setup/admin` → 404 Not Found (bean não registrado)

---

### SEC-009 — EstabelecimentoController restringe a ROLE_USER — exclui ADMIN da plataforma

**Severidade:** MÉDIO

**Arquivo:** `src/main/java/com/wilgner/cardapio/controller/EstabelecimentoController.java` — linha 14

**Problema:**

```java
@PreAuthorize("hasRole('USER')")
```

O ADMIN da plataforma (`ROLE_ADMIN`) **não pode** acessar `GET /painel/estabelecimento` nem editar seu próprio estabelecimento via estas rotas. Isso foi verificado no teste `SecurityIntegrationTests.usuarioSemPermissao_deveRetornar403()` — o admin recebe 403 neste endpoint.

**Risco:** Funcional, não de segurança. O admin da plataforma gerencia estabelecimentos via `/plataforma/estabelecimentos`, então isso é esperado. Porém, se o admin também tiver `ROLE_USER`, ele poderia acessar. Atualmente, admins criados via `cadastrarAdministrador()` recebem SOMENTE `ROLE_ADMIN`.

**Alteração recomendada (baixa prioridade):**

Documentar este comportamento no código com comentário. Se necessário que o super-admin também edite seu próprio estabelecimento dummy, considerar adicionar `ROLE_USER` ao admin OU criar rota separada.

**Teste necessário:** Já coberto por `SecurityIntegrationTests.usuarioSemPermissao_deveRetornar403()`.

---

### SEC-010 — Falta @PreAuthorize nos controllers do painel

**Severidade:** MÉDIO

**Arquivos:**
- `src/main/java/com/wilgner/cardapio/controller/AdminProdutoController.java`
- `src/main/java/com/wilgner/cardapio/controller/AdminCategoriaController.java`
- `src/main/java/com/wilgner/cardapio/controller/SupabaseStorageController.java`

**Problema:**

Estes controllers dependem exclusivamente do matcher no `SecurityConfig`:
```java
.requestMatchers("/painel/**").hasAnyRole("USER", "ADMIN")
```

Não possuem `@PreAuthorize` como defesa em profundidade. Se alguém acidentalmente alterar o matcher no `SecurityConfig`, os endpoints ficam expostos.

**Alteração recomendada:**

Adicionar `@PreAuthorize` em cada controller:

```java
@RestController
@RequestMapping("/painel/produtos")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")  // ← ou hasRole('USER') se SEC-004 for aplicado
public class AdminProdutoController { ... }
```

```java
@RestController
@RequestMapping("/painel/categorias")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class AdminCategoriaController { ... }
```

```java
@RestController
@RequestMapping("/painel/storage")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class SupabaseStorageController { ... }
```

**Teste necessário:**

1. Sem token de autenticação, chamar qualquer rota `/painel/**`
2. **Esperado:** 401 Unauthorized
3. Com token de usuário sem role válida, chamar qualquer rota `/painel/**`
4. **Esperado:** 403 Forbidden

---

### SEC-011 — Supabase project ID hardcoded em properties

**Severidade:** ALTO

**Arquivo:** `src/main/resources/application-supabase.properties` — linhas 1-3, 9

**Problema:**

```properties
spring.datasource.url=${DATABASE_URL:jdbc:postgresql://aws-1-us-west-1.pooler.supabase.com:5432/postgres?sslmode=require}
spring.datasource.username=${DATABASE_USERNAME:postgres.hjigxbjeeecuehlbxxny}
...
supabase.url=${SUPABASE_URL:https://hjigxbjeeecuehlbxxny.supabase.co}
```

O Project Reference ID do Supabase (`hjigxbjeeecuehlbxxny`) e o endpoint do pooler estão hardcoded como valores padrão nos properties. Se o repositório for público (ou alguém acessar o código), isso revela:
- O projeto Supabase exato
- O endpoint do banco de dados
- O username do pooler

**Risco:** Embora a senha não esteja hardcoded, o atacante tem 2 dos 3 elementos necessários para conexão. Facilita ataques de brute force ou credential stuffing contra o endpoint do banco.

**Alteração recomendada:**

Remover os valores padrão, forçando a definição via variáveis de ambiente:

```properties
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
...
supabase.url=${SUPABASE_URL}
```

Também remover o project ID comentado em `.env.example`:

```properties
# Remover ou substituir por placeholder:
# DATABASE_URL=jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require
# DATABASE_USERNAME=postgres.<project-ref>
```

**Teste necessário:**

1. Iniciar a aplicação com profile `supabase` sem definir `DATABASE_URL`
2. **Esperado:** falha na inicialização com mensagem clara sobre variável ausente

---

### SEC-012 — Senha BCrypt hardcoded em migration seed

**Severidade:** MÉDIO

**Arquivo:** `src/main/resources/db/migration/V4__add_endereco_and_seed_sample_data.sql` — linhas de INSERT de usuario

**Problema:**

```sql
INSERT INTO usuario (username, password, email, estabelecimento_id)
SELECT 'restaurante', '$2a$10$E/qfWuRaVShSG0d5bgJxmOb4gMz27FX0oMALKTtlbpB9t0sDl/H4G', 'dono@restaurante.test', e.id
...
```

Todos os 5 usuários seed compartilham a **mesma senha BCrypt hash**. Se alguém descobrir qual senha gera esse hash, terá acesso a todos os 5 estabelecimentos demo.

**Risco:** Médio — esses são dados de demonstração e provavelmente não existem em produção (a migration usa `WHERE NOT EXISTS`). Porém, se o banco de produção for inicializado sem limpeza, esses usuários existirão com senhas conhecidas.

**Alteração recomendada:**

1. **Verificar** se esses usuários existem no banco de produção e removê-los se existirem
2. **Considerar** criar uma migration apenas para ambientes dev/local, ou proteger com condicional
3. **Documentar** qual é a senha correspondente ao hash, para referência de desenvolvimento

**Teste necessário:**

1. Verificar no banco de produção se existem usuários com username `restaurante`, `pizzaria`, `hamburgueria`, `espetaria`, `bar`
2. Se existirem, trocar as senhas ou remover os usuários

---

### SEC-013 — Falta rate limiting em login e endpoints públicos

**Severidade:** ALTO

**Arquivos:** Nenhum implementa rate limiting atualmente

**Problema:**

Não existe nenhum mecanismo de rate limiting em:
- `POST /auth/login` — vulnerável a brute force
- `POST /auth/refresh` — amplificação de refresh
- `POST /setup/admin` — tentativas ilimitadas
- `GET /public/**` — possível DDoS de leitura

**Cenário de exploração:**

1. Atacante envia milhares de requests para `POST /auth/login` com diferentes senhas
2. Sem rate limiting, pode testar todas as senhas comuns em minutos
3. Cada tentativa executa BCrypt (CPU intensivo), podendo causar DoS

**Alteração recomendada:**

Implementar rate limiting usando uma das opções:

**Opção A — Spring Boot Actuator + Bucket4j:**
```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.0</version>
</dependency>
```

**Opção B — Filtro customizado simples:**
```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        if (request.getRequestURI().equals("/auth/login") && "POST".equals(request.getMethod())) {
            String clientIp = request.getRemoteAddr();
            Bucket bucket = buckets.computeIfAbsent(clientIp, k -> createBucket());
            if (!bucket.tryConsume(1)) {
                response.setStatus(429);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private Bucket createBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
            .build();
    }
}
```

**Teste necessário:**

1. Enviar 6 requests `POST /auth/login` com senha errada em menos de 1 minuto
2. **Esperado:** as primeiras 5 retornam 401, a 6ª retorna 429 Too Many Requests
3. Esperar 1 minuto e enviar novamente → 401 (não 429)

---

### SEC-014 — WebClient com followRedirect(true)

**Severidade:** MÉDIO

**Arquivo:** `src/main/java/com/wilgner/cardapio/config/WebClientConfig.java` — linha 23

**Problema:**

```java
HttpClient httpClient = HttpClient.create(provider)
        .responseTimeout(Duration.ofMinutes(2))
        .followRedirect(true);  // ← segue redirecionamentos automaticamente
```

Se o WebClient for usado para acessar URLs derivadas de input do usuário (ex: URL de imagem fornecida pelo admin), um atacante poderia:
1. Configurar uma URL que redireciona para `http://169.254.169.254/latest/meta-data/` (metadados cloud)
2. O WebClient seguiria o redirecionamento e exporia dados internos (SSRF)

**Mitigação existente:** Atualmente o WebClient é usado apenas para comunicação com Supabase Storage (URL configurada em property, não fornecida pelo usuário).

**Alteração recomendada:**

Desabilitar follow redirect ou limitar a URLs confiáveis:

```java
HttpClient httpClient = HttpClient.create(provider)
        .responseTimeout(Duration.ofSeconds(30))  // ← reduzir timeout
        .followRedirect(false);                    // ← desabilitar redirect
```

**Teste necessário:**

Teste unitário do `SupabaseStorageService`:
1. Mockando uma resposta 302 da Supabase
2. **Esperado:** exceção ou tratamento adequado, não seguir o redirect cegamente

---

### SEC-015 — localStorage armazena metadados do usuário

**Severidade:** MÉDIO

**Arquivo:** `frontend/src/api/client.js` — linhas 8-17

**Problema:**

```javascript
function saveSession(session) {
  if (session?.username) {
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify({
      userId: session.userId,
      username: session.username,
      email: session.email,                    // ← email no localStorage
      estabelecimentoSlug: session.estabelecimentoSlug,
      roles: session.roles || []
    }));
  }
}
```

O `localStorage` persiste entre sessões e é acessível por qualquer script JavaScript na mesma origem. Em caso de XSS, o atacante pode ler `userId`, `username`, `email` e `roles`.

**Mitigação existente:** Os tokens JWT NÃO são armazenados no localStorage (são cookies HttpOnly). O que está no localStorage são apenas metadados para UX.

**Risco:** Baixo-médio. O email do admin do restaurante pode ser considerado dado pessoal (LGPD).

**Alteração recomendada:**

Mover para `sessionStorage` (limpa ao fechar o navegador) e remover o `email`:

```javascript
function saveSession(session) {
  if (session?.username) {
    sessionStorage.setItem(AUTH_USER_KEY, JSON.stringify({
      userId: session.userId,
      username: session.username,
      estabelecimentoSlug: session.estabelecimentoSlug,
      roles: session.roles || []
      // email removido — não necessário no client-side
    }));
  }
}
```

Atualizar `getStoredUser()` e `hasStoredUser()` para ler de `sessionStorage`.

**Teste necessário:**

1. Login no frontend
2. Verificar `sessionStorage` (não `localStorage`) contém os metadados
3. Fechar o navegador e reabrir → sessão limpa (precisa revalidar via `/auth/validate`)
4. Verificar que `email` não está presente no storage

---

### SEC-016 — Sem Content-Security-Policy header

**Severidade:** MÉDIO

**Arquivo:** `src/main/java/com/wilgner/cardapio/config/SecurityConfig.java` — seção headers

**Problema:**

O CSP (Content-Security-Policy) não está configurado. Sem CSP, scripts inline e de fontes externas podem ser executados sem restrição, facilitando ataques XSS.

**Alteração recomendada:**

Adicionar CSP ao `SecurityConfig`:

```java
.headers(headers -> headers
    .frameOptions(frame -> frame.deny())
    .contentTypeOptions(Customizer.withDefaults())
    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
    .referrerPolicy(referrer -> referrer.policy(...))
    .contentSecurityPolicy(csp -> csp
        .policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self'; connect-src 'self' " + allowedOrigins + "; frame-ancestors 'none'"))
)
```

> Nota: `'unsafe-inline'` para `style-src` pode ser necessário se o framework CSS usar inline styles.

**Teste necessário:**

1. Acessar a aplicação e verificar header `Content-Security-Policy` na resposta
2. Tentar injetar `<script>alert(1)</script>` em campo de texto e verificar que o browser bloqueia a execução

---

### SEC-017 — Sem limite de uploads por tenant

**Severidade:** MÉDIO

**Arquivo:** `src/main/java/com/wilgner/cardapio/controller/SupabaseStorageController.java`

**Problema:**

Qualquer usuário autenticado pode fazer uploads ilimitados. Não há:
- Limite de quantidade de uploads por dia/hora
- Limite de espaço total por estabelecimento
- Limpeza de imagens órfãs

**Cenário:** Um tenant malicioso pode fazer milhares de uploads de 5MB, consumindo o storage do Supabase (que pode ter custos).

**Alteração recomendada:**

1. Implementar contador de uploads por tenant (ex: campo `total_storage_bytes` no `Estabelecimento`)
2. Definir limite (ex: 100MB por estabelecimento)
3. Verificar antes de cada upload
4. Implementar job para limpar imagens não referenciadas

**Teste necessário:**

1. Autenticar como tenant
2. Fazer upload até atingir o limite
3. **Esperado:** próximo upload retorna 413 ou 400 com mensagem de limite atingido

---

### SEC-018 — CustomUserDetails não verifica flag ativo do estabelecimento

**Severidade:** BAIXO

**Arquivo:** `src/main/java/com/wilgner/cardapio/security/CustomUserDetails.java`

**Problema:**

```java
public class CustomUserDetails implements UserDetails {
    // Não sobrescreve isEnabled(), isAccountNonLocked(), etc.
    // Retornam true por padrão
}
```

Se um estabelecimento for desativado via `PATCH /plataforma/estabelecimentos/{id}/desativar`, o usuário associado continua podendo fazer login e operar normalmente. A flag `ativo` do estabelecimento não é verificada durante a autenticação.

**Mitigação existente:** O `PublicProdutoServiceImpl` verifica `estabelecimento.getAtivo()` antes de retornar dados públicos. Mas o dono do estabelecimento desativado ainda pode fazer login no painel.

**Alteração recomendada:**

Sobrescrever `isEnabled()` em `CustomUserDetails`:

```java
@Override
public boolean isEnabled() {
    return usuario.getEstabelecimento() != null && usuario.getEstabelecimento().getAtivo();
}
```

**Teste necessário:**

1. Criar estabelecimento e usuário
2. Desativar o estabelecimento via `/plataforma/estabelecimentos/{id}/desativar`
3. Tentar login com o usuário do estabelecimento desativado
4. **Esperado:** 401 Unauthorized (conta desabilitada)

---

### SEC-019 — Falta audit logging de operações sensíveis

**Severidade:** BAIXO

**Arquivos:** Controllers e services em geral

**Problema:**

Não existe logging estruturado de auditoria para:
- Criação de estabelecimentos
- Criação/remoção de administradores
- Alteração de status de estabelecimentos (ativar/desativar)
- Deleção de estabelecimentos
- Login bem-sucedido
- Tentativas de login falhadas
- Refresh de token

O `RestAccessDeniedHandler` faz log de acessos negados, mas operações legítimas sensíveis não são logadas.

**Alteração recomendada:**

Adicionar logs de auditoria nos services críticos:

```java
// AdminEstabelecimentoServiceImpl.java
log.info("AUDIT: Estabelecimento criado id={} slug={} por_admin={}",
    estabelecimentoSalvo.getId(), slug, SecurityUtils.getUsuarioAutenticado().getUsername());
```

```java
// AuthenticationController.java
log.info("AUDIT: Login bem-sucedido usuario={} ip={}",
    LogSanitizer.sanitize(user.getUsername()), request.getRemoteAddr());
```

**Teste necessário:** Verificar que os logs aparecem no stdout/arquivo de log após operações sensíveis.

---

### SEC-020 — Docker Compose expõe porta 5432 em 0.0.0.0

**Severidade:** BAIXO

**Arquivo:** `docker-compose.yml` — linha 7

**Problema:**

```yaml
ports:
  - "5432:5432"  # ← expõe em todas as interfaces de rede
```

Em ambientes compartilhados (co-working, rede corporativa), o banco de dados PostgreSQL fica acessível externamente.

**Alteração recomendada:**

Vincular apenas a localhost:

```yaml
ports:
  - "127.0.0.1:5432:5432"
```

**Teste necessário:** Após alteração, tentar conectar ao PostgreSQL de outra máquina na mesma rede → deve falhar.

---

### SEC-021 — CI/CD não roda testes frontend nem SAST

**Severidade:** MÉDIO

**Arquivo:** `.github/workflows/ci.yml`

**Problema:**

O CI executa apenas `mvn -B test` (testes backend Java). Faltam:
- Testes do frontend (`npm test` / `vitest run`)
- Verificação de dependências vulneráveis (`mvn dependency-check:check` / `npm audit`)
- Análise estática de segurança (SAST)
- Build do frontend para verificar erros de compilação

**Alteração recomendada:**

```yaml
jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven
      - run: mvn -B test

  frontend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: "20"
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - run: npm ci
      - run: npm test
      - run: npm run build
      - run: npm audit --audit-level=high || true
```

**Teste necessário:** Push para main/PR e verificar que ambos os jobs executam com sucesso.

---

### SEC-022 — Sem Permissions-Policy header

**Severidade:** BAIXO

**Arquivo:** `src/main/java/com/wilgner/cardapio/config/SecurityConfig.java`

**Problema:**

O header `Permissions-Policy` (antigo `Feature-Policy`) não está configurado. Browsers podem permitir acesso a câmera, microfone, geolocalização, etc.

**Alteração recomendada:**

Adicionar header via filtro ou configuração:

```java
.headers(headers -> headers
    ...
    .permissionsPolicy(permissions -> permissions
        .policy("camera=(), microphone=(), geolocation=(), payment=()"))
)
```

**Teste necessário:** Verificar presença do header `Permissions-Policy` nas respostas HTTP.

---

### SEC-023 — Logs podem conter dados pessoais implícitos (LGPD)

**Severidade:** MÉDIO

**Arquivos:** Toda a aplicação

**Problema:**

Os logs de acesso do servidor web (Tomcat/Spring Boot) registram por padrão:
- IP do visitante
- User-Agent
- URLs acessadas (que contêm o slug do estabelecimento)
- Timestamps

Para o visitante público (que apenas visualiza o cardápio), isso constitui coleta de dados pessoais (IP é considerado dado pessoal pela LGPD).

**Alteração recomendada:**

Ver seção [8. LGPD — Análise Completa](#8-lgpd--análise-completa).

---

### SEC-024 — Falta teste de isolamento multi-tenant (IDOR)

**Severidade:** BAIXO

**Arquivo:** Todos os arquivos de teste

**Problema:**

Nenhum teste existente verifica explicitamente se um usuário do restaurante A consegue acessar/modificar recursos do restaurante B. Os testes atuais testam:
- Acesso não autenticado (401)
- Roles (403 para ADMIN em `/painel/estabelecimento`)
- CSRF enforcement
- Cookies e headers

Mas **não testam IDOR**:
- Restaurante A tenta PUT `/painel/produtos/{produtoDoRestauranteB}` → deveria receber 404
- Restaurante A tenta GET `/painel/produtos/{produtoDoRestauranteB}` → deveria receber 404

**Alteração recomendada:**

Criar teste de integração:

```java
@Test
void usuarioNaoPodeAlterarProdutoDeOutroEstabelecimento() throws Exception {
    // Criar restaurante A e produto A
    Cookie[] cookiesA = registerAndLogin("restaurante-a", "a@test.com");
    MvcResult resultA = createProduct(cookiesA, "Produto A", "Cat A");
    long produtoIdA = extractProductId(resultA);

    // Criar restaurante B
    Cookie[] cookiesB = registerAndLogin("restaurante-b", "b@test.com");

    // Restaurante B tenta atualizar produto do restaurante A
    mockMvc.perform(put("/painel/produtos/" + produtoIdA)
            .with(csrf())
            .cookie(cookiesB)
            .contentType("application/json")
            .content("""
                {"nome":"Hackeado","descricao":"XSS","preco":0,"categoria":"Hack","imageUrl":""}
            """))
            .andExpect(status().isNotFound()); // ← deve ser 404, não 200

    // Restaurante B tenta deletar produto do restaurante A
    mockMvc.perform(delete("/painel/produtos/" + produtoIdA)
            .with(csrf())
            .cookie(cookiesB))
            .andExpect(status().isNotFound());

    // Verificar que o produto A está intacto
    mockMvc.perform(get("/painel/produtos/" + produtoIdA)
            .cookie(cookiesA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Produto A"));
}
```

**Teste necessário:** O próprio teste descrito acima.

---

### SEC-025 — js-cookie declarado mas não utilizado

**Severidade:** MELHORIA

**Arquivo:** `frontend/package.json`

**Problema:**

`"js-cookie": "^3.0.8"` está listado como dependência, mas não é importado em nenhum arquivo do frontend. O frontend lê cookies diretamente via `document.cookie.match(...)`.

**Alteração recomendada:** Remover da lista de dependências:

```bash
cd frontend && npm uninstall js-cookie
```

**Teste necessário:** `npm run build` deve completar sem erros.

---

### SEC-026 — Frontend não valida tamanho da imagem antes do upload

**Severidade:** MELHORIA

**Arquivo:** `frontend/src/components/ProductEditorModal.jsx`, `EstablishmentEditorModal.jsx`

**Problema:**

O backend limita uploads a 5MB (`spring.servlet.multipart.max-file-size`), mas o frontend não valida o tamanho antes de enviar. O usuário seleciona uma imagem de 20MB, espera o upload, e recebe erro.

**Alteração recomendada:**

Adicionar validação no `onChange` do input file:

```jsx
onChange={event => {
  const selectedFile = event.target.files?.[0] || null;
  if (selectedFile && selectedFile.size > 5 * 1024 * 1024) {
    setError("Imagem deve ter no máximo 5MB");
    return;
  }
  setFile(selectedFile);
}}
```

**Teste necessário:** Selecionar imagem > 5MB → mensagem de erro exibida sem request ao servidor.

---

### SEC-027 — Bug no MenuShell.jsx com prop products

**Severidade:** MELHORIA

**Arquivo:** `frontend/src/components/MenuShell.jsx`

**Problema:**

O componente `MenuShell` recebe `products` como prop (vide `App.jsx`), mas na assinatura da função o destructuring não inclui `products`:

```jsx
export function MenuShell({
  username,
  establishment,
  theme,
  mode = "public",
  loading = false,
  error = "",
  actionError = "",
  // ... products NÃO está no destructuring
```

Porém no corpo usa `products` diretamente:
```jsx
const grouped = useMemo(() => groupByCategory(products, isAdmin), [isAdmin, products]);
```

Isso funciona porque `products` provavelmente vem do escopo do componente pai via closure. Mas é um bug potencial.

**Alteração recomendada:** Adicionar `products = []` ao destructuring da prop.

**Teste necessário:** Verificar que o cardápio renderiza corretamente com e sem produtos.

---

### SEC-028 — Falta health check protegido com detalhes

**Severidade:** MELHORIA

**Arquivo:** `src/main/resources/application.properties` — linhas 10-11

**Problema:**

```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.probes.enabled=true
```

O health endpoint está público e expõe apenas `UP/DOWN`. Para monitoramento em produção, pode ser necessário expor detalhes (conectividade do banco, Supabase, etc.) para administradores autenticados.

**Alteração recomendada:** Manter o endpoint básico público e adicionar detalhes apenas para autenticados:

```properties
management.endpoint.health.show-details=when-authorized
management.endpoint.health.roles=ADMIN
```

**Teste necessário:** 
1. Acesso anônimo a `/actuator/health` → mostra apenas `{"status":"UP"}`
2. Acesso autenticado como ADMIN → mostra detalhes dos checks

---

## 6. Controles Já Implementados Corretamente

| Controle | Implementação | Arquivo |
|---|---|---|
| **Isolamento de tenant em produtos** | `findByEstabelecimentoAndId()` em todas as operações CRUD | `AdminProdutoServiceImpl.java` |
| **Estabelecimento derivado da autenticação** | `SecurityUtils.getUsuarioAutenticado().getEstabelecimento()` | `AdminProdutoServiceImpl.java`, `EstabelecimentoServiceImpl.java` |
| **JWT com tipo separado** | Claims `type: "access"` e `type: "refresh"` evitam token confusion | `TokenService.java` |
| **Secret key forte** | Validação `secret.length() >= 32` no `@PostConstruct` | `TokenService.java` |
| **Cookies HttpOnly** | `httpOnly(true)` em access_token e refresh_token | `TokenCookieService.java` |
| **Cookie Secure configurável** | `secure` vem de property, default `true` em prod | `TokenCookieService.java` |
| **CSRF habilitado** | Cookie-to-header pattern com `SpaCsrfTokenRequestHandler` | `SecurityConfig.java`, `SpaCsrfTokenRequestHandler.java` |
| **CSRF bypass para Bearer** | Requests com header `Authorization: Bearer` ignoram CSRF corretamente | `SecurityConfig.java` |
| **BCrypt** | `BCryptPasswordEncoder` para senhas | `SecurityConfig.java` |
| **Validação de entrada robusta** | Bean Validation em todos os DTOs (NotBlank, Size, Pattern, Email, etc.) | DTOs em `model/dto/` |
| **Upload com magic bytes** | Verifica assinatura binária do arquivo (JPEG, PNG, GIF, WEBP) | `SupabaseStorageService.java` |
| **Upload com MIME whitelist** | Aceita apenas `image/jpeg`, `image/png`, `image/webp`, `image/gif` | `SupabaseStorageService.java` |
| **Upload com filename sanitization** | UUID prefix + regex sanitization | `SupabaseStorageService.java` |
| **Path traversal protection** | Rejeita `/` e `\\` em filenames de download | `SupabaseStorageService.java` |
| **Log sanitization** | `LogSanitizer.sanitize()` contra CRLF injection | `LogSanitizer.java` |
| **Error response sanitizada** | `server.error.include-stacktrace=never`, `include-message=never` | `application.properties` |
| **REST error handlers** | JSON formatado sem stack traces em 401/403 | `RestAuthenticationEntryPoint.java`, `RestAccessDeniedHandler.java` |
| **Exception handler** | `ApiExceptionHandler` sem exposição de internals | `ApiExceptionHandler.java` |
| **Security headers** | HSTS 1yr + subdomains, X-Frame-Options DENY, nosniff, referrer-policy | `SecurityConfig.java` |
| **Swagger desabilitado em prod** | `@Profile({"dev", "local-postgres"})` + properties `springdoc.*.enabled=false` | `OpenApiConfig.java`, `application-prod.properties` |
| **Actuator restrito** | Apenas `health` e `info` expostos | `application.properties` |
| **Setup admin condicional** | `@ConditionalOnProperty` + verificação de existência no banco | `SetupController.java`, `AdminEstabelecimentoServiceImpl.java` |
| **Slug sanitization** | Regex `[^a-z0-9_-]+` em geração de slug | `AdminEstabelecimentoServiceImpl.java` |
| **Enumeração de usuários mitigada** | Mensagem genérica "Credenciais inválidas" em login | `AuthenticationController.java` |
| **Docker non-root** | `USER app` no Dockerfile | `Dockerfile` |
| **Docker multi-stage** | Build separado de runtime (JDK vs JRE Alpine) | `Dockerfile` |
| **Hibernate validate** | `spring.jpa.hibernate.ddl-auto=validate` (não auto-gera schema) | `application.properties` |
| **OSIV desabilitado** | `spring.jpa.open-in-view=false` | `application.properties` |
| **Upload size limit** | `max-file-size=5MB`, `max-request-size=5MB` | `application.properties` |
| **Tema com whitelist** | Enum `CardapioTema` com validação de valor | `CardapioTema.java` |
| **Frontend sem dangerouslySetInnerHTML** | Zero ocorrências no codebase React | Frontend inteiro |
| **Frontend sem tracking** | Nenhum Google Analytics, Meta Pixel, Hotjar, Sentry, etc. | `index.html` e todos os JS/JSX |
| **Frontend sem scripts de terceiros** | Nenhum CDN externo ou biblioteca carregada via script tag | `index.html` |
| **Gitignore correto** | `.env`, `.env.*`, `node_modules`, `target/`, etc. ignorados | `.gitignore` |
| **DB revalidação em cada request** | `SecurityFilter` carrega usuário do banco a cada request | `SecurityFilter.java` |

---

## 7. Estado Atual vs Estado Desejado

| Aspecto | Estado Atual | Estado Desejado |
|---|---|---|
| Nomenclatura de roles | `ROLE_ADMIN` = super-admin, `ROLE_USER` = admin do restaurante | `ROLE_SUPER_ADMIN` = super-admin, `ROLE_ADMIN` = admin do restaurante |
| Token no corpo | accessToken retornado no body e no cookie | accessToken somente no cookie HttpOnly |
| Rate limiting | Inexistente | Pelo menos login com limite de 5 tentativas/minuto/IP |
| Revogação de refresh token | Não implementada | Token family com jti ou blocklist |
| CSP header | Ausente | `default-src 'self'; script-src 'self'; ...` |
| Audit logging | Parcial (apenas 401/403) | Login, logout, CRUD de admin, criação/deleção de estabelecimentos |
| IDOR tests | Inexistente | Pelo menos 1 teste de cross-tenant product access |
| CI frontend | Inexistente | `npm test` + `npm run build` + `npm audit` |
| Bootstrap admin | Promove usuário existente | Rejeita se username já existe |
| @PreAuthorize em controllers | Parcial | Em todos os controllers privados |
| Supabase project ID | Hardcoded em properties | Sem valores padrão, somente via env vars |
| localStorage | Contém email do usuário | sessionStorage sem email |

---

## 8. LGPD — Análise Completa

### 8.1 Visitante Público (cliente final)

| Item | Status | Detalhes |
|---|---|---|
| Cria conta? | ❌ Não | Visitante apenas visualiza o cardápio |
| Fornece dados pessoais? | ❌ Não | Nenhum formulário de cadastro/pedido |
| Login? | ❌ Não | Acesso totalmente anônimo |
| Cookies de sessão? | ❌ Não | Nenhum cookie definido para visitante |
| Cookies de tracking? | ❌ Não | Nenhum cookie de analytics |
| Google Analytics? | ❌ Não instalado | Nenhum script GA no `index.html` |
| Meta Pixel? | ❌ Não instalado | Nenhum script Meta no `index.html` |
| Hotjar? | ❌ Não instalado | Nenhum script Hotjar |
| Sentry? | ❌ Não instalado | Nenhum SDK Sentry |
| Scripts de terceiros? | ❌ Nenhum | `index.html` limpo, sem CDN externo |
| Access logs? | ⚠️ **Sim** | O servidor web (Tomcat) registra IP, User-Agent, URLs por padrão |
| CSRF cookie? | ❌ Não para visitante | Cookie XSRF-TOKEN só é emitido quando o frontend chama `/auth/csrf` |

**Conclusão LGPD para visitante:**

O sistema **não coleta dados pessoais ativamente** do visitante. Porém, os **logs do servidor** registram o IP do visitante, que é considerado dado pessoal pela LGPD (Art. 5º, I — dado pessoal é "informação relacionada a pessoa natural identificada ou identificável").

**Ação recomendada:**
1. Definir período de retenção dos access logs (ex: 30 dias)
2. Documentar na Política de Privacidade que logs de acesso são mantidos para segurança
3. NÃO é necessário criar banner de cookies pois não há cookies opcionais
4. NÃO é necessário consentimento para logs de segurança (base legal: legítimo interesse — Art. 7º, IX)

### 8.2 ADMIN do Restaurante

| Item | Status | Detalhes |
|---|---|---|
| Dados coletados | Username, email, senha (BCrypt) | Necessários para autenticação |
| Base legal LGPD | Execução de contrato (Art. 7º, V) | O admin contratou o serviço |
| Senha armazenada | BCrypt hash | ✅ Correto |
| Email exposto no localStorage | ⚠️ Sim | Ver SEC-015 |
| Email no JWT | ❌ Não | JWT contém apenas username e tipo |
| Dados no token de autenticação | Username (subject) | ✅ Mínimo necessário |
| Direito de acesso (Art. 18, II) | Não implementado | O admin pode ver seus dados via `/painel/estabelecimento` |
| Direito de correção (Art. 18, III) | Parcial | Pode editar dados do estabelecimento, mas não username/email |
| Direito de exclusão (Art. 18, VI) | Não implementado | Não há endpoint para o admin solicitar exclusão da conta |
| Exportação de dados (Art. 18, V) | Não implementado | Não há endpoint para exportar dados |

**Ações recomendadas para LGPD do ADMIN:**
1. Criar endpoint para o ADMIN atualizar seu email (com validação)
2. Criar endpoint ou processo para solicitar exclusão da conta
3. Documentar Política de Privacidade
4. Mover email do `localStorage` para `sessionStorage` (SEC-015)

### 8.3 Dados do Estabelecimento

Os dados do estabelecimento (nome, descrição, telefone, endereço, email de contato) são dados **da pessoa jurídica**, não da pessoa física, portanto não são dados pessoais segundo a LGPD (Art. 1º — "pessoa natural"). Porém:

- Se o estabelecimento for de pessoa física (MEI, autônomo), o telefone e email podem ser pessoais
- O nome do estabelecimento pode coincidir com o nome do proprietário

**Ação recomendada:** Na Política de Privacidade, esclarecer que dados do estabelecimento serão exibidos publicamente no cardápio.

---

## 9. Plano de Implementação para o Codex

### Fase 1 — Correções Críticas (Prioridade Máxima)

| Ordem | ID | Descrição | Esforço |
|---|---|---|---|
| 1.1 | SEC-001 | Corrigir BootstrapAdminRunner para rejeitar username existente | 30 min |
| 1.2 | SEC-003 | Remover accessToken do corpo da resposta de login/refresh | 1h |
| 1.3 | SEC-005 | Null check em SecurityUtils.getUsuarioAutenticado() | 15 min |

### Fase 2 — Segurança Alta (1-2 dias)

| Ordem | ID | Descrição | Esforço |
|---|---|---|---|
| 2.1 | SEC-006 | Remover CSRF ignore do logout | 30 min |
| 2.2 | SEC-010 | Adicionar @PreAuthorize em controllers do painel | 30 min |
| 2.3 | SEC-013 | Implementar rate limiting no login | 2h |
| 2.4 | SEC-011 | Remover project ID hardcoded de properties | 30 min |
| 2.5 | SEC-008 | Adicionar token de setup ou desabilitar explicitamente em prod | 1h |
| 2.6 | SEC-004 | Restringir `/painel/**` a ROLE_USER nos controllers de produto | 30 min |

### Fase 3 — Melhorias Médias (2-3 dias)

| Ordem | ID | Descrição | Esforço |
|---|---|---|---|
| 3.1 | SEC-016 | Adicionar Content-Security-Policy | 2h |
| 3.2 | SEC-015 | Migrar localStorage para sessionStorage, remover email | 1h |
| 3.3 | SEC-007 | Implementar revogação de refresh token | 4h |
| 3.4 | SEC-014 | Desabilitar followRedirect no WebClient | 15 min |
| 3.5 | SEC-017 | Implementar limite de storage por tenant | 3h |
| 3.6 | SEC-012 | Verificar e limpar dados seed em produção | 1h |
| 3.7 | SEC-009 | Documentar comportamento de roles no código | 15 min |
| 3.8 | SEC-021 | Adicionar testes frontend e audit no CI | 1h |
| 3.9 | SEC-023 | Definir política de retenção de logs | 1h |

### Fase 4 — Melhorias de Baixa Prioridade

| Ordem | ID | Descrição | Esforço |
|---|---|---|---|
| 4.1 | SEC-002 | Renomear roles (ROLE_ADMIN → ROLE_SUPER_ADMIN, ROLE_USER → ROLE_ADMIN) | 4h |
| 4.2 | SEC-024 | Criar teste de IDOR cross-tenant | 2h |
| 4.3 | SEC-018 | Verificar flag ativo em CustomUserDetails | 30 min |
| 4.4 | SEC-019 | Implementar audit logging | 2h |
| 4.5 | SEC-020 | Bind porta 5432 a localhost no Docker Compose | 5 min |
| 4.6 | SEC-022 | Adicionar Permissions-Policy header | 15 min |
| 4.7 | SEC-025 | Remover js-cookie do package.json | 5 min |
| 4.8 | SEC-026 | Validar tamanho de imagem no frontend | 30 min |
| 4.9 | SEC-027 | Corrigir destructuring em MenuShell.jsx | 5 min |
| 4.10 | SEC-028 | Health check com detalhes para autenticados | 15 min |

---

## 10. Checklist Final

### Autenticação
- [ ] SEC-001: BootstrapAdminRunner rejeita username existente
- [ ] SEC-003: accessToken removido do corpo da resposta
- [ ] SEC-005: SecurityUtils com null check
- [ ] SEC-007: Refresh token com revogação server-side
- [ ] SEC-018: CustomUserDetails verifica flag ativo

### Autorização
- [ ] SEC-002: Roles renomeadas (SUPER_ADMIN/ADMIN)
- [ ] SEC-004: Controllers de produto restritos a ROLE_USER
- [ ] SEC-009: Comportamento de roles documentado
- [ ] SEC-010: @PreAuthorize em todos os controllers privados

### Spring Security
- [ ] SEC-006: CSRF ativo no logout
- [ ] SEC-008: Setup endpoint protegido
- [ ] SEC-013: Rate limiting no login
- [ ] SEC-016: Content-Security-Policy
- [ ] SEC-022: Permissions-Policy

### Multi-tenancy
- [ ] SEC-024: Teste de IDOR cross-tenant implementado

### Frontend
- [ ] SEC-015: sessionStorage em vez de localStorage
- [ ] SEC-025: js-cookie removido
- [ ] SEC-026: Validação de tamanho de imagem
- [ ] SEC-027: MenuShell.jsx destructuring corrigido

### Infraestrutura
- [ ] SEC-011: Project ID removido de properties
- [ ] SEC-012: Dados seed verificados em produção
- [ ] SEC-014: WebClient sem followRedirect
- [ ] SEC-017: Limite de storage por tenant
- [ ] SEC-019: Audit logging implementado
- [ ] SEC-020: Docker Compose porta localhost only
- [ ] SEC-021: CI com testes frontend
- [ ] SEC-028: Health check detalhado para admins

### LGPD
- [ ] SEC-023: Política de retenção de logs definida
- [ ] Política de Privacidade criada
- [ ] Email removido do localStorage
- [ ] Endpoint de exclusão de conta (ADMIN)

---

## 11. Testes de Segurança Necessários

### Testes de integração a implementar

| # | Teste | Endpoint | Método | Esperado |
|---|---|---|---|---|
| T-01 | IDOR: tenant A altera produto de tenant B | `PUT /painel/produtos/{idB}` | PUT com cookie de A | 404 |
| T-02 | IDOR: tenant A deleta produto de tenant B | `DELETE /painel/produtos/{idB}` | DELETE com cookie de A | 404 |
| T-03 | IDOR: tenant A consulta produto de tenant B | `GET /painel/produtos/{idB}` | GET com cookie de A | 404 |
| T-04 | ROLE_ADMIN bloqueado em produto | `POST /painel/produtos` | POST com cookie ADMIN | 403 |
| T-05 | ROLE_USER bloqueado em plataforma | `POST /plataforma/estabelecimentos` | POST com cookie USER | 403 |
| T-06 | Logout sem CSRF rejeitado | `POST /auth/logout` | POST sem X-XSRF-TOKEN | 403 |
| T-07 | Login brute force bloqueado | `POST /auth/login` | 6 tentativas em 1 min | 429 na 6ª |
| T-08 | Refresh token revogado após logout | `POST /auth/refresh` | POST após logout | 401 |
| T-09 | Estabelecimento desativado bloqueia login | `POST /auth/login` | Login com estab desativado | 401 |
| T-10 | Bootstrap com username existente falha | Startup | Config com user existente | IllegalStateException |
| T-11 | Setup admin com admin existente | `POST /setup/admin` | POST quando admin existe | 409 |
| T-12 | Upload > 5MB rejeitado | `POST /painel/storage/upload` | File de 6MB | 400/413 |
| T-13 | Path traversal em download | `GET /public/storage/../../etc/passwd` | GET | 400 |
| T-14 | Token expirado rejeitado | Qualquer autenticado | Cookie com token expirado | 401 |
| T-15 | Refresh token usado como access | Qualquer autenticado | Cookie com refresh token | 401 |

### Testes manuais recomendados

1. **XSS stored:** Inserir `<script>alert(1)</script>` no campo nome do produto via API. Verificar que ao visualizar o cardápio público, o script NÃO é executado (React escapa por padrão).

2. **SQL Injection:** Enviar `' OR '1'='1` nos parâmetros de busca. Verificar que a query não é alterada (Spring Data usa prepared statements por padrão).

3. **CORS:** Fazer fetch de uma origem não permitida. Verificar que o browser bloqueia a resposta.

4. **Cookie scope:** Verificar que cookies `access_token` e `refresh_token` têm:
   - `HttpOnly: true`
   - `Secure: true` (em produção)
   - `SameSite` configurado
   - `Path: /`

5. **JWT tampering:** Alterar o payload do JWT (ex: mudar username) sem alterar a assinatura. Verificar que o servidor rejeita com 401.

---

> **NOTA FINAL:** Este documento é resultado de uma auditoria read-only. Nenhum arquivo do projeto foi modificado. Todas as recomendações devem ser implementadas pelo Codex seguindo as prioridades definidas na Fase 1-4 do plano de implementação.
