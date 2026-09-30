# Correções finais de segurança — Fase 3

Data: 2026-09-29. Escopo: SEC-018, SEC-019, SEC-020, SEC-021, SEC-022, SEC-025, SEC-027 e SEC-028.

Esta etapa partiu do código resultante das Fases 1 e 2 e da correção posterior de CSRF. A auditoria original foi usada apenas como histórico. Foram preservados cookies JWT HttpOnly, rotação e revogação server-side do refresh, separação de roles, multi-tenancy, quota de storage, CSP existente e o fluxo SPA de CSRF. Em particular, `sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())` permanece diretamente no `CsrfConfigurer`.

## Baseline desta execução

Antes das alterações da Fase 3, a validação local registrou:

| Verificação | Resultado |
|---|---|
| Backend Maven | 100 testes aprovados, sem falhas ou erros. |
| Frontend Vitest | 41 testes aprovados em 6 arquivos. |
| Build Vite | Aprovado, 51 módulos transformados. |
| `git diff --check` | Aprovado; apenas avisos de conversão LF/CRLF do Git no Windows. |

O executável `mvn` não está no PATH e `mvnw.cmd` retorna acesso negado neste ambiente. A suíte foi executada pelo launcher do Maven 3.9.11 já baixado pelo wrapper, com o repositório local existente. O código foi compilado com `release 21`; a JVM disponível localmente foi Java 25.0.3.

## SEC-018 — estabelecimento inativo

**Estado encontrado:** parcialmente mitigado. Login/refresh já consultavam o estabelecimento em parte do fluxo, mas `CustomUserDetails` não implementava `isEnabled()` e o `SecurityFilter` reconstruía manualmente a autenticação de um access JWT sem verificar esse estado. Assim, um JWT emitido antes da desativação continuava autorizando o painel.

**Mudança:** `CustomUserDetails.isEnabled()` agora exige estabelecimento ativo para tenant admin. A coleção de authorities remove `ROLE_TENANT_ADMIN` de estabelecimento inativo. `SecurityFilter`, que recarrega usuário, roles e estabelecimento do banco a cada request JWT, rejeita explicitamente um usuário desabilitado antes de criar o `SecurityContext`. `RefreshSessionService` mantém a mesma regra para tenant, com exceção deliberada para `ROLE_PLATFORM_ADMIN`; isso evita desabilitar a administração da plataforma por causa do estabelecimento administrativo.

**Política de reativação:** o projeto não possui denylist de access JWT. Se o estabelecimento for reativado antes da expiração, o mesmo access token volta a funcionar porque o estado atual do banco é novamente favorável. Refresh permanece sujeito à sessão persistida e às regras de rotação/revogação já existentes.

**Testes adicionados:** `InactiveEstablishmentSecurityIntegrationTest` cobre tenant ativo, login de tenant inativo, access JWT emitido antes da desativação, GET do painel, mutação com CSRF válido, reativação, platform admin com estabelecimento administrativo inativo e isolamento entre dois tenants. A mutação inativa retorna 401 mesmo com CSRF válido, comprovando que a rejeição vem da autenticação e não de uma ausência artificial de CSRF.

**Arquivos:** `security/CustomUserDetails.java`, `security/SecurityFilter.java`, `security/RefreshSessionService.java` e o novo teste de integração.

**Estado final:** **RESOLVIDO**.

## SEC-019 — audit logging

**Estado encontrado:** havia sanitização de logs técnicos e minimização LGPD da Fase 2, mas não uma trilha central de eventos sensíveis de negócio. Não foi criado um segundo sanitizador.

**Mudança:** `SecurityAuditLogger` centraliza eventos em um logger SLF4J dedicado, `SECURITY_AUDIT`. O tipo do evento vem de enum e os demais campos aceitam somente IDs numéricos ou `-`, usando o `LogSanitizer` existente. O formato estável contém `event`, `actor_user_id`, `target_user_id`, `establishment_id` e `product_id`.

Eventos cobertos:

- criação, ativação, desativação e exclusão de estabelecimento;
- criação de platform admin;
- exclusão de produto;
- login aceito e login rejeitado;
- logout e revogação efetiva da refresh session;
- setup concluído e setup rejeitado.

Falha de login não registra o identificador apresentado. Nenhum evento recebe senha, email, username, Authorization, JWT, token de refresh, hash de refresh, cookie, setup token, CSRF token ou headers completos. Setup deixou de usar mensagens locais paralelas e passou pelo logger central. Revogação só gera evento quando uma sessão ainda não revogada é efetivamente marcada.

**Testes:** `LogPrivacyTest` captura o logger real e verifica formato, IDs, ausência de PII/segredos e ausência de CR/LF. Os fluxos integrados também exercitam login, logout, revogação e criação de estabelecimento.

**Arquivos:** novo `security/SecurityAuditLogger.java`; integrações em `AuthenticationController`, `SetupController`, `RefreshSessionService`, `AdminEstabelecimentoServiceImpl` e `AdminProdutoServiceImpl`; ajustes nos testes de privacidade e no contexto reduzido de setup.

**Limitação operacional:** a aplicação emite os eventos para stdout conforme a configuração atual. A retenção de 30 dias, controle de acesso, alertas e proteção contra alteração dependem do coletor/hospedagem e continuam incluídos na pendência externa de SEC-023.

**Estado final:** **RESOLVIDO** no código; operação dos logs continua vinculada a **SEC-023 — PARCIAL / DEPENDE DE INFRA**.

## SEC-020 — PostgreSQL no Docker

**Estado encontrado:** `docker-compose.yml` publicava `5432:5432`, vinculando a porta a todas as interfaces do host.

**Mudança:** publicação alterada para `127.0.0.1:5432:5432`. A API dentro da rede Compose continua usando `jdbc:postgresql://db:5432/cardapio`; não foi alterada para localhost.

**Validação e limitação:** inspeção do arquivo confirma os dois endereços corretos. O comando `docker` não está instalado neste host, portanto `docker compose config` e uma subida real não puderam ser executados.

**Arquivo:** `docker-compose.yml`.

**Estado final:** **RESOLVIDO** no repositório; validação operacional do Compose deve ser feita em ambiente com Docker.

## SEC-021 — CI/CD

**Estado encontrado:** o workflow executava somente `mvn -B test` com Java. Não havia frontend, SAST ou revisão de dependências.

**Mudança:** `.github/workflows/ci.yml` agora possui:

- backend com Java 21, cache Maven e `mvn -B test`;
- frontend com Node 22, cache npm, `npm ci`, `npm test` e `npm run build`;
- Dependency Review em pull requests, falhando para nova dependência vulnerável de severidade alta ou crítica;
- CodeQL v4 em matriz para `java-kotlin` e `javascript-typescript`, usando `build-mode: none`;
- permissões globais somente de leitura e `security-events: write` apenas no job CodeQL;
- SAST de PR executado quando a origem pertence ao próprio repositório, evitando falha de permissão do token em forks. Testes backend/frontend continuam executando em PRs de fork.

Não há `|| true`, segredo no YAML ou supressão de falha das suítes. Como `mvn -B test` executa toda a suíte, as regressões `SpaCsrfIntegrationTests`, login/validate/mutation e os testes de refresh continuam bloqueando o CI se quebrarem.

**Validação e limitação:** versões atuais foram confrontadas com documentação/repositórios oficiais do GitHub. `actionlint` e parser YAML não estavam instalados; a estrutura foi revisada localmente, mas Dependency Review e CodeQL só podem ser confirmados após push/PR. Dependency Review e CodeQL também dependem da disponibilidade/licenciamento do GitHub Security para o repositório.

**Arquivo:** `.github/workflows/ci.yml`.

**Estado final:** **RESOLVIDO** no repositório; primeira execução remota ainda é necessária.

## SEC-022 — Permissions-Policy

**Estado encontrado:** headers HSTS, frame options, content type e referrer já existiam; `Permissions-Policy` não existia.

**Mudança:** `SecurityConfig` adiciona `Permissions-Policy: camera=(), microphone=(), geolocation=()` por `StaticHeadersWriter`, sem alterar CORS. A configuração CSRF stateless foi preservada, inclusive `NullAuthenticatedSessionStrategy` dentro do `CsrfConfigurer`.

**Teste:** `CardapioApplicationTests.securityHeadersArePresent` exige o valor completo do header. Logo após a alteração, `SpaCsrfIntegrationTests` passou com 3 testes, 0 falhas.

**Arquivos:** `SecurityConfig.java` e `CardapioApplicationTests.java`.

**Estado final:** **RESOLVIDO**.

## SEC-025 — js-cookie

**Estado encontrado:** busca fora de `node_modules` e `dist` encontrou a dependência somente em `package.json` e `package-lock.json`; o cliente CSRF atual não a importa nem depende dela.

**Mudança:** `npm uninstall js-cookie` removeu a dependência e a entrada do lockfile. Nenhuma biblioteca substituta foi adicionada e o gerenciamento de CSRF não foi modificado.

**Validação:** 42 testes frontend e build Vite aprovados.

**Arquivos:** `frontend/package.json` e `frontend/package-lock.json`.

**Estado final:** **RESOLVIDO**.

## SEC-027 — MenuShell

**Estado encontrado:** `products` já estava corretamente declarado no destructuring, então a descrição antiga do bug não representava integralmente o código atual. A lacuna restante era a ausência de default; omitir a prop causava acesso a `products.length` sobre `undefined`. Lista preenchida e lista vazia já tinham cobertura.

**Mudança:** default mínimo `products = []`, sem alteração visual ou estrutural.

**Teste:** novo caso omite a prop e confirma o estado vazio existente (`Cardapio indisponivel`). Os testes anteriores de produtos existentes e lista vazia foram preservados.

**Arquivos:** `frontend/src/components/MenuShell.jsx` e `MenuShell.test.jsx`.

**Estado final:** **RESOLVIDO**.

## SEC-028 — Actuator / health

**Estado encontrado:** `health,info` estavam expostos e todo `/actuator/health/**` era público. O default não mostrava detalhes completos, mas não existia uma rota administrativa deliberada nem separação explícita de autorização.

**Mudança:** somente `health` permanece exposto. `show-components=never` e `show-details=never` protegem a visão pública. `/actuator/health`, `/liveness` e `/readiness` são públicos para infraestrutura. O grupo `/actuator/health/admin` contém somente `db` e `ping`, mostra os status dos componentes, não seus mapas de detalhes, e exige `ROLE_PLATFORM_ADMIN`. Tenant admin recebe 403.

O matcher CSRF existente do Actuator não foi ampliado nem usado para contornar autorização; os endpoints são GET. CORS permaneceu intacto.

**Teste:** anônimo recebe apenas `status` no health principal; tenant não acessa o grupo administrativo; platform admin recebe status e componentes permitidos, sem `details`.

**Arquivos:** `application.properties`, `SecurityConfig.java` e `CardapioApplicationTests.java`.

**Estado final:** **RESOLVIDO**.

## Validação final

| Verificação | Resultado final |
|---|---|
| Maven completo | **106 testes; 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS; 29,544 s.** |
| CSRF focado após `SecurityConfig`/autorização | **3 testes; 0 falhas; BUILD SUCCESS.** |
| Testes focados SEC-018/019/022/028 | **20 testes; 0 falhas; BUILD SUCCESS.** |
| Frontend Vitest | **42 testes em 6 arquivos; aprovados; 1,91 s.** |
| Build Vite | **Aprovado; 51 módulos; 174 ms.** |
| `git diff --check` | **Aprovado**, com avisos informativos LF/CRLF no Windows. |
| Docker Compose | Não executado: Docker indisponível. |
| Workflow GitHub Actions | Revisão local; execução real pendente de push/PR. |

Uma primeira execução completa do Maven falhou em um teste de contexto reduzido que instanciava `SetupController` sem registrar a nova dependência `SecurityAuditLogger`. O teste foi corrigido para fornecer esse mock, sem alterar código de produção nem enfraquecer assertion. A repetição completa resultou nos 106 testes aprovados acima.

Nenhum teste existente foi removido ou relaxado. A suíte final contém os 100 testes backend e os 41 testes frontend do baseline, além dos novos casos desta fase. O build permanece com 51 módulos.

## Arquivos da Fase 3

- `.github/workflows/ci.yml`
- `docker-compose.yml`
- `README.md`
- `STATUS_SEGURANCA_ATUALIZADO.md`
- `RELATORIO_CORRECOES_SEGURANCA_FINAL.md`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/src/components/MenuShell.jsx`
- `frontend/src/components/MenuShell.test.jsx`
- `src/main/java/com/wilgner/cardapio/config/SecurityConfig.java`
- `src/main/java/com/wilgner/cardapio/controller/AuthenticationController.java`
- `src/main/java/com/wilgner/cardapio/controller/SetupController.java`
- `src/main/java/com/wilgner/cardapio/security/CustomUserDetails.java`
- `src/main/java/com/wilgner/cardapio/security/RefreshSessionService.java`
- `src/main/java/com/wilgner/cardapio/security/SecurityAuditLogger.java`
- `src/main/java/com/wilgner/cardapio/security/SecurityFilter.java`
- `src/main/java/com/wilgner/cardapio/service/AdminEstabelecimentoServiceImpl.java`
- `src/main/java/com/wilgner/cardapio/service/AdminProdutoServiceImpl.java`
- `src/main/resources/application.properties`
- `src/test/java/com/wilgner/cardapio/CardapioApplicationTests.java`
- `src/test/java/com/wilgner/cardapio/config/DeploymentSecurityConfigurationTest.java`
- `src/test/java/com/wilgner/cardapio/security/InactiveEstablishmentSecurityIntegrationTest.java`
- `src/test/java/com/wilgner/cardapio/security/LogPrivacyTest.java`

Nenhuma migration foi criada ou alterada nesta fase.

## Ações antes de produção

1. Abrir push/PR e confirmar os quatro jobs do GitHub Actions; habilitar GitHub Code Security/Dependency Review se o plano do repositório exigir.
2. Executar `docker compose config` e um smoke test do Compose em máquina com Docker, confirmando que o host expõe PostgreSQL somente em `127.0.0.1` e que a API alcança `db:5432`.
3. Em homologação, desativar um tenant com sessão aberta e confirmar 401 imediato; reativar e confirmar a política documentada.
4. Configurar coleta, acesso restrito, alertas e retenção de 30 dias para `SECURITY_AUDIT`, sem enriquecer eventos com headers, cookies ou identidade textual.
5. Aplicar/verificar o header CSP no host real do frontend e concluir a pendência de SEC-016.
6. Verificar os probes públicos e `/actuator/health/admin` através do proxy real, sem publicar outros endpoints Actuator.
