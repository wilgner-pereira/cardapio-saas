# Administração da plataforma

O frontend utiliza o roteamento por `window.location.pathname`, `history.pushState` e `popstate` já existente. Não há nova biblioteca de roteamento ou de UI.

## Rotas e acesso

| Rota | Finalidade | Role exigida |
| --- | --- | --- |
| `/plataforma` | Visão geral com total, ativos e inativos derivados da listagem | `ROLE_PLATFORM_ADMIN` |
| `/plataforma/clientes` | Busca local, cadastro e gestão dos estabelecimentos | `ROLE_PLATFORM_ADMIN` |
| `/plataforma/clientes/{id}` | Dados do cliente, status e datas disponíveis | `ROLE_PLATFORM_ADMIN` |
| `/plataforma/administradores` | Cadastro de outro administrador da plataforma | `ROLE_PLATFORM_ADMIN` |
| `/painel/**` | Gestão do próprio cardápio | `ROLE_TENANT_ADMIN` |

Após o login, uma conta de plataforma segue para `/plataforma` e uma conta de cliente segue para `/painel/produtos`. Uma conta com ambas as roles explícitas segue para a plataforma após o login e pode acessar as áreas autorizadas pelo servidor. Uma conta sem role reconhecida não carrega APIs administrativas.

`ProtectedArea` recupera a sessão pelo `AuthContext` antes de montar páginas protegidas. Uma role incompatível redireciona para a área permitida sem carregar os endpoints da área anterior. A validação permanece restrita às rotas protegidas; o cardápio público não consulta a sessão.

## Contratos utilizados

- `GET /plataforma/estabelecimentos`
- `POST /plataforma/estabelecimentos`
- `GET /plataforma/estabelecimentos/{id}`
- `PATCH /plataforma/estabelecimentos/{id}/ativar`
- `PATCH /plataforma/estabelecimentos/{id}/desativar`
- `DELETE /plataforma/estabelecimentos/{id}`
- `POST /plataforma/admins`

Todas as chamadas passam pelo `request()` existente, com cookies, CSRF e recuperação de 401/refresh. Erros de validação preservam a mensagem e os `fieldErrors` retornados pelo backend. Credenciais dos formulários não são persistidas no navegador.

O cadastro de clientes recebe os campos de `EstabelecimentoCreateRequestDTO`; o slug é gerado pelo backend a partir do username. O cadastro de administradores recebe os campos de `RegisterRequestDTO`. Não há endpoint de listagem de administradores, portanto a interface oferece apenas o cadastro.

A listagem resumida não fornece datas. Por isso a visão geral apresenta estabelecimentos cadastrados, sem afirmar que são os mais recentes; as datas aparecem nos detalhes através de `EstabelecimentoResponseDTO`.

## Proteção das contas administrativas

O modelo atual exige que cada usuário tenha um estabelecimento, inclusive administradores da plataforma. Os endpoints de clientes excluem registros vinculados a qualquer usuário com `ROLE_PLATFORM_ADMIN`, inclusive usuários com ambas as roles. Estes registros não entram nas métricas nem podem ser obtidos, ativados, desativados ou excluídos pelas operações de clientes. A checagem é feita no backend pelas consultas do repositório, sem depender de nomes ou slugs na interface.

O controle de acesso do Spring Security permanece independente: uma conta de plataforma não recebe automaticamente a role de cliente nem permissão para `/painel/**`. Não há impersonation.

## Desativação e exclusão

Desativação exige confirmação e informa que o painel e o cardápio público ficam indisponíveis enquanto o cliente estiver inativo. Reativação atualiza o estado da interface após confirmação da API.

Exclusão exige confirmação explícita e remove o estabelecimento, os usuários vinculados, os produtos associados e as sessões desses usuários, conforme as cascatas JPA/FKs atuais. A interface só remove o cliente da lista após o sucesso da API.

O endpoint existente de exclusão de estabelecimento não executa limpeza de imagens no Supabase. O inventário de storage permanece conservado, com o vínculo de estabelecimento nulo por `ON DELETE SET NULL`; o frontend não promete exclusão remota nem libera quota. A conciliação desses objetos continua uma responsabilidade operacional do fluxo de storage existente.

## Validação

Os testes frontend cobrem roteamento por role, recuperação em StrictMode, dashboard, busca, cadastros, detalhes, ativação/desativação, confirmações, cancelamentos, erros, logout, modais acessíveis e o cliente HTTP.

`PlatformDashboardIntegrationTest` cobre os endpoints com cookies reais de teste, proteção das contas administrativas, CSRF, separação de roles, indisponibilidade do tenant desativado e as cascatas da exclusão, preservando o inventário de storage.

As suítes de backend usam H2 em modo PostgreSQL e Flyway; isso não substitui validação em produção/PostgreSQL/Supabase.
