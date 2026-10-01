import { useEffect, useRef, useState } from "react";
import { api } from "../api/client.js";
import { useAuth } from "../auth/AuthContext.jsx";
import { Check, Loader2, Plus, RefreshCw, ShieldCheck } from "../components/icons.js";
import { CreateAccountModal } from "./CreateAccountModal.jsx";
import { PlatformClientDetails } from "./PlatformClientDetails.jsx";
import { PlatformClients } from "./PlatformClients.jsx";
import { PlatformDashboard } from "./PlatformDashboard.jsx";
import { PlatformLayout } from "./PlatformLayout.jsx";
import { PlatformModal } from "./PlatformModal.jsx";
import { EditClientModal } from "./EditClientModal.jsx";
import { ChangeClientPasswordModal } from "./ChangeClientPasswordModal.jsx";

export function PlatformPage({ section, clientId, onNavigate }) {
  const auth = useAuth();
  const [clients, setClients] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [detailReload, setDetailReload] = useState(0);
  const [detail, setDetail] = useState({});
  const [modal, setModal] = useState(null);
  const [modalError, setModalError] = useState("");
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const mutationInProgress = useRef(false);
  const listRequest = useRef(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    if (!listRequest.current || listRequest.current.reload !== reload) {
      listRequest.current = { reload, promise: api.getPlatformEstablishments() };
    }
    listRequest.current.promise.then(data => {
      if (active) setClients(data);
    }).catch(cause => {
      if (active) setError(cause.message);
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [reload]);

  useEffect(() => {
    if (!clientId) return;
    let active = true;
    setDetail({ id: clientId, loading: true });
    api.getPlatformEstablishment(clientId).then(client => {
      if (active) setDetail({ id: clientId, client });
    }).catch(cause => {
      if (active) setDetail({ id: clientId, error: cause.message });
    });
    return () => { active = false; };
  }, [clientId, detailReload]);

  function openModal(nextModal) {
    setModalError("");
    setActionError("");
    setNotice("");
    setModal(nextModal);
  }

  function closeModal() {
    if (mutationInProgress.current) return;
    setModal(null);
    setModalError("");
  }

  async function mutateClient(kind, client) {
    if (mutationInProgress.current) return;
    mutationInProgress.current = true;
    setBusy(true);
    setModalError("");
    setActionError("");
    setNotice("");
    try {
      if (kind === "delete") {
        await api.deletePlatformEstablishment(client.id);
        setClients(current => current.filter(item => item.id !== client.id));
        setNotice(`${client.nome} foi excluído.`);
        if (clientId === String(client.id)) onNavigate("/plataforma/clientes", { replace: true });
      } else {
        const updated = kind === "activate"
          ? await api.activatePlatformEstablishment(client.id)
          : await api.deactivatePlatformEstablishment(client.id);
        setClients(current => current.map(item => item.id === updated.id ? { ...item, ...updated } : item));
        setDetail(current => current.id === String(updated.id) ? { id: current.id, client: { ...current.client, ...updated } } : current);
        setNotice(`${updated.nome} foi ${updated.ativo ? "ativado" : "desativado"}.`);
      }
      setModal(null);
    } catch (cause) {
      if (kind === "activate") setActionError(cause.message);
      else setModalError(cause.message);
    } finally {
      mutationInProgress.current = false;
      setBusy(false);
    }
  }

  async function logout() {
    if (loggingOut) return;
    setLoggingOut(true);
    setActionError("");
    try {
      await auth.logout();
      onNavigate("/login", { replace: true });
    } catch (cause) {
      setActionError(cause.message);
    } finally {
      setLoggingOut(false);
    }
  }

  const actions = {
    busy,
    onActivate: client => void mutateClient("activate", client),
    onDeactivate: client => openModal({ kind: "deactivate", client }),
    onDelete: client => openModal({ kind: "delete", client })
  };
  const listProps = { clients, loading, error, onRetry: () => setReload(value => value + 1), onCreate: () => openModal({ kind: "client" }), onNavigate };
  const deleting = modal?.kind === "delete";
  const confirming = deleting || modal?.kind === "deactivate";

  return <div className="platform-page">
    <div inert={modal ? "" : undefined}>
      <PlatformLayout section={section} username={auth.username} onNavigate={onNavigate} onLogout={logout} loggingOut={loggingOut}>
        <div className="platform-feedback" aria-live="polite">
          {notice && <p className="platform-success" role="status"><Check size={18} aria-hidden="true" />{notice}</p>}
        </div>
        {actionError && <p className="platform-action-error" role="alert">{actionError}</p>}
        {section !== "administradores" && !clientId && <div className="platform-refresh">
          <button className="secondary-action" type="button" disabled={loading || busy} onClick={() => setReload(value => value + 1)}>
            <RefreshCw size={16} aria-hidden="true" />Atualizar lista
          </button>
        </div>}
        {section === "dashboard" && <PlatformDashboard {...listProps} />}
        {section === "clientes" && !clientId && <PlatformClients {...listProps} {...actions} />}
        {clientId && <PlatformClientDetails client={detail.id === clientId ? detail.client : null}
          loading={detail.id !== clientId || detail.loading} error={detail.id === clientId ? detail.error : ""}
          onRetry={() => setDetailReload(value => value + 1)} onNavigate={onNavigate}
          onEdit={client => openModal({ kind: "edit", client })}
          onChangePassword={client => openModal({ kind: "password", client })} {...actions} />}
        {section === "administradores" && <>
          <div className="platform-page-heading"><div><span className="platform-eyebrow">Acesso à plataforma</span><h1>Administradores</h1><p>Cadastre outras pessoas para administrar o SaaS.</p></div></div>
          <section className="platform-card platform-admin-card">
            <span className="platform-admin-icon"><ShieldCheck size={32} aria-hidden="true" /></span>
            <div><h2>Novo administrador da plataforma</h2><p>Este acesso permite gerenciar clientes e cadastrar outros administradores. Conceda-o somente a pessoas autorizadas a administrar a plataforma.</p>
              <button className="primary-action" type="button" onClick={() => openModal({ kind: "admin" })}><Plus size={18} aria-hidden="true" />Novo administrador</button>
            </div>
          </section>
        </>}
      </PlatformLayout>
    </div>
    {(modal?.kind === "client" || modal?.kind === "admin") && <CreateAccountModal kind={modal.kind} onClose={closeModal} onCreated={created => {
      setModal(null);
      if (modal.kind === "client") {
        setNotice(`${created.nome} foi cadastrado com sucesso.`);
        setReload(value => value + 1);
      } else setNotice(`Administrador ${created.username} criado com sucesso.`);
    }} />}
    {modal?.kind === "edit" && <EditClientModal client={modal.client} onClose={closeModal} onUpdated={updated => {
      setDetail(current => current.id === String(updated.id) ? { id: current.id, client: updated } : current);
      setClients(current => current.map(item => item.id === updated.id ? { ...item, ...updated } : item));
      setModal(null);
      setNotice(`${updated.nome} foi atualizado.`);
    }} />}
    {modal?.kind === "password" && <ChangeClientPasswordModal client={modal.client} onClose={closeModal} onChanged={() => {
      setModal(null);
      setNotice(`Senha de ${modal.client.nome} alterada com sucesso.`);
    }} />}
    {confirming && <PlatformModal title={`${deleting ? "Excluir" : "Desativar"} ${modal.client.nome}?`}
      description={deleting
        ? "Esta ação é permanente e não pode ser desfeita."
        : "O painel e o cardápio público deixarão de ficar disponíveis enquanto o estabelecimento estiver desativado."}
      busy={busy} onClose={closeModal}>
      {deleting && <><p>Serão removidos permanentemente:</p><ul className="platform-delete-list"><li>O estabelecimento</li><li>Os usuários vinculados e suas sessões</li><li>Os produtos associados</li></ul></>}
      {modalError && <p className="form-error" role="alert">{modalError}</p>}
      <div className="platform-modal-actions">
        <button className="secondary-action" type="button" disabled={busy} onClick={closeModal}>Cancelar</button>
        <button className="platform-danger-action" type="button" disabled={busy} onClick={() => void mutateClient(modal.kind, modal.client)}>
          {busy && <Loader2 className="spin" size={18} aria-hidden="true" />}
          {busy ? "Aguarde…" : deleting ? "Excluir permanentemente" : "Desativar cliente"}
        </button>
      </div>
    </PlatformModal>}
  </div>;
}
