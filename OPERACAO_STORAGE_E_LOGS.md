# Operação de storage, CSP e logs

## Implantação da V13 e inventário de storage

**Antes de liberar uploads em uma instalação existente, concilie o bucket.** A V13 adiciona `storage_object` e marca todos os estabelecimentos preexistentes com `storage_inventory_required=true`. Isso evita presumir consumo zero para arquivos antigos, principalmente os sem vínculo com produtos/logos. Os cadastros novos começam com a flag false. Mesmo um tenant sem imagens visíveis pode ter órfãos e precisa da verificação inicial. Migrations antigas não foram alteradas.

Cota padrão por tenant: **100 MiB (104857600 bytes)**, configurável por `STORAGE_TENANT_QUOTA_BYTES`. O arquivo individual continua limitado a **5 MiB** por `MAX_FILE_SIZE`; a requisição multipart admite 6 MiB por `MAX_REQUEST_SIZE` para acomodar o envelope. O frontend usa o mesmo padrão, configurável no build por `VITE_MAX_IMAGE_BYTES=5242880`; mantenha os dois limites de arquivo alinhados.

O servidor obtém o tenant da autenticação. Não aceita um tenant do formulário como autoridade. Uma transação bloqueia a linha do estabelecimento, verifica consumo e grava a reserva antes da chamada remota. Cada objeto novo recebe um UUID sem nome original; a URL e o vínculo ao tenant ficam no ledger. Uploads usam POST para criar um objeto novo, conforme a [API oficial de Storage](https://supabase.com/docs/reference/self-hosting-storage).

Estados:

| Estado | Consumo | Significado |
|---|---|---|
| RESERVED | Conta o tamanho reservado | Reserva persistida; pode indicar processo interrompido ou indisponibilidade do banco. |
| STORED | Conta o tamanho | Upload confirmado, inclusive se ainda não vinculado a produto/logo. |
| UNCERTAIN | Conta o tamanho | Resultado remoto incerto; exige conciliação, não liberar só por idade. |
| LEGACY | Bloqueia novos uploads | Referência preexistente sem tamanho confiável. |
| DELETED | Não conta | Exclusão confirmada após upload concluído ou criação explicitamente recusada com 4xx. |

Não há transação distribuída entre banco e Supabase. Uma recusa HTTP 4xx libera a reserva, sem apagar objeto remoto. Um timeout, 3xx, 5xx ou perda de conexão mantém a cobrança como incerta mesmo que a tentativa imediata de limpeza responda sucesso: a escrita original ainda pode terminar depois. Se o upload terminou e apenas a atualização no banco falhou, a exclusão remota confirmada permite liberar a reserva. Se o banco continua indisponível, o registro RESERVED permanece cobrado e rastreável.

### Procedimento de conciliação

1. Pare instâncias antigas que não usam a cota. Suspenda uploads durante a conciliação e aguarde a estabilização de operações remotas em andamento; não presuma que timeout significa ausência de arquivo.
2. Liste todos os objetos do bucket pelo painel/API administrativa Supabase, incluindo arquivos sem referência. Relacione cada chave e tamanho real a um tenant por URLs/referências existentes de produtos, logos e capas (`hero_image_url`), além de registros operacionais. Não atribua arquivos desconhecidos arbitrariamente.
3. Complete cada registro LEGACY com chave real, tamanho e estado STORED, deduplicando por objeto/tenant. Inclua objetos antigos ainda ausentes do ledger. Objetos já removidos devem ser marcados DELETED apenas após confirmação. Objetos cuja propriedade não pode ser determinada exigem decisão operacional antes de liberar os tenants afetados.
4. Para RESERVED/UNCERTAIN, verifique se houve conclusão tardia; mantenha STORED com tamanho real se existir. Para remoção, use a API de storage e confirme a ausência após as operações pendentes terminarem. Nunca libere espaço apenas por alterar o banco.
5. Confirme que o total contabiliza todos os arquivos do tenant. Se ultrapassar a cota, remova objetos sem uso com segurança ou ajuste a configuração conforme decisão do operador.
6. Só então atualize `storage_inventory_required=false` para os IDs conciliados, em transação administrativa. Não execute uma liberação global sem inventário.

O arquivo não fornece credenciais nem executa alterações de produção. O inventário de arquivos preexistentes continua sendo uma operação administrativa; a exclusão de imagens substituídas usa a fila persistente descrita abaixo.

### Substituição, exclusão e órfãos

Produtos e capas armazenam URLs. Cada envio cria um objeto novo e contabilizado para o estabelecimento autenticado. Ao substituir a imagem de um prato ou a capa, remover a capa ou excluir um produto, o backend grava a referência nova e uma entrada em `storage_deletion` na mesma transação. Só depois do commit tenta excluir o arquivo anterior pela API do Supabase. Uma edição sem mudança de imagem não apaga o arquivo.

A V16 cria essa fila. A exclusão aceita apenas objetos do projeto/bucket configurados e pertencentes ao estabelecimento; imagens externas não são apagadas. A verificação de referências cobre produtos, capas e logos de todos os estabelecimentos. Se outro item ainda usa o arquivo, a fila aguarda a última referência desaparecer. Uma imagem gerenciada nova só pode ser vinculada se o ledger comprovar que ela pertence ao tenant e está STORED.

Se o Supabase estiver indisponível, a imagem nova permanece salva e a exclusão fica pendente. O processo agendado retoma a fila após reinicializações e tenta novamente, por padrão a cada 60 segundos (`STORAGE_CLEANUP_INTERVAL_MS`). O consumo antigo só é liberado quando a API confirma a exclusão ou informa que o arquivo já não existe (404). Os registros ficam DELETED no ledger para rastreabilidade. Falhas no banco preservam a fila para nova tentativa; uma transação de edição revertida não dispara exclusão.

Quando o upload termina, mas a gravação do prato/capa falha, o frontend solicita `DELETE /painel/storage/unused-image` com a URL do arquivo novo. O endpoint exige autenticação e CSRF e agenda apenas um objeto do próprio estabelecimento. A mesma verificação de referências impede apagar uma imagem que chegou a ser vinculada, inclusive quando a resposta do salvamento foi perdida. Imagens abandonadas antes dessa solicitação continuam contabilizadas e podem exigir inventário administrativo.

Ao excluir um estabelecimento, o ledger permanece, com `estabelecimento_id` nulo, para permitir inventário e limpeza. Não se deve apagar esses registros antes de resolver os objetos remotos.

Consulta para candidatos a órfãos, sem autorizar exclusão automática:

```sql
SELECT s.id, s.estabelecimento_id, s.object_key, s.public_url, s.size_bytes, s.status, s.created_at
FROM storage_object s
WHERE s.status <> 'DELETED'
  AND NOT EXISTS (SELECT 1 FROM produtos p WHERE p.imagem_url = s.public_url)
  AND NOT EXISTS (SELECT 1 FROM estabelecimento e WHERE e.logo_url = s.public_url OR e.hero_image_url = s.public_url);
```

A consulta verifica referências de **todos** os tenants, pois uma URL pode ser compartilhada. Uma reserva recente pode estar sendo anexada por uma edição em andamento. Para a limpeza administrativa de arquivos preexistentes, suspenda edições/uploads, verifique referências novamente e confirme exclusão remota antes de marcar DELETED. A limpeza automática usa o mesmo bloqueio do tenant das alterações de imagens. Não há endpoint público para alterar ledger ou consumo.

## CSP do documento React

O Dockerfile empacota somente o backend. Não há integração de `frontend/dist` nos recursos Spring; a aplicação React é hospedada separadamente. A CSP de respostas JSON da API não resolveria a proteção do documento.

`npm run build` gera uma meta CSP antes dos recursos no HTML, `dist/csp-header.txt` e `dist/_headers`. A política usa a origem de `VITE_API_BASE_URL` do mesmo build. `npm run serve:static` lê esse arquivo e entrega o header nas respostas, incluindo fallback da SPA. O arquivo `_headers` funciona somente em hosts que suportam esse formato; nos demais, configure o header no reverse proxy/CDN.

Exemplo para frontend e API separados:

```text
Content-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data: https: https://api.example.com; font-src 'self'; connect-src 'self' https://api.example.com; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'
```

Use **o conteúdo exato de `dist/csp-header.txt` do build implantado**, substituindo a origem apenas por meio da configuração do build. Não configure `connect-src *`, `script-src *`, `'unsafe-eval'` ou `'unsafe-inline'` para scripts. Publique o bundle e a política juntos. A política não depende de JWT nem de segredo de API.

- Scripts e fontes são locais; não há CDN/fontes externas no código atual.
- Estilos inline são necessários para as amostras de cores/gradientes do seletor de temas. A exceção está restrita a `style-src`.
- `blob:` permite previews locais de upload; `data:` permite imagens embutidas. `https:` em imagens preserva o contrato existente que aceita URLs de imagens externas; isso não libera scripts nem conexões de API arbitrárias. Imagens remotas HTTP não são liberadas genericamente; a origem HTTP de API local, quando configurada, é explícita.
- `connect-src` libera somente self e a origem da API configurada.
- `frame-ancestors` precisa de header HTTP e não funciona em meta, conforme [MDN](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy/frame-ancestors). Por isso ele é excluído da meta e mantido nos headers gerados.
- O modo `vite dev` não recebe a política de produção porque HMR exige recursos diferentes. Para verificar produção, use o build e `serve:static`.

O código e os testes confirmam CSP no servidor estático versionado, mas **não confirmam configuração no host externo de produção**. Verifique a resposta HTML efetiva após deploy, não apenas o header de `/auth/validate` ou de outra API.

## Logs técnicos e informação de privacidade

O projeto não habilitava access logs Tomcat; não é correto assumir que estavam ativos por padrão. Agora `server.tomcat.accesslog.enabled=false` e `spring.mvc.log-request-details=false` tornam a opção explícita. Logs vão para stdout, sem política local de rotação/eliminação. O Dockerfile não configura coletor externo.

Logs próprios registram eventos técnicos para diagnóstico e segurança: data/hora, categoria da rota (por exemplo `/painel/**`), método, classe do erro e, quando necessário, ID interno do usuário. Erros inesperados recebem um identificador de evento e localização no código. Não registram username/email bruto, URL completa, query string, IP, User-Agent, JWT, cookies, senha, segredo do setup ou mensagem/cadeia de exceção potencialmente sensível. O rate limiter utiliza IP em memória para sua função, mas não o escreve em novos logs. O setup preserva eventos genéricos de sucesso/recusa sem segredo.

Perfis prod/supabase desabilitam logs SQL/bind e avisos do helper SQL que podem conter valores; restringem diagnósticos internos HTTP/Security e desabilitam logs de transporte do cliente que poderiam carregar URL/cabeçalhos. Não habilite TRACE/DEBUG de frameworks, wiretap, request/response bodies ou SQL em tráfego real. Variáveis externas podem sobrepor configurações Spring; o operador deve auditar as configurações efetivas.

**Retenção operacional: 30 dias**, com acesso restrito à equipe autorizada, transporte/armazenamento protegidos e eliminação automática configurada no coletor da hospedagem. A regra deve abranger réplicas, exports e backups de logs. O código sozinho **não garante essa exclusão**. Habilitar access logs em proxy/CDN/WAF exige configurar minimização: não guardar cookies, Authorization, tokens ou query strings; só guardar IP/User-Agent se houver necessidade operacional definida. Esses provedores podem processar dados de conexão independentemente da aplicação.

Texto informativo de privacidade para documentação do serviço: “Usamos registros técnicos mínimos para proteger o serviço, investigar falhas e controlar abusos. Esses registros podem incluir horário, operação, identificador interno de conta e códigos de erro. A infraestrutura de hospedagem pode processar dados de conexão, conforme sua configuração. O acesso é restrito e a retenção operacional prevista é de 30 dias. Cookies de autenticação e de proteção CSRF atendem funções necessárias do serviço; não há analytics/tracking ou cookies opcionais identificados no código atual.”

Este texto descreve o tratamento técnico e a política operacional pretendida; a ativação da retenção e as informações reais do provedor precisam ser verificadas pelo responsável pela implantação. Não foi adicionado banner de cookies nem alegado consentimento do visitante para logs de segurança.
