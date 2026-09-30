import { Plus, Store } from "../components/icons.js";
import { ClientStatus } from "./PlatformClients.jsx";
import { PlatformLink } from "./PlatformLayout.jsx";

export function PlatformDashboard({ clients, loading, error, onRetry, onCreate, onNavigate }) {
  const active = clients.filter(client => client.ativo).length;
  return <>
    <div className="platform-page-heading">
      <div><span className="platform-eyebrow">Seu negócio, em perspectiva</span><h1>Visão geral</h1><p>Acompanhe os estabelecimentos da sua plataforma.</p></div>
      <button className="primary-action" type="button" onClick={onCreate}><Plus size={18} aria-hidden="true" />Novo cliente</button>
    </div>
    {loading ? <section className="platform-card platform-empty" role="status">Carregando estabelecimentos…</section>
      : error ? <section className="platform-card platform-empty"><p role="alert">{error}</p><button className="secondary-action" type="button" onClick={onRetry}>Tentar novamente</button></section>
      : <>
        <dl className="platform-metrics">
          {[{ label: "Total de clientes", value: clients.length }, { label: "Clientes ativos", value: active }, { label: "Clientes inativos", value: clients.length - active }].map(metric =>
            <div className="platform-card" key={metric.label}><dt>{metric.label}</dt><dd>{metric.value}</dd></div>)}
        </dl>
        <section className="platform-card">
          <div className="platform-card-heading"><h2>Estabelecimentos cadastrados</h2><PlatformLink to="/plataforma/clientes" onNavigate={onNavigate}>Ver todos os clientes</PlatformLink></div>
          {clients.length === 0 ? <div className="platform-empty">
            <Store size={36} aria-hidden="true" /><h3>Nenhum cliente cadastrado</h3><p>Seu primeiro cliente começa aqui. Cadastre um estabelecimento e seu responsável.</p>
            <button className="secondary-action" type="button" onClick={onCreate}>Cadastrar primeiro cliente</button>
          </div> : <ul className="platform-overview-list">
            {clients.slice(0, 5).map(client => <li key={client.id}>
              <PlatformLink to={`/plataforma/clientes/${client.id}`} onNavigate={onNavigate}><strong>{client.nome}</strong><span>{client.slug}</span></PlatformLink>
              <ClientStatus active={client.ativo} />
            </li>)}
          </ul>}
        </section>
      </>}
  </>;
}
