# Correções de segurança — MÉDIO, parte 2

Data: 2026-09-29. Escopo: SEC-014, SEC-016, SEC-017, SEC-023 e SEC-026. Entrada preservada: Fase 1 e Parte 1, incluindo cookies HttpOnly, CSRF XOR/logout, roles separadas, setup protegido, rate limiting, sessões persistentes/rotação, saneamento da seed e metadados de autenticação em memória.

Os cinco documentos solicitados foram considerados e confrontados com código/configuração. As alterações da Parte 1 já estavam presentes no working tree, sem commit, e foram preservadas. Nenhuma implantação ou alteração em Supabase real foi feita.

## Baseline

| Verificação | Resultado antes das alterações |
|---|---|
| Maven `-B test` | 82 testes; 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS; 26,415 s. |
| `npm test` | 33 testes em 4 arquivos; aprovado; 2,07 s. |
| `npm run build` | Aprovado; 50 módulos; 281 ms. |

Como estabelecido na Parte 1, `mvn` não está no PATH e o wrapper não executa corretamente no sandbox. Foi usado diretamente o Maven 3.9.11 já instalado pelo wrapper, sem instalar outra versão ou modificar o pom. Comando utilizado no baseline e na validação final:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
Remove-Item Env:SPRING_DATASOURCE_URL, Env:SPRING_DATASOURCE_USERNAME, Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
& 'C:\Users\Wilgner\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11\bin\mvn.cmd' '-Dmaven.repo.local=C:\Users\Wilgner\.m2\repository' -B test
```

As variáveis foram removidas apenas no processo filho para usar os bancos de teste. Execução autorizada fora da restrição de acesso ao cache Maven. JVM Java 25.0.3, compilação `release 21`, H2 em modo PostgreSQL; não foi testada JVM Java 21 ou PostgreSQL real. Frontend usou Vitest 4.1.11 e Vite 8.2.2 instalados no projeto.

## SEC-014 — Redirects do WebClient

**Problema validado:** há um único consumidor do builder, `SupabaseStorageService`. Origem e bucket vêm de configuração administrativa. Download recebe somente o nome do arquivo, não uma URL arbitrária; upload antes incorporava uma versão sanitizada do nome original. Logo e imagem de produto são URLs armazenadas/exibidas, não destinos de fetch arbitrário pelo servidor. Portanto `followRedirect(true)` não provava sozinho SSRF diretamente explorável por URL fornecida pelo usuário. Mesmo assim permitia seguir um Location inesperado do storage, inclusive para outro host.

**Solução:** `followRedirect(false)`; upload, download e compensação DELETE recusam qualquer status não 2xx, incluindo 3xx. Não é necessário redirect para os endpoints de objetos do Supabase usados pelo projeto. Endpoint de configuração exige esquema HTTP/HTTPS, host, ausência de userinfo/query/fragment e caminho raiz; bucket tem conjunto de caracteres restrito. Download rejeita separadores e segmentos `.`/`..`, construindo o nome como segmento codificado. Upload passa a usar UUID puro, sem nome original, e POST de criação de objeto novo.

Timeout de conexão: 10 s; resposta: 30 s; operação reativa: 45 s, em vez dos 2 minutos anteriores. Arquivos individuais permanecem limitados a 5 MiB por padrão. Não foi realizado benchmark no Supabase real; redes lentas podem resultar em reserva UNCERTAIN e requerer conciliação. Pool mantém seus limites anteriores.

**Divergências:** nenhuma allowlist de URLs fornecidas por usuário foi necessária porque não existe esse input no fluxo. A origem exata é a configuração administrativa; localhost continua possível para um Supabase local deliberadamente configurado. Não há navegação involuntária para loopback, redes privadas ou metadata por redirects. Não se afirma proteção contra uma configuração administrativa maliciosa ou comprometimento do DNS/infraestrutura. Egress de produção deve ser limitado ao storage configurado. POST para novas chaves foi alinhado à [API oficial Supabase](https://supabase.com/docs/reference/self-hosting-storage), que distingue criação POST de atualização PUT.

**Arquivos:** `config/WebClientConfig.java`, `service/SupabaseStorageService.java`, sob `src/main/java/com/wilgner/cardapio/`.

**Testes:** `service/StorageRedirectTest.java` usa dois servidores HTTP locais e resposta 307. Upload, download e delete falham sem um único acesso ao destino loopback indicado por Location. Também verifica rejeição de URL metadata como filename e de configuração com esquema não HTTP, credenciais ou query. Nenhuma migration própria.

## SEC-016 — CSP no ponto que entrega HTML

**Problema validado:** Dockerfile empacota somente Java; React/Vite está separado e não é copiado para recursos estáticos Spring. Existe `frontend/serve-preview.mjs`, mas nenhum host externo de produção foi identificado/configurado no repositório. Uma CSP no JSON da API seria insuficiente.

**Solução:** plugin Vite de produção gera meta CSP antes dos recursos em `dist/index.html`, além de `dist/csp-header.txt` e `dist/_headers`. O servidor estático lê a política compilada com o bundle e envia o header, incluindo nas rotas de fallback da SPA. O documento de operação fornece a política exata para CDN/reverse proxy e esclarece suporte variável de `_headers`.

Política: default none; scripts self; estilos self e unsafe-inline; imagens self/blob/data/HTTPS e origem explícita da API; fontes self; conexões self e origem da API; object none; base none; frame-ancestors none por header; form-action self. `VITE_API_BASE_URL` define a mesma origem no cliente e na política. Não há unsafe-inline para scripts, unsafe-eval ou scripts wildcard.

**Recursos reais:** bundle/CSS locais, ícones React, fontes locais/sistema; amostras de temas usam estilos inline e justificam somente a exceção de estilos. Previews de upload usam blob. URLs externas de imagens já são aceitas pelo produto, por isso img-src permite HTTPS sem restringir a um CDN fictício; connect-src continua restrito. Vite dev/HMR não recebe essa política de produção.

**Divergência:** `frame-ancestors` não funciona em meta, conforme [MDN](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/frame-ancestors). A meta protege carregamento/execução dos recursos, mas o host precisa enviar o header para a proteção completa de framing. Não foi adicionada uma CSP meramente à API nem alegado que isso protegeria o React.

**Arquivos:** novos `frontend/csp.mjs` e `frontend/csp.test.js`; alterações em `frontend/vite.config.js` e `frontend/serve-preview.mjs`; documentação em README e `OPERACAO_STORAGE_E_LOGS.md`.

**Testes:** política com API em outro host, ausência de permissões inseguras de scripts, geração de meta/headers e HTTP real do servidor estático para HTML e fallback. Checagem adicional serviu o build real e confirmou igualdade do header e meta anterior ao script. Não foi usado navegador real para verificar enforcement visual, nem consultado o host externo.

**Estado:** implementação do repositório concluída e testada; validação/configuração no host de produção continua pendente. SEC-016 permanece na lista de pendências operacionais do status.

## SEC-017 — Cota persistente por tenant

### Mapeamento do ciclo de imagens

| Operação existente | Comportamento observado e mantido |
|---|---|
| Logo | Mesmo endpoint de upload; URL associada depois em atualização do estabelecimento. |
| Imagem de produto | Upload separado da gravação de produto. |
| Substituição | Novo upload, depois troca de URL; objeto anterior permanece no Supabase. |
| Exclusão de produto | Remove registro do produto, não objeto remoto. |
| Remoção/substituição de logo | Altera URL, sem delete remoto. |
| Edição cancelada/falha após upload | Pode deixar objeto sem referência. |
| Path | Antes UUID + nome sanitizado; agora UUID puro, sem dados pessoais do filename. |

**Solução:** V13 cria `storage_object`, com vínculo ao tenant, chave única, URL, tamanho, estado e timestamps. Adiciona `storage_inventory_required` ao estabelecimento, true para todos os preexistentes e false por padrão para cadastros novos. Faz backfill deduplicado das referências Supabase de produtos/logos como LEGACY, sem inventar tamanhos. A flag global por tenant cobre também órfãos antigos invisíveis ao banco.

`StorageQuotaService.reserve`, em transação independente, bloqueia a linha do estabelecimento, exige inventário conciliado e ausência de LEGACY, soma todos os bytes não DELETED e cria reserva. A cota é **100 MiB** por padrão via `STORAGE_TENANT_QUOTA_BYTES`. Dois uploads concorrentes do mesmo tenant são serializados na reserva; outras instâncias usam o mesmo lock no banco. Tenant vem de `SecurityUtils` no controller, nunca de parâmetro livre. Exceder cota ou exigir conciliação retorna **409**, com mensagem sanitizada.

`TenantStorageService` valida antes de reservar, confirma o upload remoto e marca STORED. A antiga entrada de upload sem quota foi removida do serviço; o controller passa pela orquestração persistente. O upload é síncrono na camada Spring MVC, com chamadas remotas limitadas por timeout; nenhuma transação/lock permanece aberta durante a chamada Supabase.

Tratamento de falhas:

- 4xx explícito ao criar: reserva marcada DELETED, sem excluir uma chave remota preexistente por engano.
- Upload confirmado e banco falha: tenta delete remoto; libera apenas se o delete for confirmado e a atualização do ledger funcionar.
- Timeout/perda de conexão/3xx/5xx: tenta limpeza, mas mantém UNCERTAIN mesmo com delete imediato confirmado, porque o upload original pode terminar depois.
- Limpeza falha: UNCERTAIN permanece cobrado. Se banco também falhar, RESERVED permanece cobrado.
- Restart: estado vem do banco; não depende de contador em memória.

Órfãos continuam consumindo a cota. Excluir/trocar referências não libera bytes inexistentes no banco de objetos. Exclusão do estabelecimento mantém o ledger com tenant nulo para investigação/limpeza. Não há limpeza automática nem endpoint público para liberar cota. O procedimento operacional inclui consulta de candidatos sem referências em **qualquer** tenant e exige confirmar estabilidade/exclusão antes de liberar bytes. Isso evita apagar imagem compartilhada ou reserva ainda em uso.

**Implantação:** tenants anteriores à V13 ficam impedidos de enviar até conciliar o bucket completo e desligar sua flag individual. Não foi realizado esse inventário nem alterado nenhum banco real. Instâncias antigas devem ser retiradas antes de retomar uploads, pois não conhecem quota. Uploads por dashboard/chave administrativa fora desta aplicação precisam integrar a conciliação; o código não intercepta chamadas diretas ao Supabase.

**Arquivos:** nova migration `src/main/resources/db/migration/V13__track_tenant_storage.sql`; novos `service/StorageQuotaService.java`, `service/TenantStorageService.java`, `exception/StorageQuotaException.java` e `exception/StorageWriteRejectedException.java`; alterações em `SupabaseStorageService`, `SupabaseStorageController`, `ApiExceptionHandler`, `application.properties` e `.env.example`. Classes Java sob `src/main/java/com/wilgner/cardapio/`.

**Testes:** `StorageQuotaIntegrationTest` (10 testes) cobre limite, isolamento, tenant forjado no request, 409, concorrência, recusa explícita, timeout com limpeza bem/malsucedida, bloqueio por inventário/LEGACY, retenção de órfão após exclusão do tenant e recriação completa do contexto usando H2 em arquivo. `TenantStorageFailureTest` verifica falha de banco após sucesso remoto e ordem da compensação. `StorageMigrationTest` migra V12 para V13, preserva produtos, deduplica referência compartilhada entre logo/produtos e exige inventário para os cinco tenants anteriores.

**Limitações:** SQL/concorrência testados em H2, não PostgreSQL real. Supabase foi simulado. O ledger impede novos envios sem limite pelo aplicativo; capacidade pode ficar conservadoramente retida após falhas até conciliação. Uma limpeza automática segura exige trabalho posterior e não é alegada como pronta.

## SEC-026 — Validação antes do upload no frontend

**Problema validado:** os dois modais usavam apenas `accept`, sem verificar tamanho/tipo antes de chamar `onSubmit`; o cliente enviava qualquer FormData.

**Solução:** novo `uploadValidation.js` centraliza 5 MiB, MIME JPEG/PNG/WEBP/GIF e extensões correspondentes. Ambos os modais verificam antes de `onSubmit`; `api.uploadImage` repete a verificação antes de montar/enviar FormData. Mensagens são apresentadas na área de erro existente. `VITE_MAX_IMAGE_BYTES` permite alinhar o build a outro limite de backend; deve ser inteiro positivo.

O backend mantém validações de MIME e magic bytes, acrescenta guarda de tamanho no serviço e continua com limite multipart. Limite da requisição foi ajustado para 6 MiB para acomodar envelope de arquivo de 5 MiB; não aumentou o limite individual. Corrigido o marcador GIF89a, antes escrito como GIF88a; JPEG/PNG/WEBP/GIF permanecem cobertos por testes. MIME/extensão no frontend são somente UX, não controle de segurança suficiente.

**Arquivos:** `frontend/src/api/uploadValidation.js`, `frontend/src/api/client.js`, `frontend/src/components/ProductEditorModal.jsx`, `EstablishmentEditorModal.jsx`, novo `UploadValidation.test.jsx`; alterações de validação em `SupabaseStorageService` e `SupabaseStorageServiceTest`.

**Testes:** arquivo acima do limite em cada editor não chama onSubmit nem HTTP; chamada direta ao cliente também não envia; MIME/extensão incompatível é recusado; limite exato e formatos suportados são aceitos. Backend rejeita tamanho excessivo e bytes forjados antes da rede. O teste anterior de tipo inválido foi mantido, apontando para a função de validação extraída.

## SEC-023 — Minimização de logs e retenção

**Problema validado:** não havia configuração habilitando access logs Tomcat. A auditoria generalizava incorretamente esse padrão. Logs próprios tinham username bruto no filtro/negação, URI completa em eventos CSRF/autenticação e throwable integral no handler inesperado. O último podia carregar mensagens/cadeias SQL/HTTP com dados pessoais ou credenciais. Rate limiter não escrevia logs de IP. Setup já registrava apenas mensagens genéricas. Não havia configuração versionada de coletor/rotação/retenção.

**Solução:** `LogSanitizer.route` retorna somente categorias estáticas e `method` aceita valores conhecidos. Eventos de autenticação/negação usam ID interno, sem username/email. CSRF registra somente origem da representação, método e categoria. Erro inesperado registra eventId aleatório, tipo e localização no código, sem mensagem/cause/stacktrace completo. Erros remotos do storage são reconstruídos com texto fixo, sem corpo do provedor ou causa crua. Upload não incorpora nome original à URL.

Access log Tomcat e log-request-details explicitamente false. Perfis prod/supabase limitam logs internos HTTP/Security, desativam transporte HTTP do cliente, SQL/bind e helper de exceções SQL. Logs operacionais próprios de acesso negado, setup e erros sanitizados são preservados. Não foi adicionado registro de IP nem implementado o SEC-019 de auditoria de operações de negócio.

**Retenção:** política operacional documentada de **30 dias**, acesso restrito e exclusão configurada no coletor, incluindo exports/backups. A aplicação continua emitindo stdout; sem acesso à hospedagem não pode garantir eliminação. Documentação distingue logs próprios, proxy/CDN/WAF e configurações externas que podem sobrescrever Spring. Contém texto informativo de privacidade sobre logs técnicos e cookies necessários, sem alegar consentimento, introduzir banner ou analytics.

**Arquivos:** `util/LogSanitizer.java`, `security/SecurityFilter.java`, `security/RestAccessDeniedHandler.java`, `security/SpaCsrfTokenRequestHandler.java`, `exception/ApiExceptionHandler.java`; `application.properties`, `application-prod.properties`, `application-supabase.properties`; `OPERACAO_STORAGE_E_LOGS.md` e README.

**Testes:** `LogPrivacyTest` captura eventos reais via ListAppender. Exceção contendo senha/cookie e URI com email/token não aparecem na mensagem; não existe throwable anexado. Negação usa ID e categoria da rota, sem username/mensagem sensível. Metodologia não equivale a inspecionar logs reais de CDN, banco ou collector.

**Estado:** minimização no código concluída; retenção/configuração efetiva na hospedagem ainda precisa ser aplicada/verificada. SEC-023 permanece pendente operacionalmente no status. Não foi afirmada conformidade jurídica completa por mudança de código.

## Validação final e evidências

| Verificação | Resultado |
|---|---|
| Maven `-B test`, comando acima | **100 testes; 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS; 33,894 s.** |
| `cd frontend; npm test` | **40 testes aprovados em 6 arquivos; 2,00 s.** |
| `cd frontend; npm run build` | **Aprovado; 51 módulos; 110 ms.** |
| HTTP real servindo build de produção | **Aprovado:** CSP do header igual à política compilada; meta antes do script; scripts limitados a self. |
| `git diff --check` | **Aprovado**, com verificação adicional dos arquivos novos. |

Encerramento Maven:

```text
[INFO] Tests run: 100, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time: 33.894 s
```

Frontend:

```text
Test Files  6 passed (6)
     Tests  40 passed (40)
  Duration  2.00s

vite v8.2.2 building client environment for production...
✓ 51 modules transformed.
dist/csp-header.txt               0.26 kB │ gzip:  0.15 kB
dist/_headers                     0.29 kB
dist/index.html                   0.85 kB │ gzip:  0.44 kB
dist/assets/index-CeGAYDAV.css   31.42 kB │ gzip:  6.82 kB
dist/assets/index-CiifjroR.js   176.54 kB │ gzip: 56.73 kB
✓ built in 110ms
```

Logs integrais locais, ignorados pelo Git: `part2-baseline-backend.log`, `part2-baseline-frontend.log`, `part2-baseline-build.log`; `part2-backend.log`, `part2-frontend.log`, `part2-build.log`, `part2-csp-smoke.log`. Houve rodadas intermediárias aprovadas com 94/99 testes backend e 37/40 frontend, seguidas pelos casos adicionais de privacidade, migration, imagens e timeout conservador. Nenhum teste existente foi removido ou enfraquecido.

Todos os 82 testes backend anteriores permanecem na suíte de 100; os 33 frontend anteriores permanecem nos 40 finais. Migrations V1–V12 e relatórios históricos foram preservados. Mudanças no filtro/handler CSRF nesta parte são de log; autenticação, XOR, logout, roles, rate limiting e isolamento continuam cobertos pelas suítes anteriores. O diff total do working tree inclui a Parte 1 ainda não commitada; este relatório descreve somente a Parte 2.

O status contabiliza SEC-014/017/026 como concluídos no repositório. SEC-016/023 registram implementação local e mantêm as pendências externas explícitas. Não houve commit, push, deploy, limpeza de bucket ou configuração de hospedagem nesta execução.
