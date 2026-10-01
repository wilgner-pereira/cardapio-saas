import { ArrowLeft } from "../components/icons.js";
import { ClientActions, ClientStatus } from "./PlatformClients.jsx";
import { PlatformLink } from "./PlatformLayout.jsx";

function formatDate(value) {
  if (!value) return "Não informado";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Não informado" : date.toLocaleString("pt-BR");
}

export function PlatformClientDetails({ client, loading, error, busy, onRetry, onNavigate, onEdit, onChangePassword, ...actions }) {
  return <>
    <PlatformLink className="platform-back-link" to="/plataforma/clientes" onNavigate={onNavigate}><ArrowLeft size={18} aria-hidden="true" />Voltar para clientes</PlatformLink>
    {loading ? <section className="platform-card platform-empty" role="status">Carregando dados do cliente…</section>
      : error ? <section className="platform-card platform-empty"><p role="alert">{error}</p><button className="secondary-action" type="button" onClick={onRetry}>Tentar novamente</button></section>
      : client && <>
        <div className="platform-page-heading"><div><span className="platform-eyebrow">Detalhes do cliente</span><h1>{client.nome}</h1><p>{client.slug}</p></div><ClientStatus active={client.ativo} /></div>
        <section className="platform-card platform-detail-card">
          <h2>Dados do estabelecimento</h2>
          <dl className="platform-details">
            {[["Nome", client.nome], ["Slug", client.slug], ["Descrição", client.descricao], ["Telefone", client.telefone], ["Endereço", client.endereco], ["Email de contato", client.emailContato], ["Horário de funcionamento", client.horarioFuncionamento], ["Tema", client.tema], ["Criado em", formatDate(client.criadoEm)], ["Atualizado em", formatDate(client.atualizadoEm)]].map(([label, value]) =>
              <div key={label}><dt>{label}</dt><dd>{value || "Não informado"}</dd></div>)}
          </dl>
          <div className="platform-modal-actions"><button className="secondary-action" type="button" disabled={busy} onClick={() => onEdit(client)}>Editar cliente</button></div>
        </section>
        <section className="platform-card platform-detail-card">
          <h2>Acesso do cliente</h2>
          <dl className="platform-details">
            <div><dt>Usuário</dt><dd>{client.username || "Não informado"}</dd></div>
            <div><dt>Email de acesso</dt><dd>{client.email || "Não informado"}</dd></div>
          </dl>
          <div className="platform-modal-actions"><button className="secondary-action" type="button" disabled={busy} onClick={() => onChangePassword(client)}>Alterar senha</button></div>
        </section>
        <section className="platform-card platform-detail-card"><h2>Disponibilidade e gestão</h2>
          <p className="platform-muted">{client.ativo ? "O painel e o cardápio público estão disponíveis para este cliente." : "O painel e o cardápio público estão indisponíveis enquanto este cliente estiver inativo."}</p>
          <ClientActions client={client} busy={busy} {...actions} />
        </section>
      </>}
  </>;
}
