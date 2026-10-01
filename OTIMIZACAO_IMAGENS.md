# Upload de imagens

Novos uploads passam por este fluxo:

```text
seleção local → validação do original → inspeção de dimensões → decode/orientação
→ resize/crop → WebP → validação do resultado → preview → upload com perfil
→ validação independente no Spring → reserva de quota → Supabase → STORED
```

## Perfis e limites

| Regra | Produto | Capa (`hero`) |
|---|---|---|
| Original no navegador | JPEG/JPG, PNG ou WebP, até 10 MiB | Idem |
| Dimensões do original | Até 8192 px por eixo e 24 milhões de pixels | Idem |
| Resultado | 512×512, crop central quadrado sem distorção | Proporção preservada, até 1920×1920, sem crop e sem ampliar |
| Formato do resultado | WebP estático | WebP estático |
| Qualidade | 0,85; se ultrapassar o limite, 0,82 e 0,78 | Idem |
| Limite de segurança do resultado | 500 KiB (512000 bytes) | 1 MiB (1048576 bytes) |

O tamanho final varia conforme o conteúdo. A compressão para assim que a imagem cabe no limite; não há meta exata em bytes, redução de resolução adicional ou tentativas abaixo de 0,78. Resultados que ainda excedam o limite são rejeitados. Imagens pequenas de produto são ampliadas para o quadrado de 512 px; uma capa pequena mantém suas dimensões.

`VITE_MAX_SOURCE_IMAGE_BYTES` pode reduzir o limite de seleção local, até o teto de 10485760 bytes. Esse limite é diferente do limite HTTP: o original nunca é enviado. `MAX_FILE_SIZE=1MB` e `MAX_REQUEST_SIZE=2MB` limitam o multipart no Spring. A validação também aplica os limites fixos do perfil, mesmo que um operador aumente o limite multipart. Reduzir `MAX_FILE_SIZE` abaixo de 1 MiB reduz o tamanho aceito para capas.

GIF, HEIC e HEIF não são aceitos para novas seleções. WebP animado também é rejeitado. URLs antigas, inclusive GIF, continuam sendo exibidas e entregues por `/public/storage/{fileName}` sem nova validação de upload ou migração. Ao substituir uma imagem antiga, apenas a nova imagem passa pelo pipeline.

## Processamento e compatibilidade

`imageOptimization.js` escolhe o processamento em um Web Worker quando Worker, OffscreenCanvas e createImageBitmap estão disponíveis. O worker usa o mesmo `imageProcessing.js` do fallback. Isso mantém decode, desenho e codificação fora da thread da interface nos navegadores compatíveis. O fallback usa createImageBitmap e canvas.toBlob; quando o bitmap não está disponível ou falha, usa HTMLImageElement com carregamento assíncrono. O canvas tem somente as dimensões de saída.

createImageBitmap usa `imageOrientation: "from-image"`; o fallback depende do suporte de orientação EXIF dos navegadores modernos. A imagem é desenhada já orientada e recodificada sem copiar nome, EXIF ou outros metadados originais. O nome local enviado é `optimized.webp`. As chaves remotas continuam sendo UUID sem extensão.

Um navegador que não codifica WebP recebe uma mensagem clara para usar outro navegador atualizado. Não há fallback de upload para PNG, JPEG ou para o original. A função verifica o MIME do blob porque canvas pode retornar PNG silenciosamente quando um encoder não está disponível. O processamento em worker termina ao concluir, falhar, cancelar ou ultrapassar 40 segundos. No fallback, um resultado que termina após cancelamento é descartado e seus recursos são liberados.

Os modais reutilizam `useOptimizedImage`: seleção, processamento, erro e cancelamento têm uma só implementação. O botão de salvar e o seletor ficam bloqueados durante o processamento. O arquivo otimizado é mantido para preview e upload, sem recompressão no cliente HTTP. Bitmaps são fechados; canvas é reduzido após processamento; URLs de decode e preview são revogadas na troca/remoção/desmontagem. O fluxo otimista já existente em App continua revogando suas URLs e compensando falhas no salvamento.

## Contrato e segurança do backend

`POST /painel/storage/upload` exige multipart `file` e `type=product|hero`. O perfil escolhe limites; não concede permissões nem define tenant. O endpoint continua exigindo TENANT_ADMIN, autenticação e CSRF. O tenant vem do usuário autenticado. Frontend e backend devem ser publicados juntos: clientes antigos que enviam o original sem perfil receberão HTTP 400.

O servidor aceita apenas `image/webp` para novos uploads. Não confia no nome, MIME ou perfil como evidência de conteúdo válido. Antes da reserva de quota, ele:

1. Aplica o menor limite entre multipart e perfil, incluindo uma leitura limitada independente do tamanho declarado.
2. Valida RIFF/WEBP e o comprimento exato do arquivo, limites dos chunks e um único frame estático.
3. Inspeciona dimensões tanto em VP8X quanto no bitstream VP8/VP8L. Divergência de dimensões, animação, frames duplicados, truncamento e conteúdo além do RIFF são rejeitados. As dimensões são limitadas antes do decode.
4. Usa ImageIO com `com.twelvemonkeys.imageio:imageio-webp:3.15.2` para confirmar o formato e decodificar pixels. A dependência adiciona suporte WebP ausente no JDK, sem executáveis nativos. Não há transformação no servidor. Erros do decoder são traduzidos para uma mensagem fixa.

O limite máximo de decode é 1920×1920 para capa e 512×512 para produto, com entradas de até 1 MiB. Esses limites reduzem a expansão e o uso de memória por imagem. O navegador também verifica cabeçalhos antes do decode e confere dimensões novamente depois. Não se promete segurança absoluta contra falhas desconhecidas de codecs: mantenha o navegador e o plugin atualizados e dimensione heap/concurrency da aplicação conforme a carga real.

`StorageQuotaService.reserve` continua contabilizando `bytes.length`, agora do WebP validado; a reserva persiste antes da rede e mantém o lock por tenant. Supabase recebe `Content-Type: image/webp`. Falhas ambíguas permanecem cobradas como UNCERTAIN; recusa explícita e compensação seguem as regras anteriores. StorageImageLifecycle, ownership, referência compartilhada, exclusão depois do commit, rollback e fila durável de retry não foram alterados.

O perfil de upload não é uma restrição persistida de uso da URL: imagens do mesmo tenant continuam podendo compartilhar referências como antes. Ele jamais autoriza reutilizar a imagem de outro tenant; essa fronteira continua sendo o ledger STORED e as verificações do lifecycle.

## Verificação

As suítes cobrem validação local, geometria de crop/resize, orientação do decode, fallback, piso de qualidade, liberação/cancelamento, envio do arquivo otimizado e perfil via FormData/CSRF, WebP real lossy/lossless com alpha, conteúdo inválido, dimensões externas/internas, quota, falhas remotas, rollback, substituição, proteção entre tenants e Content-Type da entrega pública.

Fixtures reais estão em `src/test/resources/images/`: produto 512×512 lossy, capa 1920×1280 lossy, arquivo acima do limite de dimensões e WebP lossless com alpha. Elas são dados de teste locais, sem pessoas ou metadados pessoais.

Comandos de validação: `mvn -B test`, `npm test` e `npm run build`. Smoke de browser e chamadas simuladas a storage não substituem verificação real do bucket/deploy. A arquitetura de download, CDN e signed URLs permanece fora deste trabalho.

## Evidência desta implementação

Verificado em 30/09/2026 neste checkout:

- Backend completo: 148 testes, zero falhas, erros ou testes ignorados, via Maven 3.9.11 instalado pelo wrapper. A execução local usou Java 25 e compilação com `release 21`; CI/containers com Java 21 não foram executados neste trabalho.
- Frontend completo: 145 testes em 13 arquivos, todos passando, via `npm test -- --reporter=dot`.
- Build: `npm run build` passou, incluindo o worker de aproximadamente 4,73 kB e os arquivos de CSP existentes. Nenhuma diretiva de CSP foi flexibilizada.
- Smoke no Chrome 153: modais, Worker/OffscreenCanvas, EXIF, preview, FormData, perfil e CSRF passaram com o bundle de produção e seu header CSP, em viewport desktop e 390×844. Todas as URLs de preview criadas foram revogadas; cada seleção fez um único processamento. Backend/storage foram simulados no smoke de UI.
- Os arquivos gerados pelo Chrome foram aceitos separadamente pelo validador Java real: produto 60564 bytes e capa 951776 bytes.
- O teste HTTP local do backend confirmou envio WebP com MIME correto e entrega pública de WebP e GIF antigo. Testes de quota, concorrência, rollback, falha remota, substituição e uso indevido entre tenants passaram na suíte completa.
- `git diff --check` passou. Não houve alterações em migrations, lockfiles, autenticação, locks, StorageQuotaService ou StorageImageLifecycle.

A amostra sintética de celular era um JPEG 4032×3024 de 6863873 bytes. O resultado foi produto 512×512 de 60564 bytes (redução de 99,1%) e capa 1920×1440 de 951776 bytes (redução de 86,1%). Esses números descrevem essa amostra, não uma garantia de redução para todas as fotos.

O teste focal inicial encontrou um caso inadequado de corrupção: zerar pixels comprimidos ainda pode produzir uma imagem decodificável. O teste passou a usar uma partição VP8 truncada, mantendo os tamanhos RIFF/chunk consistentes, e o validador também passou a conferir o tamanho da partição antes do decode. O teste específico e a suíte completa passaram depois da correção.

Os logs e o relatório de smoke desta execução estão em `target/image-optimization-backend-tests.log`, `target/image-optimization-frontend-tests.log`, `target/image-optimization-build.log`, `target/image-browser-backend-validation.log` e `target/image-browser-smoke.json`. São artefatos locais ignorados pelo Git. Não houve publicação, commit, push ou upload para o Supabase real.

Validações futuras úteis: executar o smoke em Safari, Firefox e celulares reais; verificar upload/substituição no bucket de staging com a aplicação publicada; medir memória/concurrency sob a carga real. Não há migração obrigatória de imagens antigas.

## Arquivos alterados ou adicionados

| Arquivo | Motivo |
|---|---|
| `frontend/src/api/uploadValidation.js` | Separar original e resultado, remover GIF/HEIC/HEIF, centralizar perfis e limites. |
| `frontend/src/api/imageOptimization.js` | Entrada compartilhada; escolher worker/fallback e controlar abort/timeout. |
| `frontend/src/api/imageProcessing.js` | Inspeção antes do decode, orientação, crop/resize, WebP, compressão limitada e liberação de recursos. |
| `frontend/src/api/imageOptimization.worker.js` | Executar o mesmo processamento fora da thread da interface. |
| `frontend/src/components/useOptimizedImage.js` | Compartilhar seleção, arquivo resultante, loading, erros e cancelamento. |
| `frontend/src/components/ProductEditorModal.jsx` | Otimizar produto na seleção, preview do resultado e bloquear salvamento durante processamento. |
| `frontend/src/components/EstablishmentEditorModal.jsx` | Otimizar capa na seleção, manter preview/remoção e bloquear salvamento durante processamento. |
| `frontend/src/api/client.js` | Enviar somente WebP validado e perfil explícito, mantendo fetch/cookies/CSRF existentes. |
| `frontend/src/App.jsx` | Passar product/hero no fluxo de salvamento com compensação existente. |
| `frontend/src/styles.css` | Limitar a prévia quadrada de produto a 180 px usando o padrão existente. |
| `frontend/src/api/imageOptimization.test.js` | Cobrir geometria, orientação, formatos, segurança, qualidade, fallback e recursos/worker. |
| `frontend/src/api/client.test.js` | Verificar arquivo otimizado, perfil multipart, cookies e CSRF. |
| `frontend/src/components/UploadValidation.test.jsx` | Atualizar limites e cobrir formato/extensão, arquivo vazio e original/resultado. |
| `frontend/src/components/ProductEditorModal.test.jsx` | Verificar preview, arquivo único, cleanup, cancelamento e seleção obsoleta. |
| `frontend/src/components/EstablishmentEditorModal.test.jsx` | Verificar preview/submissão do WebP, cleanup e erro sem substituir preview. |
| `frontend/src/App.test.jsx` | Integrar otimização assíncrona aos testes de persistência, restauração e cleanup. |
| `src/main/java/com/wilgner/cardapio/service/ImageUploadProfile.java` | Contrato explícito de perfil, dimensões e bytes no servidor. |
| `src/main/java/com/wilgner/cardapio/service/SupabaseStorageService.java` | Validar tamanho real, estrutura/bitstream/dimensões WebP e decode com erros fixos. |
| `src/main/java/com/wilgner/cardapio/service/TenantStorageService.java` | Validar perfil antes da quota e enviar MIME WebP; manter contabilização e compensação. |
| `src/main/java/com/wilgner/cardapio/controller/SupabaseStorageController.java` | Receber e validar tipo de upload preservando autorização e tenant autenticado. |
| `src/main/java/com/wilgner/cardapio/config/MultipartConfig.java` | Alinhar limites padrão do multipart com transporte do resultado otimizado. |
| `src/main/resources/application.properties` | Defaults de transporte: arquivo 1 MiB, requisição 2 MiB. |
| `pom.xml` | Adicionar apenas o plugin ImageIO WebP necessário para decode independente no Java. |
| `src/test/java/com/wilgner/cardapio/service/SupabaseStorageServiceTest.java` | Fixtures reais, spoofing, corrupção, dimensões, leitura limitada e limites de perfil. |
| `src/test/java/com/wilgner/cardapio/service/TenantStorageFailureTest.java` | Bytes otimizados na quota, MIME WebP, validação antes da reserva e falha de persistência. |
| `src/test/java/com/wilgner/cardapio/service/StorageQuotaIntegrationTest.java` | Atualizar perfil e manter invariantes de quota/tenant/concurrency/restart/falhas. |
| `src/test/java/com/wilgner/cardapio/service/ImageReplacementIntegrationTest.java` | Atualizar contrato de upload sem remover garantias de rollback/substituição/ownership. |
| `src/test/java/com/wilgner/cardapio/service/StorageRedirectTest.java` | Upload real via servidor HTTP local e MIME correto na entrega de WebP/GIF, além das proteções existentes. |
| `src/test/resources/images/product.webp` | Fixture WebP lossy 512×512. |
| `src/test/resources/images/hero.webp` | Fixture WebP lossy 1920×1280. |
| `src/test/resources/images/oversized.webp` | Fixture 2000×100 para rejeição por dimensões. |
| `src/test/resources/images/lossless-alpha.webp` | Fixture WebP lossless 16×8 com transparência. |
| `.env.example` | Documentar limites HTTP do arquivo otimizado. |
| `frontend/.env.example` | Documentar limite configurável do original local. |
| `OTIMIZACAO_IMAGENS.md` | Registrar fluxo, contrato, limites, segurança, compatibilidade, verificação e este inventário. |
