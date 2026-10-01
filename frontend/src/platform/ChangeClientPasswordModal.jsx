import { useRef, useState } from "react";
import { api } from "../api/client.js";
import { Loader2 } from "../components/icons.js";
import { PlatformModal } from "./PlatformModal.jsx";

export function ChangeClientPasswordModal({ client, onClose, onChanged }) {
  const [saving, setSaving] = useState(false);
  const submitting = useRef(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting.current) return;
    const form = event.currentTarget;
    const password = form.elements.password.value;
    if (password !== form.elements.confirmPassword.value) {
      setFieldErrors({ confirmPassword: "As senhas não coincidem" });
      return;
    }
    submitting.current = true;
    setSaving(true);
    setError("");
    setFieldErrors({});
    try {
      await api.updatePlatformClientPassword(client.id, password);
      form.reset();
      onChanged();
    } catch (cause) {
      setError(cause.message);
      setFieldErrors(cause.fieldErrors || {});
    } finally {
      submitting.current = false;
      setSaving(false);
    }
  }

  return <PlatformModal title="Alterar senha" description={`Redefinir o acesso de ${client.nome}. As sessões anteriores serão encerradas.`}
    busy={saving} onClose={onClose}>
    <form className="product-form platform-form" onSubmit={handleSubmit}>
      <fieldset disabled={saving}>
        <div className="platform-field">
          <label htmlFor="platform-new-password">Nova senha</label>
          <input id="platform-new-password" name="password" type="password" required minLength={8} maxLength={100}
            autoComplete="new-password" aria-invalid={Boolean(fieldErrors.password)} />
          {fieldErrors.password && <span className="form-error">{fieldErrors.password}</span>}
        </div>
        <div className="platform-field">
          <label htmlFor="platform-confirm-password">Confirmar nova senha</label>
          <input id="platform-confirm-password" name="confirmPassword" type="password" required minLength={8} maxLength={100}
            autoComplete="new-password" aria-invalid={Boolean(fieldErrors.confirmPassword)} />
          {fieldErrors.confirmPassword && <span className="form-error">{fieldErrors.confirmPassword}</span>}
        </div>
      </fieldset>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="platform-modal-actions">
        <button className="secondary-action" type="button" disabled={saving} onClick={onClose}>Cancelar</button>
        <button className="primary-action" type="submit" disabled={saving}>
          {saving && <Loader2 className="spin" size={18} aria-hidden="true" />}
          {saving ? "Alterando…" : "Alterar senha"}
        </button>
      </div>
    </form>
  </PlatformModal>;
}
