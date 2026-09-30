import { useState } from "react";
import { api } from "../api/client.js";
import { Loader2 } from "../components/icons.js";
import { PlatformModal } from "./PlatformModal.jsx";

export function CreateAccountModal({ kind, onClose, onCreated }) {
  const isClient = kind === "client";
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});

  async function handleSubmit(event) {
    event.preventDefault();
    if (saving) return;
    const payload = Object.fromEntries(new FormData(event.currentTarget));
    for (const key of Object.keys(payload)) {
      if (key !== "password") payload[key] = payload[key].trim();
    }
    setSaving(true);
    setError("");
    setFieldErrors({});
    try {
      const result = isClient
        ? await api.createPlatformEstablishment(payload)
        : await api.createPlatformAdmin(payload);
      onCreated(result);
    } catch (cause) {
      setError(cause.message);
      setFieldErrors(cause.fieldErrors || {});
    } finally {
      setSaving(false);
    }
  }

  function field(name, label, attributes = {}) {
    const errorId = `platform-field-${name}-error`;
    const inputId = `platform-field-${name}`;
    return <div className="platform-field" key={name}>
      <label htmlFor={inputId}>{label}</label>
      {name === "descricao"
        ? <textarea id={inputId} name={name} aria-invalid={Boolean(fieldErrors[name])} aria-describedby={fieldErrors[name] ? errorId : undefined} {...attributes} />
        : <input id={inputId} name={name} aria-invalid={Boolean(fieldErrors[name])} aria-describedby={fieldErrors[name] ? errorId : undefined} {...attributes} />}
      {fieldErrors[name] && <span className="form-error" id={errorId}>{fieldErrors[name]}</span>}
    </div>;
  }

  return <PlatformModal title={isClient ? "Novo cliente" : "Novo administrador"}
    description={isClient ? "Cadastre o estabelecimento e o acesso de seu responsável." : "O novo administrador terá acesso à gestão da plataforma."}
    busy={saving} onClose={onClose}>
    <form className="product-form platform-form" onSubmit={handleSubmit}>
      <fieldset disabled={saving}>
        <legend>{isClient ? "Acesso do cliente" : "Acesso do administrador"}</legend>
        {field("username", "Usuário", { required: true, minLength: 3, maxLength: 50, pattern: "[A-Za-z0-9._\\-]+", autoComplete: "off" })}
        <p className="platform-field-help">De 3 a 50 caracteres: letras, números, ponto, hífen ou sublinhado.{isClient && " O endereço do cardápio será gerado a partir deste usuário."}</p>
        {field("email", "Email de acesso", { required: true, type: "email", maxLength: 255, autoComplete: "off" })}
        {field("password", "Senha", { required: true, type: "password", minLength: 8, maxLength: 100, autoComplete: "new-password" })}
        <p className="platform-field-help">Use uma senha de 8 a 100 caracteres.</p>
      </fieldset>
      {isClient && <fieldset disabled={saving}>
        <legend>Estabelecimento</legend>
        {field("nome", "Nome", { required: true, minLength: 3, maxLength: 100 })}
        {field("descricao", "Descrição", { maxLength: 500 })}
        <div className="form-grid">
          {field("horarioFuncionamento", "Horário de funcionamento", { maxLength: 100 })}
          {field("telefone", "Telefone", { maxLength: 20, type: "tel", pattern: "[0-9\\s\\(\\)\\-]*" })}
        </div>
        {field("endereco", "Endereço", { maxLength: 255 })}
        {field("emailContato", "Email de contato", { type: "email", maxLength: 255 })}
      </fieldset>}
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="platform-modal-actions">
        <button className="secondary-action" type="button" disabled={saving} onClick={onClose}>Cancelar</button>
        <button className="primary-action" type="submit" disabled={saving}>
          {saving && <Loader2 className="spin" size={18} aria-hidden="true" />}
          {saving ? "Criando…" : isClient ? "Criar cliente" : "Criar administrador"}
        </button>
      </div>
    </form>
  </PlatformModal>;
}
