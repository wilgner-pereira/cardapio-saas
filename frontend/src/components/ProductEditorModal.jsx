import { useEffect, useState } from "react";
import { ImagePlus, Loader2, X } from "./icons.js";
import { IMAGE_ACCEPT, validateImage } from "../api/uploadValidation.js";

const emptyForm = {
  nome: "",
  descricao: "",
  preco: "",
  categoria: ""
};

export function ProductEditorModal({ product, onClose, onSubmit }) {
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
        categoria: product.categoria || ""
      });
    } else {
      setForm(emptyForm);
    }
    setFile(null);
    setError("");
  }, [product]);

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose?.();
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  function updateField(event) {
    const { name, value } = event.target;
    setForm(current => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);
    setError("");

    try {
      if (file) validateImage(file);
      await onSubmit({
        ...form,
        preco: Number(String(form.preco).replace(",", ".")),
        imageUrl: product?.imageUrl || ""
      }, file);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div
      className="modal-backdrop"
      role="presentation"
      onClick={event => {
        if (event.target === event.currentTarget) onClose?.();
      }}
    >
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

          <label className="file-field">
            <ImagePlus size={20} />
            <span>{file ? file.name : product?.imageUrl ? "Trocar imagem" : "Enviar imagem"}</span>
            <input type="file" accept={IMAGE_ACCEPT} onChange={event => setFile(event.target.files?.[0] || null)} />
          </label>

          {error && <p className="form-error" role="alert">{error}</p>}

          <button className="primary-action" type="submit" disabled={saving}>
            {saving ? <Loader2 className="spin" size={18} /> : null}
            Salvar
          </button>
        </form>
      </section>
    </div>
  );
}
