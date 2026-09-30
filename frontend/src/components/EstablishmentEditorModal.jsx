import { useEffect, useState } from "react";
import { ImagePlus, Loader2, X } from "./icons.js";
import { IMAGE_ACCEPT, MAX_IMAGE_BYTES, validateImage } from "../api/uploadValidation.js";
import { MenuHero } from "./MenuHero.jsx";

const emptyForm = {
  nome: "",
  descricao: "",
  endereco: "",
  horarioFuncionamento: "",
  telefone: "",
  emailContato: ""
};

export function EstablishmentEditorModal({ establishment, onClose, onSubmit, onHeroPreview, theme }) {
  const [form, setForm] = useState(emptyForm);
  const [heroFile, setHeroFile] = useState(null);
  const [heroRemoved, setHeroRemoved] = useState(false);
  const [heroPreview, setHeroPreview] = useState(establishment?.heroImageUrl || "");
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
    setError("");
  }, [establishment]);

  useEffect(() => {
    const url = heroFile ? URL.createObjectURL(heroFile) : heroRemoved ? "" : establishment?.heroImageUrl || "";
    setHeroPreview(url);
    onHeroPreview?.(url);
    return () => {
      if (heroFile) URL.revokeObjectURL(url);
      onHeroPreview?.(null);
    };
  }, [heroFile, heroRemoved, establishment?.heroImageUrl, onHeroPreview]);

  function selectHero(event) {
    const selected = event.target.files?.[0];
    if (!selected) return;
    try {
      validateImage(selected);
      setHeroFile(selected);
      setHeroRemoved(false);
      setError("");
    } catch (err) {
      event.target.value = "";
      setError(err.message);
    }
  }

  function updateField(event) {
    const { name, value } = event.target;
    setForm(current => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);
    setError("");

    try {
      if (heroFile) validateImage(heroFile);
      await onSubmit({
        ...form,
        heroImageUrl: heroRemoved ? "" : establishment?.heroImageUrl || ""
      }, heroFile);
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

          <div className="hero-preview" data-theme={theme || establishment?.tema || "artesanal"}>
            <MenuHero name={form.nome || "Seu estabelecimento"} imageUrl={heroPreview} isAdmin />
          </div>
          <label className="file-field">
            <ImagePlus size={20} />
            <span>{heroFile ? heroFile.name : heroPreview ? "Trocar imagem de capa" : "Enviar imagem de capa"}</span>
            <input key={heroRemoved ? "removed" : "cover"} type="file" accept={IMAGE_ACCEPT} aria-label="Imagem de capa" disabled={saving} onChange={selectHero} />
          </label>
          <p className="hero-upload-help">Use uma imagem horizontal. JPEG, PNG, WEBP ou GIF, até {MAX_IMAGE_BYTES / (1024 * 1024)} MiB.</p>
          {(heroPreview || heroFile) && (
            <button type="button" className="secondary-action" disabled={saving} onClick={() => {
              setHeroFile(null);
              setHeroRemoved(true);
            }}>Remover imagem de capa</button>
          )}

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
