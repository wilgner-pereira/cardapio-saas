import { useRef, useState } from "react";
import { api } from "../api/client.js";
import { Loader2 } from "../components/icons.js";
import { PlatformModal } from "./PlatformModal.jsx";

export function EditClientModal({ client, onClose, onUpdated }) {
  const [saving, setSaving] = useState(false);
  const submitting = useRef(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting.current) return;
    submitting.current = true;
    const payload = Object.fromEntries(new FormData(event.currentTarget));
    for (const key of Object.keys(payload)) payload[key] = payload[key].trim();
    setSaving(true);
    setError("");
    setFieldErrors({});
    try {
      onUpdated(await api.updatePlatformEstablishment(client.id, payload));
    } catch (cause) {
      setError(cause.message);
      setFieldErrors(cause.fieldErrors || {});
    } finally {
      submitting.current = false;
      setSaving(false);
    }
  }

  function field(name, label, attributes = {}) {
    const id = `platform-edit-${name}`;
    const errorId = `${id}-error`;
    const props = { id, name, defaultValue: client[name] || "", "aria-invalid": Boolean(fieldErrors[name]),
      "aria-describedby": fieldErrors[name] ? errorId : undefined, ...attributes };
    return <div className="platform-field" key={name}>
      <label htmlFor={id}>{label}</label>
      {name === "descricao" ? <textarea {...props} /> : <input {...props} />}
      {fieldErrors[name] && <span className="form-error" id={errorId}>{fieldErrors[name]}</span>}
    </div>;
  }

  return <PlatformModal title="Editar cliente" description={`O endereço público /cardapio/${client.slug} permanece igual.`}
    busy={saving} onClose={onClose}>
    <form className="product-form platform-form" onSubmit={handleSubmit}>
      <fieldset disabled={saving}>
        <legend>Dados do estabelecimento</legend>
        {field("nome", "Nome", { required: true, minLength: 3, maxLength: 100 })}
        {field("descricao", "Descrição", { maxLength: 500 })}
        <div className="form-grid">
          {field("horarioFuncionamento", "Horário de funcionamento", { maxLength: 100 })}
          {field("telefone", "Telefone", { maxLength: 20, type: "tel", pattern: "[0-9\\s\\(\\)\\-]*" })}
        </div>
        {field("endereco", "Endereço", { maxLength: 255 })}
        {field("emailContato", "Email de contato", { type: "email", maxLength: 255 })}
      </fieldset>
      <fieldset disabled={saving}>
        <legend>Acesso do cliente</legend>
        {field("username", "Usuário", { required: true, minLength: 3, maxLength: 50, pattern: "[A-Za-z0-9._\\-]+", autoComplete: "off" })}
        {field("email", "Email de acesso", { required: true, type: "email", maxLength: 255, autoComplete: "off" })}
      </fieldset>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="platform-modal-actions">
        <button className="secondary-action" type="button" disabled={saving} onClick={onClose}>Cancelar</button>
        <button className="primary-action" type="submit" disabled={saving}>
          {saving && <Loader2 className="spin" size={18} aria-hidden="true" />}
          {saving ? "Salvando…" : "Salvar alterações"}
        </button>
      </div>
    </form>
  </PlatformModal>;
}
