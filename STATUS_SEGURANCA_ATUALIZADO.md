# Status Unificado de Auditoria de Segurança e LGPD

**Data da Atualização:** 2026-09-29
**Projeto:** cardapio-saas

Este documento unifica os relatórios anteriores (`AUDITORIA_SEGURANCA_LGPD.md`, `RELATORIO_CORRECOES_SEGURANCA_ALTO.md` e considerações de segurança do `README.md`), fornecendo um panorama atualizado do que já foi resolvido na Fase 1 (focada em itens críticos e altos) e as instruções claras para as correções que ainda estão pendentes.

---

## 1. Resumo Executivo e Estado Atual

A arquitetura do projeto continua baseada em Spring Boot 3.3 (Java 21) e React 18 (Vite). Durante a Fase 1 de correções de segurança, o sistema de autorização, autenticação (cookies e CSRF) e multi-tenancy sofreu um fortalecimento e reestruturação, validado por baterias robustas de testes integrados.

O foco das correções já aplicadas foi mitigar as vulnerabilidades de grau **CRÍTICO** e **ALTO**, garantindo que não existam vetores iminentes de escalação de privilégios ou comprometimento severo de dados no sistema base.

### Resumo de Achados (Total original: 28)
- **RESOLVIDO / MITIGADO no código e validado localmente:** 26 achados.
- **PARCIAL / DEPENDE DE INFRA:** 2 achados (`SEC-016` e `SEC-023`).
- **PENDENTE no código dentro do escopo auditado:** 0 achados.

---

## 2. Correções Já Realizadas (Fase 1)

Os seguintes itens já foram mitigados, validados por testes e integrados com sucesso:

| ID | Severidade | Categoria | Descrição / Solução Aplicada |
|---|---|---|---|
| **SEC-001** | CRÍTICO | Autenticação | **Revisado:** O comportamento de abortar o bootstrap para identidade existente (e exigir desabilitação posterior em vez de escalar privilégios) foi validado e mantido íntegro. |
| **SEC-003** | CRÍTICO | Autenticação | **Resolvido:** O `accessToken` e o `refreshToken` não são mais retornados no corpo da resposta JSON, prevenindo capturas via XSS. O fluxo de autenticação utiliza exclusivamente Cookies HttpOnly. |
| **SEC-002** | ALTO | Multi-tenancy | **Resolvido (Migration V10):** Roles reestruturadas para remover ambiguidades. A antiga `ROLE_ADMIN` virou `ROLE_PLATFORM_ADMIN` e a antiga `ROLE_USER` virou `ROLE_TENANT_ADMIN`. |
| **SEC-004** | ALTO | Spring Security | **Resolvido:** Separação estrita entre `/painel/**` (exige administrador do tenant) e `/plataforma/**` (exige administrador da plataforma). Administradores da plataforma não mais acessam implicitamente rotas de gestão dos tenants. |
| **SEC-005** | ALTO | Segurança | **Resolvido:** `SecurityUtils.getUsuarioAutenticado()` foi atualizado para retornar `null` preventivamente (evitando NPE) para contextos ausentes ou credenciais incompletas. |
| **SEC-006** | ALTO | Spring Security | **Resolvido:** O fluxo de `/auth/logout` agora não é mais ignorado e **exige** validação CSRF. Token mascarado com XOR no frontend é enviado com segurança via `X-CSRF-TOKEN`. |
| **SEC-008** | ALTO | Segurança | **Resolvido:** O endpoint condicional de setup agora é desabilitado por padrão e restrito, exigindo header com um `APP_SETUP_ADMIN_TOKEN` seguro e verificação CSRF em conjunto com um bloqueio (lock pessimista) no banco contra corridas de criação. |
| **SEC-010** | MÉDIO | Segurança | **Mitigado (com SEC-004):** Adicionadas validações de método (`@PreAuthorize`) que faltavam em `AdminProdutoController`, `AdminCategoriaController` e `SupabaseStorageController`. |
| **SEC-011** | ALTO | Infraestrutura | **Resolvido:** Removidos todos os valores hardcoded de projeto Supabase no arquivo profile, tornando obrigatória a passagem das variáveis (`DATABASE_URL`, `SUPABASE_URL`, etc). |
| **SEC-013** | ALTO | Segurança | **Resolvido:** Implementado um limite rigoroso de requisições em memória (Rate Limit) por IP para: `/auth/login` (5/min), `/auth/refresh` (30/min), `/setup/admin` (5/min) e chamadas `/public/**` (300/min). |
| **SEC-024** | BAIXO | Testes | **Mitigado (com SEC-004):** Adicionados testes específicos para verificar as restrições de autorização de produtos entre tenants. |

> **Nota Adicional (SEC-009):** O achado referente à restrição do `EstabelecimentoController` em "excluir ADMIN" foi funcionalmente resolvido pela introdução do `ROLE_PLATFORM_ADMIN` (SEC-002 e SEC-004). Como um admin de plataforma genuinamente não deve acessar as rotas de tenant (painel do cliente), o comportamento agora é o esperado do sistema.

---

## 2.1. Correções da Fase 2 — Parte 1 (2026-09-29)

| ID | Status | Solução e evidência |
|---|---|---|
| **SEC-007** | Resolvido no código | Sessões persistidas pela V11, hash de jti, rotação transacional, revogação por sessão no logout e rejeição de replay. Testes de concorrência e recriação completa do contexto Spring com banco H2 em arquivo. |
| **SEC-012** | Resolvido no código | V12 inutiliza a credencial da seed identificada por hash, username, email e vínculo ao slug. Não remove usuários nem dados comerciais; preserva contas modificadas. V4 e demais migrations anteriores intactas. |
| **SEC-015** | Resolvido no código | Metadados somente em memória, limpeza do storage legado e reconstrução sob demanda ao entrar em rota protegida por `/auth/validate`. Rotas públicas não iniciam validate/refresh. Testes de reload protegido, logout, expiração e falhas transitórias/429. |

Validação: 82 testes backend e 33 frontend aprovados; build frontend e `git diff --check` aprovados. Ver `RELATORIO_CORRECOES_SEGURANCA_MEDIO_PARTE1.md` para comandos, arquivos, decisões e limitações. Correções implementadas e verificadas localmente; não houve implantação nem verificação do banco de produção/Supabase. SEC-018 continua pendente como revisão integral: somente emissão/renovação de sessão verifica estabelecimento ativo nesta etapa.

## 2.2. Correções da Fase 2 — Parte 2 (2026-09-29)

| ID | Estado | Implementação / pendência |
|---|---|---|
| **SEC-014** | Resolvido no código | Redirects automáticos desabilitados; 3xx recusados em upload/download/delete; origem fixa da configuração, paths restritos e teste de destino loopback não acessado. |
| **SEC-017** | Resolvido no código | V13, reserva persistente com lock por tenant, quota configurável de 100 MiB, tratamento conservador de falhas e rastreamento de órfãos. Tenants preexistentes ficam impedidos de enviar até conciliar inventário; limpeza automática não implementada. |
| **SEC-026** | Resolvido no código | Validação central de tamanho, MIME/extensão nos dois editores e cliente de upload; testes comprovam ausência de HTTP para arquivo acima do limite. |
| **SEC-016** | Implementado no repositório; verificação de produção pendente | CSP no HTML de build e no servidor estático; headers gerados para host externo. Header efetivo do host externo, incluindo frame-ancestors, ainda precisa ser verificado após implantação. |
| **SEC-023** | Minimização implementada; retenção operacional pendente | Logs próprios sem identidade textual/URL bruta/cadeia de exceções; configurações de produção restritas. Retenção de 30 dias precisa ser aplicada e verificada no coletor/hospedagem. |

Validação local: **100 testes backend, 40 frontend, build e checagem HTTP do HTML compilado aprovados**. Ver `RELATORIO_CORRECOES_SEGURANCA_MEDIO_PARTE2.md` e `OPERACAO_STORAGE_E_LOGS.md`. Não houve implantação, inventário do bucket real ou verificação de coletor de produção. Os dois itens com dependência de hospedagem não foram contabilizados como concluídos.

## 2.3. Correções finais — Fase 3 (2026-09-29)

| ID | Estado | Implementação / evidência |
|---|---|---|
| **SEC-018** | **RESOLVIDO** | `CustomUserDetails.isEnabled()` e `SecurityFilter` consultam o estabelecimento atual em cada request JWT. Tenant inativo perde a role de tenant e não autentica; platform admin permanece habilitado. Testes cobrem login, JWT anterior, mutação com CSRF válido, reativação, plataforma e isolamento. |
| **SEC-019** | **RESOLVIDO** | Logger central `SECURITY_AUDIT` registra operações administrativas, login, logout, revogação e setup somente com eventos de enum e IDs internos. Teste captura o evento e comprova ausência de PII, segredo e CRLF. |
| **SEC-020** | **RESOLVIDO** | PostgreSQL do compose vinculado a `127.0.0.1:5432`; API preserva `jdbc:postgresql://db:5432/cardapio`. Docker não estava instalado para executar `docker compose config`. |
| **SEC-021** | **RESOLVIDO** | CI passou a executar backend Java 21, frontend Node 22 (`npm ci`, testes e build), Dependency Review para severidade alta e CodeQL para Java e JavaScript/TypeScript, com permissões mínimas. Execução remota depende de push/PR no GitHub. |
| **SEC-022** | **RESOLVIDO** | `Permissions-Policy: camera=(), microphone=(), geolocation=()` adicionado e testado. |
| **SEC-025** | **RESOLVIDO** | Confirmada ausência de imports/usos e removido `js-cookie` por npm, mantendo manifest e lock consistentes. |
| **SEC-027** | **RESOLVIDO** | `MenuShell` usa `products = []`; teste cobre prop omitida, mantendo os testes de lista populada e vazia. |
| **SEC-028** | **RESOLVIDO** | Health público/probes retornam status mínimo; grupo `health/admin` com componentes exige `ROLE_PLATFORM_ADMIN`, recusa tenant e não expõe detalhes arbitrários. |

Validação final: **106 testes backend e 42 testes frontend aprovados; build Vite aprovado com 51 módulos; regressão `SpaCsrfIntegrationTests` aprovada; `git diff --check` aprovado**. A estratégia `NullAuthenticatedSessionStrategy` permanece diretamente no `CsrfConfigurer`. Nenhuma migration foi criada nesta fase.

## 3. Pendências externas

| ID | Estado | Ação restante |
|---|---|---|
| **SEC-016** | **PARCIAL / DEPENDE DE INFRA** | Aplicar e verificar no host real o header CSP gerado com o frontend, especialmente `frame-ancestors`. |
| **SEC-023** | **PARCIAL / DEPENDE DE INFRA** | Configurar e verificar retenção de 30 dias, acesso restrito e minimização no coletor, proxy/CDN e backups da hospedagem. O novo logger de auditoria também deve seguir essa política. |

---

## 4. Próximos Passos (Plano de Ação Sugerido)

1. Publicar a branch/PR para que GitHub Actions execute testes, Dependency Review e CodeQL no ambiente remoto.
2. Validar `docker compose config` e a subida dos serviços em uma máquina com Docker.
3. Aplicar a CSP e a política de logs no ambiente real, mantendo acesso restrito ao logger `SECURITY_AUDIT`.
4. Repetir o smoke test de health e autenticação no ambiente de homologação antes da produção.
