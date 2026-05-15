import { useEffect, useState } from "react";
import { ImagePlus, Loader2, X } from "./icons.js";

const emptyForm = {
  nome: "",
  descricao: "",
  preco: "",
  categoria: "",
  imageUrl: ""
};

export function ProductEditorModal({ product, onClose, onSubmit, onUpload }) {
  const [form, setForm] = useState(emptyForm);
  const [file, setFile] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (product) {
      setForm({
        nome: product.nome || "",
        descricao: product.descricao || "",
        preco: product.preco ?? "",
        categoria: product.categoria || "",
        imageUrl: product.imageUrl || ""
      });
    } else {
      setForm(emptyForm);
    }
    setFile(null);
    setError("");
  }, [product]);

  function updateField(event) {
    const { name, value } = event.target;
    setForm(current => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);
    setError("");

    try {
      let imageUrl = form.imageUrl;
      if (file) {
        imageUrl = await onUpload(file);
      }

      await onSubmit({
        ...form,
        preco: Number(String(form.preco).replace(",", ".")),
        imageUrl
      });
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="product-modal" role="dialog" aria-modal="true" aria-labelledby="product-modal-title">
        <button className="modal-close" type="button" onClick={onClose} aria-label="Fechar">
          <X size={20} />
        </button>

        <h2 id="product-modal-title">{product ? "Editar item" : "Novo item"}</h2>

        <form onSubmit={handleSubmit} className="product-form">
          <label>
            Nome
            <input name="nome" value={form.nome} onChange={updateField} minLength={2} maxLength={60} required />
          </label>

          <label>
            Descricao
            <textarea name="descricao" value={form.descricao} onChange={updateField} minLength={2} maxLength={300} required />
          </label>

          <div className="form-grid">
            <label>
              Preco
              <input name="preco" value={form.preco} onChange={updateField} inputMode="decimal" required />
            </label>

            <label>
              Categoria
              <input name="categoria" value={form.categoria} onChange={updateField} maxLength={50} required />
            </label>
          </div>

          <label>
            URL da imagem
            <input name="imageUrl" value={form.imageUrl} onChange={updateField} placeholder="https://..." />
          </label>

          <label className="file-field">
            <ImagePlus size={20} />
            <span>{file ? file.name : "Enviar imagem"}</span>
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
