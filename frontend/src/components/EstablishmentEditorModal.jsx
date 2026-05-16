import { useEffect, useState } from "react";
import { ImagePlus, Loader2, X } from "./icons.js";

const emptyForm = {
  nome: "",
  descricao: "",
  endereco: "",
  horarioFuncionamento: "",
  telefone: "",
  emailContato: ""
};

export function EstablishmentEditorModal({ establishment, onClose, onSubmit, onUpload }) {
  const [form, setForm] = useState(emptyForm);
  const [file, setFile] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    setForm({
      nome: establishment?.nome || "",
      descricao: establishment?.descricao || "",
      endereco: establishment?.endereco || "",
      horarioFuncionamento: establishment?.horarioFuncionamento || "",
      telefone: establishment?.telefone || "",
      emailContato: establishment?.emailContato || ""
    });
    setFile(null);
    setError("");
  }, [establishment]);

  function updateField(event) {
    const { name, value } = event.target;
    setForm(current => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);
    setError("");

    try {
      let logoUrl = establishment?.logoUrl || "";
      if (file) {
        logoUrl = await onUpload(file);
      }

      await onSubmit({
        ...form,
        logoUrl
      });
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="product-modal" role="dialog" aria-modal="true" aria-labelledby="establishment-modal-title">
        <button className="modal-close" type="button" onClick={onClose} aria-label="Fechar">
          <X size={20} />
        </button>

        <h2 id="establishment-modal-title">Editar estabelecimento</h2>

        <form onSubmit={handleSubmit} className="product-form">
          <label>
            Nome
            <input name="nome" value={form.nome} onChange={updateField} minLength={3} maxLength={100} required />
          </label>

          <label>
            Descricao
            <textarea name="descricao" value={form.descricao} onChange={updateField} maxLength={500} />
          </label>

          <label>
            Endereco
            <input name="endereco" value={form.endereco} onChange={updateField} maxLength={255} />
          </label>

          <div className="form-grid">
            <label>
              Horario
              <input name="horarioFuncionamento" value={form.horarioFuncionamento} onChange={updateField} maxLength={100} />
            </label>

            <label>
              Telefone
              <input name="telefone" value={form.telefone} onChange={updateField} maxLength={20} />
            </label>
          </div>

          <label>
            Email de contato
            <input name="emailContato" type="email" value={form.emailContato} onChange={updateField} maxLength={255} />
          </label>

          <label className="file-field">
            <ImagePlus size={20} />
            <span>{file ? file.name : establishment?.logoUrl ? "Trocar logo" : "Enviar logo"}</span>
            <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" onChange={event => setFile(event.target.files?.[0] || null)} />
          </label>

          {error && <p className="form-error">{error}</p>}

          <button className="primary-action" type="submit" disabled={saving}>
            {saving ? <Loader2 className="spin" size={18} /> : null}
            Salvar
          </button>
        </form>
      </section>
    </div>
  );
}
