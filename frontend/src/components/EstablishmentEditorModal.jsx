import { useEffect, useRef, useState } from "react";
import { ImagePlus, Loader2, X } from "./icons.js";
import { IMAGE_ACCEPT, MAX_SOURCE_IMAGE_BYTES } from "../api/uploadValidation.js";
import { useOptimizedImage } from "./useOptimizedImage.js";
import { MenuHero } from "./MenuHero.jsx";

const emptyForm = {
  nome: "",
  descricao: "",
  endereco: "",
  horarioFuncionamento: "",
  telefone: "",
  emailContato: "",
  instagramUrl: "",
  whatsapp: ""
};

export function EstablishmentEditorModal({ establishment, onClose, onSubmit, onHeroPreview, theme }) {
  const [form, setForm] = useState(emptyForm);
  const image = useOptimizedImage("hero");
  const { file: heroFile, processing } = image;
  const [heroRemoved, setHeroRemoved] = useState(false);
  const [heroPreview, setHeroPreview] = useState(establishment?.heroImageUrl || "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const modalRef = useRef(null);
  const previouslyFocusedElementRef = useRef(null);

  useEffect(() => {
    previouslyFocusedElementRef.current = document.activeElement;
    const firstInput = modalRef.current?.querySelector('input[name="nome"]');
    if (firstInput) {
      firstInput.focus();
    } else {
      modalRef.current?.focus();
    }

    return () => {
      if (previouslyFocusedElementRef.current?.focus) {
        previouslyFocusedElementRef.current.focus();
      }
    };
  }, []);

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose?.();
        return;
      }

      if (event.key === "Tab" && modalRef.current) {
        const focusableElements = modalRef.current.querySelectorAll(
          'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), [tabindex="0"]'
        );
        if (focusableElements.length === 0) return;

        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];

        if (event.shiftKey && document.activeElement === firstElement) {
          event.preventDefault();
          lastElement.focus();
        } else if (!event.shiftKey && document.activeElement === lastElement) {
          event.preventDefault();
          firstElement.focus();
        }
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  useEffect(() => {
    setForm({
      nome: establishment?.nome || "",
      descricao: establishment?.descricao || "",
      endereco: establishment?.endereco || "",
      horarioFuncionamento: establishment?.horarioFuncionamento || "",
      telefone: establishment?.telefone || "",
      emailContato: establishment?.emailContato || "",
      instagramUrl: establishment?.instagramUrl || "",
      whatsapp: establishment?.whatsapp || ""
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
    event.target.value = "";
    setHeroRemoved(false);
    setError("");
    void image.select(selected);
  }

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
      <section ref={modalRef} className="product-modal" role="dialog" aria-modal="true" aria-labelledby="establishment-modal-title" tabIndex={-1}>
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

          <label>
            Instagram
            <input name="instagramUrl" type="url" value={form.instagramUrl} onChange={updateField} maxLength={2048}
              pattern="https?://.+" placeholder="https://instagram.com/seuperfil" />
          </label>

          <label>
            WhatsApp
            <input name="whatsapp" type="tel" value={form.whatsapp} onChange={updateField} maxLength={30}
              placeholder="+55 (34) 99999-9999" aria-describedby="whatsapp-help" />
          </label>
          <p id="whatsapp-help" className="contact-help">Informe o número com DDI (código do país).</p>

          <div className="hero-preview" data-theme={theme || establishment?.tema || "artesanal"}>
            <MenuHero name={form.nome || "Seu estabelecimento"} imageUrl={heroPreview} isAdmin />
          </div>
          <label className="file-field">
            <ImagePlus size={20} />
            <span>{heroFile ? "Imagem de capa pronta" : heroPreview ? "Trocar imagem de capa" : "Enviar imagem de capa"}</span>
            <input key={heroRemoved ? "removed" : "cover"} type="file" accept={IMAGE_ACCEPT} aria-label="Imagem de capa" disabled={saving || processing} onChange={selectHero} />
          </label>
          <p className="hero-upload-help">Use uma imagem horizontal. JPEG, PNG ou WebP, até {MAX_SOURCE_IMAGE_BYTES / (1024 * 1024)} MiB. A otimização é automática.</p>
          {(heroPreview || heroFile) && (
            <button type="button" className="secondary-action" disabled={saving || processing} onClick={() => {
              image.clear();
              setHeroRemoved(true);
            }}>Remover imagem de capa</button>
          )}

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
