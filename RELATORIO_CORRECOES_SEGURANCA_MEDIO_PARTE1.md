# Correções de segurança — MÉDIO, parte 1

Data: 2026-09-29. Escopo implementado: **SEC-007, SEC-012 e SEC-015**, com dependências estritamente necessárias. Documentos considerados em ordem: `STATUS_SEGURANCA_ATUALIZADO.md`, `RELATORIO_CORRECOES_SEGURANCA_ALTO.md`, `README.md`, `AUDITORIA_SEGURANCA_LGPD.md`. A auditoria original foi tratada como histórica, confrontando cada recomendação com o código atual.

## Baseline e ambiente

Antes das alterações de código:

| Comando | Resultado |
|---|---|
| `mvn -B test` | Não executou: `mvn` não estava no PATH. |
| `./mvnw.cmd -B test` | Wrapper retornou `Acesso negado` neste ambiente. |
| Maven 3.9.11 já instalado pelo wrapper, executado pelo caminho completo, `-B test` | **71 testes, 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS**, 25,925 s. |
| `cd frontend; npm test` | **26 testes aprovados em 3 arquivos**. |
| `cd frontend; npm run build` | **Aprovado**, 50 módulos. |

Foi necessário informar o repositório Maven local e executar fora da restrição de acesso do sandbox. A primeira execução direta não podia criar `C:\.m2\repository`; com o caminho correto ainda houve falha de acesso a uma dependência. Não foi necessário modificar dependências ou o `pom.xml`. O comando bem-sucedido foi:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
Remove-Item Env:SPRING_DATASOURCE_URL, Env:SPRING_DATASOURCE_USERNAME, Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
& 'C:\Users\Wilgner\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\Wilgner\.m2\repository' -B test
```

As variáveis foram removidas somente no processo de execução, para os testes usarem seu datasource isolado. Não houve alteração global de ambiente nem acesso ao banco real. Java 25.0.3 compilou com `release 21`, conforme o projeto; não foi executada uma JVM Java 21. H2 foi usado em modo PostgreSQL. Frontend: Vitest 4.1.11/jsdom e Vite 8.2.2 efetivamente instalados.

O arquivo de status já estava não rastreado no início e foi preservado/atualizado. Não havia alterações rastreadas de código no início. Nenhuma migration anterior foi editada, e os documentos históricos da Fase 1 e auditoria foram preservados.

## SEC-007 — Revogação server-side de refresh

### Problema confirmado

`AuthenticationController` validava somente assinatura/tipo/expiração pelo `TokenService` e emitia novos JWTs. Não existia identidade de refresh nem persistência de sessão. Logout limpava cookies, mas não impedia usar uma cópia anterior. A distinção access/refresh, cookies HttpOnly, CSRF XOR e rate limiting já estavam implementados e foram preservados.

### Solução e decisões

- **V11** cria `refresh_session`, com `id` aleatório por login, `usuario_id`, `token_hash` SHA-256 do `jti` atual, `expires_at` e `revoked_at`. FK com `ON DELETE CASCADE` e índices por usuário/expiração. Nenhum JWT bruto é persistido.
- Refresh JWT possui `jti` novo a cada emissão e `sid` estável por sessão; ambos são UUIDs aleatórios. Esses identificadores não são retornados no JSON nem adicionados aos logs.
- O produto não tinha restrição a uma única sessão por usuário. Foram preservados logins independentes, sem campo global no usuário que invalide outros dispositivos.
- `RefreshSessionService.start` cria a sessão no login. `rotate` verifica criptografia/tipo, bloqueia a linha com `PESSIMISTIC_WRITE`, exige sessão não revogada/não expirada e hash atual, relê o usuário e substitui o hash na mesma transação que gera os novos tokens.
- Duas renovações do mesmo token produzem exatamente um sucesso e uma recusa 401. O token anterior não volta a ser aceito. Replay recusado não revoga automaticamente a sessão sucessora; essa decisão evita derrubar o vencedor legítimo de uma corrida, sem tolerar reutilização do token antigo.
- A duração é **absoluta desde o login**, conforme a configuração existente (7 dias por padrão). Renovar não estende indefinidamente a sessão.
- Logout revoga a linha da sessão antes de limpar cookies e continua exigindo CSRF. Um token anterior, ainda assinado e não expirado, também pode revogar o sucessor da mesma sessão; assim a ordem entre logout e rotação não restaura a sessão. Logout sem cookie, com token inválido/expirado ou já revogado é idempotente.
- Usuário inexistente ou estabelecimento inativo não pode renovar. Emissão de nova sessão também exige estabelecimento ativo. Essa checagem é dependência do requisito de conta desativada; `CustomUserDetails`/`SecurityFilter` não foram reescritos e **SEC-018 não foi marcado como concluído**.
- Tokens de tipo trocado são recusados. Erros de revogação, expiração, replay e ausência de usuário recebem a mesma mensagem genérica `Sessão expirada`, sem motivo interno.

**Divergência da auditoria:** não foi usada blocklist local volátil nem um único `jti` no usuário. A primeira perderia a revogação ao reiniciar; o segundo encerraria sessões independentes. A observação antiga de que `SecurityFilter` já bloqueava estabelecimento desativado também não corresponde ao código atual; não foi usada como premissa.

### Arquivos

Produção, sob `src/main/java/com/wilgner/cardapio/`:

- `controller/AuthenticationController.java`: integra criação, rotação e logout com o serviço persistente.
- `security/TokenService.java`: emite/verifica `sid`/`jti`, mantendo a verificação de tipo.
- `security/RefreshSessionService.java`: transações e regras da sessão (novo).
- `model/entity/RefreshSession.java`: estado mínimo persistido (novo).
- `repository/RefreshSessionRepository.java`: leitura com lock (novo).
- Migration nova: `src/main/resources/db/migration/V11__create_refresh_sessions.sql`.

### Testes

`security/RefreshSessionIntegrationTest.java` (7 testes novos):

1. Login, refresh válido, cookies Secure/HttpOnly/SameSite, ausência de JWT no JSON, troca de jti, manutenção de sid/expiração e rejeição de replay.
2. Logout exige CSRF, limpa cookies, revoga o token e preserva um segundo login independente.
3. Logout com cookie anterior à rotação revoga o sucessor.
4. Expiração do JWT e expiração independente no banco resultam em 401.
5. Refresh como access, access como refresh e refresh legado sem sessão são recusados.
6. Estabelecimento inativo e usuário removido não renovam; login inativo também é recusado.
7. Duas tentativas concorrentes do mesmo refresh têm exatamente um vencedor.

`security/RefreshSessionRestartTest.java` (2 testes novos ordenados): login em duas sessões, revogação de uma, fechamento integral do contexto Spring/datasource, recriação do contexto e reabertura do **H2 em arquivo**. A revogada continua 401, enquanto a outra continua renovando. Não é somente recriação do objeto de serviço ou um mock de repository.

### Limitações operacionais

- Implantação exige retirar instâncias antigas que aceitam refresh sem estado. Refresh tokens anteriores à V11 não têm sessão persistida e exigirão novo login. Access tokens existentes continuam funcionando até expirar.
- Revogação de **access tokens** não faz parte desta etapa; um access token copiado pode durar até sua expiração, mesmo após logout.
- A persistência é a do banco configurado: produção/PostgreSQL é durável; o profile dev com H2 em memória é descartável e perde também sessões válidas no restart, sem ressuscitar tokens antigos.
- Não existe rotina automática de limpeza. Linhas expiradas podem ser removidas por manutenção usando o índice `expires_at`; remoção resulta em recusa, nunca reativação. É necessário definir a frequência operacional de limpeza.
- Rotação é estrita, sem janela de graça. Perda da resposta depois do commit pode exigir novo login. Páginas diferentes que compartilham cookies podem disputar a mesma renovação; o cliente deduplica chamadas dentro da página, não entre abas.
- Testes de SQL/lock foram executados em H2, não em PostgreSQL/Supabase real. Não houve implantação nesta execução.

## SEC-012 — Credencial insegura na seed

### Problema confirmado

V4 insere cinco usuários demonstrativos com o mesmo hash BCrypt, além de estabelecimentos e produtos. O usuário participa de relações e não tem flag individual de desativação. Excluir por username poderia apagar uma conta legítima ou afetar produtos. Mudar V4 quebraria a validação Flyway de bancos existentes.

### Solução e decisões

**V12** atualiza somente `password` para um marcador deliberadamente incompatível com BCrypt, quando **todos** os atributos coincidem:

1. Hash original exato da seed.
2. Username em `restaurante`, `pizzaria`, `hamburgueria`, `espetaria`, `bar`.
3. Email demonstrativo correspondente (`dono@<username>.test`).
4. Estabelecimento vinculado cujo slug corresponde ao username da seed.

IDs, emails, roles, estabelecimentos e produtos são preservados. A senha antiga não autentica mais e não existe senha que autentique o marcador. Senhas já trocadas ou identidades/vínculos diferentes são preservados. O tratamento é o mesmo para bancos novos e já migrados: V4 permanece histórica, V12 faz o saneamento antes de a aplicação servir requisições.

Não foi necessário adicionar outro seed nem separar migrations antigas por profile. O dado demonstrativo público permanece, mas a credencial compartilhada fica inutilizada em **todos os ambientes**, incluindo dev. Desenvolvimento pode criar contas novas pelos fluxos administrativos já existentes. O README não revela nem sugere descobrir a senha original. Produção/Supabase deve usar contas administrativas reais.

**Divergência da auditoria:** `WHERE NOT EXISTS` na V4 não restringe a seed a desenvolvimento. Não foi seguida a recomendação de documentar a senha compartilhada ou remover contas apenas pelo username. O saneamento evita apagar relações e não demanda recuperação cega de um segredo histórico.

### Arquivos e testes

- Novo `src/main/resources/db/migration/V12__disable_insecure_seed_credentials.sql`.
- Novo `src/test/java/com/wilgner/cardapio/config/SeedCredentialMigrationTest.java` (2 testes): aplica histórico até V10 e novas migrations, confirma as cinco credenciais inutilizadas, preservação integral de identidades/produtos/roles e preservação individual de senha, email, username e vínculo modificados. Verifica que o encoder não aceita o marcador. As suítes Spring também inicializam bancos vazios diretamente até V12.
- README atualizado com procedimento e efeitos operacionais; V1–V10 não foram editadas.

### Limitações

Não houve consulta ou alteração de produção. O saneamento ocorrerá quando a nova versão for implantada via Flyway. Uma conta demonstrativa adotada sem alterar nenhum atributo será preservada, mas precisará receber senha individual por procedimento administrativo controlado. Não foi criado fluxo de recuperação de senha. Contas diferentes da assinatura exata são intencionalmente preservadas; não se afirma ter auditado todas as credenciais reais.

## SEC-015 — Metadados no localStorage

### Problema confirmado

`client.js` gravava userId, username, email, slug e roles em `cardapio.authUser` e recusava chamar `/auth/validate` quando essa chave não existia. `AuthProvider` já era o estado React da sessão, mas convertia qualquer falha no boot em usuário anônimo.

### Solução e decisões

- Metadados mínimos mantidos apenas em memória no cliente e no `AuthProvider`, sem email. `getStoredUser`/`hasStoredUser` mantêm nomes/compatibilidade, mas consultam somente memória.
- A chave legada é removida de localStorage e sessionStorage no carregamento do módulo e na limpeza da sessão, sem ler/confiar/migrar seu conteúdo. Storage indisponível não impede a autenticação por cookie.
- Boot/reload consulta `/auth/validate` mesmo sem metadados. Access expirado usa o fluxo de refresh e repete a validação. `validationPromise` evita duplicar o boot no StrictMode; a deduplicação existente do refresh permanece.
- Nenhum JWT ou metadado de sessão é gravado em storage. Mesmo se uma resposta antiga incluir JWT, a seleção explícita de campos não os armazena.
- Logout aceito pelo servidor limpa memória e chave legada; logout recusado por CSRF mantém a sessão, preservando o contrato da Fase 1.
- 401/404 do refresh continuam encerrando a sessão. 429, erros de rede e outras falhas temporárias não apagam uma sessão conhecida. Um boot temporariamente indisponível mostra ação de tentar novamente no painel, sem presumir logout. 401 após a tentativa de renovação segue a expiração definitiva.

**Divergência da auditoria:** não foi feita migração de dados pessoais para sessionStorage. A arquitetura atual permite reconstrução por cookies HttpOnly e memória, sem necessidade de persistência alternativa.

### Arquivos e testes

- `frontend/src/api/client.js`: memória, remoção do legado, boot sem storage e classificação de erros.
- `frontend/src/auth/AuthContext.jsx`: metadados sem email e distinção de indisponibilidade temporária.
- `frontend/src/App.jsx`: mensagem/ação para repetir validação temporariamente indisponível no painel.
- `frontend/src/api/client.test.js`: testes existentes preservados e fixtures migradas para login real simulado, em vez de forjar estado no localStorage; novos testes para reconstrução após reload, limpeza legada, ausência de escrita de JWT/email e renovação no reload. Continua cobrindo expiração, 429, CSRF e logout recusado.
- Novo `frontend/src/auth/AuthContext.test.jsx`: boot, logout, falha temporária com retry, preservação de usuário autenticado e expiração definitiva.
- Nenhuma migration para SEC-015; `/auth/validate` mantém seu contrato de cookies e metadados.

### Limitações

Testes usam React Testing Library/Vitest/jsdom e HTTP simulado. Não foi executada sessão manual de navegador com API em outro domínio. Uma página antiga já aberta pode manter o código legado até recarregar; a limpeza ocorre ao carregar a versão nova. Preferências não relacionadas à autenticação não são removidas.

## Validação final

| Verificação | Resultado |
|---|---|
| Maven 3.9.11, mesmo comando do baseline, `-B test` | **82 testes; 0 falhas; 0 erros; 0 ignorados. BUILD SUCCESS.** 27,642 s. |
| `cd frontend; npm test` | **33 testes aprovados, 4 arquivos aprovados.** 1,75 s. |
| `cd frontend; npm run build` | **Aprovado.** 50 módulos, 93 ms. |
| `git diff --check` | **Aprovado**, sem erros de whitespace. |
| Migrations V1–V10 e auditoria histórica | Sem alterações no diff. |

Saída de encerramento Maven:

```text
[INFO] Tests run: 82, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time: 27.642 s
```

Saída de testes frontend:

```text
Test Files  4 passed (4)
     Tests  33 passed (33)
  Duration  1.75s
```

Saída do build:

```text
vite v8.2.2 building client environment for production...
✓ 50 modules transformed.
dist/index.html                   0.46 kB │ gzip:  0.30 kB
dist/assets/index-CeGAYDAV.css   31.42 kB │ gzip:  6.82 kB
dist/assets/index-BVT1uNZJ.js   176.10 kB │ gzip: 56.52 kB
✓ built in 93ms
```

Logs integrais locais (ignorados pelo Git): `baseline-backend.log`, `baseline-frontend-test.log`, `baseline-frontend-build.log`, `medium-backend.log`, `medium-frontend-test.log`, `medium-frontend-build.log`. Durante o desenvolvimento, a primeira compilação dos testes novos apontou colisão entre o helper local `cookie` e o matcher MockMvc de mesmo nome; o helper foi renomeado para `responseCookie`, e a suíte completa passou. Não foram removidas assertivas nem enfraquecidos testes existentes para obter aprovação.

Os 71 testes anteriores do backend continuam integrando os 82 finais, cobrindo roles, isolamento multi-tenant, CSRF mascarado, setup protegido e rate limiting. `SecurityConfig`, `TokenCookieService`, `SecurityFilter`, o handler CSRF e o rate limiter da Fase 1 não foram alterados. As únicas migrations novas são V11/V12. `STATUS_SEGURANCA_ATUALIZADO.md` marca somente SEC-007/012/015 como concluídos nesta etapa; os demais achados permanecem pendentes.
