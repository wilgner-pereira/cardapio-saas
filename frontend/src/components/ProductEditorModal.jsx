import { useEffect, useState } from "react";
import { ImagePlus, Loader2, X } from "./icons.js";
import { IMAGE_ACCEPT } from "../api/uploadValidation.js";
import { useOptimizedImage } from "./useOptimizedImage.js";

const emptyForm = {
  nome: "",
  descricao: "",
  preco: "",
  categoria: ""
};

export function ProductEditorModal({ product, onClose, onSubmit }) {
  const [form, setForm] = useState(emptyForm);
  const image = useOptimizedImage("product");
  const { file, processing } = image;
  const [preview, setPreview] = useState("");
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
    image.clear();
    setError("");
  }, [product]);

  useEffect(() => {
    if (!file) { setPreview(""); return; }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

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
    if (saving || processing || image.error) return;
    setSaving(true);
    setError("");

    try {
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
            <span>{file ? "Imagem pronta" : product?.imageUrl ? "Trocar imagem" : "Enviar imagem"}</span>
            <input type="file" accept={IMAGE_ACCEPT} aria-label="Imagem do produto" disabled={saving || processing} onChange={event => {
              const selected = event.target.files?.[0];
              event.target.value = "";
              setError("");
              void image.select(selected);
            }} />
          </label>

          {preview && <div className="product-image-preview product-image-wrap"><img src={preview} alt="Prévia da imagem do produto" /></div>}

          {(error || image.error) && <p className="form-error" role="alert">{error || image.error}</p>}

          <button className="primary-action" type="submit" disabled={saving || processing || Boolean(image.error)}>
            {saving || processing ? <Loader2 className="spin" size={18} /> : null}
            {processing ? "Processando imagem…" : "Salvar"}
          </button>
        </form>
      </section>
    </div>
  );
}
