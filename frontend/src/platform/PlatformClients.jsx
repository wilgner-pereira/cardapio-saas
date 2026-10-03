import { useMemo, useState } from "react";
import { Plus, Search, Store } from "../components/icons.js";
import { PlatformLink } from "./PlatformLayout.jsx";

export function ClientStatus({ active }) {
  return <span className={`platform-status ${active ? "is-active" : "is-inactive"}`}>
    <span aria-hidden="true" />{active ? "Ativo" : "Inativo"}
  </span>;
}

export function ClientActions({ client, busy, onActivate, onDeactivate, onDelete }) {
  return <div className="platform-row-actions">
    <button type="button" className="secondary-action" disabled={busy}
      aria-label={`${client.ativo ? "Desativar" : "Ativar"} ${client.nome}`}
      onClick={() => client.ativo ? onDeactivate(client) : onActivate(client)}>
      {client.ativo ? "Desativar" : "Ativar"}
    </button>
    <button type="button" className="platform-danger-link" disabled={busy}
      aria-label={`Excluir ${client.nome}`} onClick={() => onDelete(client)}>Excluir</button>
  </div>;
}

export function PlatformClients({ clients, loading, error, busy, onRetry, onCreate, onNavigate, ...actions }) {
  const [search, setSearch] = useState("");

  // ⚡ Bolt Optimization: Memoize filtered client list to avoid re-filtering on unrelated re-renders (e.g., when actions or busy state change).
  // Short-circuits filtering when search term is empty while preserving original matching behavior.
  const { term, filtered } = useMemo(() => {
    const trimmedTerm = search.trim().toLocaleLowerCase("pt-BR");
    if (!trimmedTerm) {
      return { term: "", filtered: clients };
    }
    const result = clients.filter(client =>
      `${client.nome || ""} ${client.slug || ""}`.toLocaleLowerCase("pt-BR").includes(trimmedTerm)
    );
    return { term: trimmedTerm, filtered: result };
  }, [clients, search]);

  return <>
    <div className="platform-page-heading">
      <div><span className="platform-eyebrow">Gestão de clientes</span><h1>Clientes</h1><p>Gerencie os estabelecimentos e a disponibilidade de seus cardápios.</p></div>
      <button className="primary-action" type="button" onClick={onCreate}><Plus size={18} aria-hidden="true" />Novo cliente</button>
    </div>
    <section className="platform-card">
      <div className="platform-list-toolbar">
        <label className="platform-search"><span className="platform-sr-only">Buscar cliente por nome ou slug</span>
          <Search size={18} aria-hidden="true" />
          <input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Buscar por nome ou slug…" />
        </label>
        {!loading && !error && <span className="platform-muted" role="status">{filtered.length} {filtered.length === 1 ? "cliente" : "clientes"}</span>}
      </div>
      {loading ? <div className="platform-empty" role="status">Carregando clientes…</div>
        : error ? <div className="platform-empty"><p role="alert">{error}</p><button className="secondary-action" type="button" onClick={onRetry}>Tentar novamente</button></div>
        : filtered.length === 0 ? <div className="platform-empty">
          <Store size={32} aria-hidden="true" /><h2>{term ? "Nenhum cliente encontrado" : "Nenhum cliente cadastrado"}</h2>
          <p>{term ? "Tente outro nome ou slug." : "Cadastre o primeiro estabelecimento para começar."}</p>
          {!term && <button className="secondary-action" type="button" onClick={onCreate}>Cadastrar primeiro cliente</button>}
        </div> : <ul className="platform-client-list" aria-label="Estabelecimentos">
          <li className="platform-client-columns" aria-hidden="true"><span>Estabelecimento</span><span>Slug</span><span>Status</span><span>Ações</span></li>
          {filtered.map(client => <li key={client.id} className="platform-client-row">
            <PlatformLink className="platform-client-name" to={`/plataforma/clientes/${client.id}`} onNavigate={onNavigate}>
              <span className="platform-client-avatar" aria-hidden="true">{client.nome?.charAt(0).toUpperCase() || "C"}</span>
              <span>{client.nome}<small>Ver detalhes</small></span>
            </PlatformLink>
            <span className="platform-client-slug"><span className="platform-mobile-label">Slug</span>{client.slug}</span>
            <ClientStatus active={client.ativo} />
            <ClientActions client={client} busy={busy} {...actions} />
          </li>)}
        </ul>}
    </section>
  </>;
}
