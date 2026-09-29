# Revisão da Fase 1 e correções de severidade ALTO

Data: 29/09/2026. Escopo autorizado: revisar SEC-001/003 e corrigir somente SEC-002, SEC-004, SEC-005, SEC-006, SEC-008, SEC-011 e SEC-013, incluindo suas dependências e testes de regressão.

## Revisão da Fase 1

Foram revisados o diff de produção, documentação, testes novos e alterados, os consumidores do DTO de autenticação, o fluxo de cookies/CSRF/refresh/logout, a transação do bootstrap e as constraints de unicidade do banco. Não foi identificada regressão funcional nas alterações da Fase 1 dentro dos cenários verificados.

A revisão identificou uma lacuna no teste de colisão de username: a conta também tinha email coincidente. O teste agora usa um email novo, isolando a colisão de username; o caso de email já é testado separadamente. Assim, uma implementação incorreta que verificasse apenas email não passaria despercebida.

Antes de iniciar os achados ALTO: `mvn -B test` aprovou os 45 testes, `npm test` aprovou os 19 testes e `npm run build` passou. Após fortalecer o cenário de username, os 11 testes do bootstrap passaram novamente. Os mesmos testes também integram a suíte final com as novas roles.

O comportamento intencional permanece: bootstrap aborta para identidade existente e precisa ser desabilitado depois do cadastro inicial; JWTs não são retornados no JSON.

## Achados e correções

### SEC-002 — Roles explícitas e migração dos vínculos

**Validação:** `ROLE_ADMIN` identificava a plataforma e `ROLE_USER` identificava o estabelecimento. Isso não constitui, por si só, uma inversão universal de convenção nem uma vulnerabilidade explorável, mas a ambiguidade apontada pela auditoria existia.

**Solução:** `ROLE_ADMIN` passa a `ROLE_PLATFORM_ADMIN`; `ROLE_USER` passa a `ROLE_TENANT_ADMIN`. A V10 altera os nomes das linhas existentes, preservando IDs e associações `user_roles`. As migrations anteriores não foram editadas. Cadastro de estabelecimentos, cadastro de administradores, setup, bootstrap, matchers e `@PreAuthorize` usam os nomes novos. JWTs continuam sem roles e as permissões são relidas do banco. O frontend não tinha decisões condicionadas aos nomes antigos; recebe os metadados atualizados na validação da sessão.

**Divergência:** não foi reaproveitado `ROLE_ADMIN` para representar tenant, como sugerido na auditoria. Nomes distintos evitam dar significado diferente a uma role antiga. A implantação deve parar as instâncias antigas antes da V10/nova versão; as antigas não reconhecerão as roles renomeadas.

**Arquivos de produção:**

- `src/main/resources/db/migration/V10__clarify_platform_and_tenant_roles.sql` (novo).
- `src/main/java/com/wilgner/cardapio/config/BootstrapAdminRunner.java`.
- `src/main/java/com/wilgner/cardapio/config/SecurityConfig.java`.
- `src/main/java/com/wilgner/cardapio/service/AdminEstabelecimentoServiceImpl.java`.
- `src/main/java/com/wilgner/cardapio/controller/AdminEstabelecimentoController.java`.
- `src/main/java/com/wilgner/cardapio/controller/PlatformAdminController.java`.
- `src/main/java/com/wilgner/cardapio/controller/EstabelecimentoController.java`.

**Testes:** `RoleMigrationTest` migra um banco até V9, prepara usuários com role de plataforma, tenant e ambas, aplica V10 e compara IDs/vínculos. `SecurityIntegrationTests` verifica autorização, rejeição de nomes antigos, roles relidas com o mesmo JWT e conta com duas roles explícitas. `SetupSecurityIntegrationTest` verifica login/refresh da plataforma. Fixtures/assertivas foram atualizadas em `BootstrapAdminRunnerTest`, `BootstrapAdminRunnerIntegrationTest`, `CardapioApplicationTests`, `EstabelecimentoTests`, `SpaCsrfIntegrationTests` e `frontend/src/api/client.test.js`.

### SEC-004 — Separação entre plataforma e painel

**Validação:** administradores de plataforma podiam operar produtos/categorias/uploads no contexto de seu estabelecimento administrativo. Isso não demonstrava acesso arbitrário aos dados de outros tenants, pois os services já filtravam por estabelecimento. A restrição do painel era inconsistente entre controllers.

**Solução:** `/painel/**` exige `ROLE_TENANT_ADMIN`, inclusive produtos, categorias e upload. `/plataforma/**` exige `ROLE_PLATFORM_ADMIN`. Uma conta explicitamente dotada das duas roles mantém os dois acessos. Os filtros por estabelecimento permanecem.

**Arquivos:** `SecurityConfig.java`, `controller/AdminProdutoController.java`, `controller/AdminCategoriaController.java`, `controller/SupabaseStorageController.java`, sob `src/main/java/com/wilgner/cardapio/`.

**Testes:** `SecurityIntegrationTests` verifica bloqueio da plataforma em todas as operações do painel, inclusive upload, bloqueio de tenant na plataforma e GET/PUT/DELETE de produto de outro tenant com verificação de integridade do produto original. Os testes existentes continuam exercitando CRUD e criação de estabelecimentos.

**Dependências:** as anotações de método coincidem com parte do SEC-010; o teste entre tenants coincide com SEC-024. Foram incluídos para validar a alteração de autorização exigida por SEC-004, sem expandir esta etapa para os demais achados médios/baixos. A sugestão de permitir superadministrador no painel, em SEC-002, conflita com SEC-004; foi seguida a exigência explícita de role de tenant.

### SEC-005 — Contexto de autenticação ausente

**Validação:** havia desreferência de `getAuthentication()` sem verificar null. Os dois services consumidores já tratavam retorno null como credenciais inválidas.

**Solução:** `SecurityUtils` retorna null para contexto ausente, autenticação não validada ou principal incompatível. Mantém o contrato dos consumidores e evita aceitar `CustomUserDetails` de uma autenticação ainda não validada.

**Arquivo:** `src/main/java/com/wilgner/cardapio/service/SecurityUtils.java`.

**Testes:** `service/SecurityUtilsTest.java` cobre autenticação válida, contexto ausente, autenticação não validada e principal inesperado. Os testes HTTP de acesso anônimo continuam passando.

### SEC-006 — Logout protegido por CSRF em todo o fluxo

**Validação:** logout era explicitamente ignorado pelo CSRF. Remover apenas essa exceção seria insuficiente: o matcher genérico de Bearer também dispensava CSRF, e o frontend só lia `document.cookie`, que não acessa cookies de outro host.

**Solução:** logout sempre exige CSRF. A exceção Bearer fica restrita às APIs autenticadas do painel/plataforma. `/auth/csrf` mantém o token mascarado com XOR, com `no-store`, indicando o header `X-CSRF-TOKEN`. O handler decodifica essa representação e preserva o mecanismo anterior de valor bruto do cookie em `X-XSRF-TOKEN`, além dos parâmetros XOR. O frontend mantém o token mascarado em memória e busca um atual antes de logout/setup. Metadados da sessão só são apagados após logout aceito pelo servidor. Não foram relaxados mascaramento da resposta, cookies de autenticação ou origens CORS. A revisão final substituiu a primeira implementação que publicava o token bruto no JSON, para preservar também a proteção contra BREACH já existente.

**Arquivos:** `config/SecurityConfig.java`, `controller/AuthenticationController.java`, `security/SpaCsrfTokenRequestHandler.java` sob `src/main/java/com/wilgner/cardapio/`; `frontend/src/api/client.js`.

**Testes:** `SpaCsrfIntegrationTests` cobre token ausente/inválido, Bearer válido/inválido tentando dispensar CSRF, URL codificada, CSRF obtido pelo JSON para outra origem permitida, rejeição de origem não permitida e limpeza dos cookies. Verifica também que o token JSON difere do cookie, muda a máscara entre requests e não funciona no header de representação incorreta. `client.test.js` cobre cookie de outro host, header do token mascarado, atualização do CSRF e preservação de metadados se logout for rejeitado. O fluxo de cookies e o Bearer nas APIs protegidas permanecem cobertos por `CardapioApplicationTests`.

Referência técnica consultada: [Spring Security — CSRF e integração SPA](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

### SEC-008 — Setup exige credencial e criação inicial serializada

**Validação:** o endpoint podia ser habilitado publicamente sem segredo e dispensava CSRF. A consulta sobre existência de administrador, isoladamente, permitia corrida entre dois setups.

**Solução:** permanece desabilitado por padrão, explicitamente também nos profiles `prod` e `supabase`. Quando habilitado, exige `APP_SETUP_ADMIN_TOKEN` de pelo menos 32 caracteres; configuração ausente/curta impede a inicialização. Requests precisam de `X-Setup-Token` e CSRF. A comparação usa `MessageDigest.isEqual`, e os logs não contêm credenciais. Um lock pessimista na linha da role de plataforma serializa a verificação/criação no banco. Depois do primeiro administrador, novas tentativas autorizadas recebem 409.

**Arquivos:** `controller/SetupController.java`, `repository/RoleRepository.java`, `service/AdminEstabelecimentoServiceImpl.java`, `config/SecurityConfig.java`, `exception/ApiExceptionHandler.java`, sob `src/main/java/com/wilgner/cardapio/`; `application.properties`, `application-prod.properties`, `application-supabase.properties` em `src/main/resources/`; `frontend/src/api/client.js`; `.env.example`.

**Divergência complementar:** a ausência do controller poderia cair no handler genérico e produzir 500 para rota inexistente. Foi adicionado tratamento 404 para `NoResourceFoundException`, validando o setup desabilitado.

**Testes:** `SetupSecurityIntegrationTest` usa banco isolado e verifica segredo ausente/incorreto, CSRF obrigatório mesmo com Bearer, criação válida, segunda criação recusada e duas chamadas simultâneas resultando em exatamente um administrador e um estabelecimento. Também verifica login/refresh, roles e cookies Secure/HttpOnly. `DeploymentSecurityConfigurationTest` verifica ativação/configuração; `RequestRateLimitIntegrationTest` verifica setup desabilitado; `client.test.js` verifica envio do segredo somente no header, sem persistência.

### SEC-011 — Configuração Supabase explícita

**Validação:** o profile continha um projeto e usuário de pooler como valores padrão. O identificador público do projeto não é uma credencial e não foi tratado como equivalente a senha vazada.

**Solução:** `DATABASE_URL`, `DATABASE_USERNAME` e `SUPABASE_URL` são obrigatórios, sem fallback para projeto real. Exemplos usam placeholders e os comandos documentados incluem as variáveis necessárias.

**Arquivos:** `src/main/resources/application-supabase.properties`, `.env.example`, `README.md`.

**Testes:** `DeploymentSecurityConfigurationTest` verifica falha de resolução sem cada variável e resolução com valores explícitos. Nenhuma conexão ao Supabase de produção foi realizada.

### SEC-013 — Limites de requisições com memória limitada

**Validação:** não existia rate limiting para login, refresh, setup ou leituras públicas.

**Solução:** filtro antes de CSRF/autenticação aplica janelas de um minuto por endereço remoto e grupo de rotas: login 5, refresh 30, setup 5, GET/HEAD `/public/**` 300. Retorna 429 com `Retry-After`. Orçamento é independente por IP/grupo, incluindo logins bem-sucedidos. Contagem é atômica, URLs codificadas compartilham o orçamento, e `Forwarded`/`X-Forwarded-For` não são interpretados pelo filtro.

O armazenamento tem limite configurável de 10.000 chaves. Não expulsa entradas ativas para admitir um IP novo; ao atingir capacidade, recusa novas chaves até liberar entradas expiradas. A limpeza durante o tráfego evita varrer o mapa a cada requisição rejeitada. Não registra IPs em novos logs. O cliente não converte 429/erros transitórios de refresh em sessão expirada.

**Arquivos:** `security/RequestRateLimiter.java` e `security/RequestRateLimitFilter.java` (novos), `config/SecurityConfig.java`, sob `src/main/java/com/wilgner/cardapio/`; `src/main/resources/application.properties`; `.env.example`; `frontend/src/api/client.js`.

**Testes:** `RequestRateLimiterTest` verifica sexta tentativa, recuperação com relógio simulado, independência de IP/grupo, capacidade/expiração, concorrência e configuração inválida. `RequestRateLimitIntegrationTest` verifica 429 real em cada grupo, troca de username/headers sem bypass, caminho codificado, cabeçalho de retry e manutenção de health/autorização. `client.test.js` verifica refresh limitado sem apagar a sessão.

**Limites operacionais:** a implementação é por instância e reinicia com o processo. Atrás de proxy, `remoteAddr` precisa refletir uma cadeia de confiança configurada pela infraestrutura, ou o orçamento será compartilhado pelo IP do proxy. Um limite global entre réplicas exige gateway ou armazenamento compartilhado. IPs compartilhados também compartilham o orçamento; os limites podem ser ajustados por variáveis documentadas. Não se afirma proteção completa contra ataques distribuídos.

## Arquivos de testes e documentação

Novos testes desta etapa:

- `src/test/java/com/wilgner/cardapio/config/DeploymentSecurityConfigurationTest.java`.
- `src/test/java/com/wilgner/cardapio/config/RoleMigrationTest.java`.
- `src/test/java/com/wilgner/cardapio/security/RequestRateLimiterTest.java`.
- `src/test/java/com/wilgner/cardapio/security/RequestRateLimitIntegrationTest.java`.
- `src/test/java/com/wilgner/cardapio/security/SetupSecurityIntegrationTest.java`.

Testes ajustados: `config/BootstrapAdminRunnerIntegrationTest.java`, `config/BootstrapAdminRunnerTest.java`, `CardapioApplicationTests.java`, `EstabelecimentoTests.java`, `security/SecurityIntegrationTests.java`, `security/SpaCsrfIntegrationTests.java`, `service/SecurityUtilsTest.java`, todos sob `src/test/java/com/wilgner/cardapio/`; `frontend/src/api/client.test.js`.

`src/test/resources/application-test.properties` mantém setup desabilitado nas suítes genéricas e amplia orçamentos apenas nessas suítes que compartilham o IP do MockMvc. Suítes dedicadas habilitam setup com segredo fictício e exercitam limites baixos reais. Nenhum controle foi desabilitado em produção para fazer testes passarem.

`README.md` e `.env.example` documentam o novo contrato, roles, implantação da V10, segredo do setup, CSRF e limites. Este relatório registra validação e divergências; `AUDITORIA_SEGURANCA_LGPD.md` não foi alterado.

## Validação final

| Verificação | Resultado final |
|---|---|
| `mvn -B test` | 71 testes, zero falhas, zero erros e zero ignorados; BUILD SUCCESS |
| `npm test` | 23 testes aprovados em 3 arquivos |
| `npm run build` | Build Vite concluído |
| `git diff --check` | Sem problemas de whitespace |
| Integridade da auditoria | SHA-256 idêntico ao da Fase 1 |

Os testes relacionados a todos os sete achados passaram. A execução Maven removeu apenas do processo filho as três variáveis externas `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`, permitindo o datasource H2 definido no profile de teste. Não foram alteradas variáveis globais da máquina nem utilizadas credenciais de produção.

Evidências locais das execuções finais: `high-severity-final-backend.log`, `frontend/high-severity-final-tests.log` e `frontend/high-severity-final-build.log` (logs ignorados pelo Git). A suíte inicial desta etapa também passou; as repetições decorreram dos cenários adicionais de segurança, do ajuste do tratamento de 429 no frontend e da preservação do mascaramento XOR do CSRF.

Os testes utilizam Java 25 com compilação `release 21` e H2 em modo PostgreSQL, além de Vitest/jsdom. A migration e a concorrência do setup foram exercitadas em H2; não houve validação contra banco PostgreSQL/Supabase real nem implantação em produção. Os testes frontend simulam HTTP e não constituem uma sessão real de navegador entre domínios.

SHA-256 preservado da auditoria: `C91DF3BF4F9AF3D3427C396CA11C0E3F492F600AECAB0E86DCEB9C3BC8F1FBC1`.

Esta etapa para nos achados ALTO. Os demais achados permanecem para autorização posterior, exceto as dependências e testes de regressão explicitados acima.
