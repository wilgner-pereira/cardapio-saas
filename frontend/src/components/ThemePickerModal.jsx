import { useEffect, useRef, useState } from "react";
import { Check, Loader2, X } from "./icons.js";

export const THEMES = [
  {
    id: "artesanal",
    name: "Claro",
    category: "Pratos Especiais",
    dish: "Costela ao Molho Barbecue",
    description: "Acompanha batatas rústicas e farofa",
    price: "R$ 49,90",
    swatches: ["#FFFFFF", "#111111", "#E8EDF2"],
    previewStyles: {
      boxBg: "#F7F7F7",
      boxBorder: "#E1E3E6",
      sectionTitle: "#111111",
      cardBg: "#FFFFFF",
      cardBorder: "#89939F",
      dishName: "#111111",
      dishDesc: "#5F6368",
      price: "#111111"
    }
  },
  {
    id: "grafite",
    name: "Escuro",
    category: "Pratos Especiais",
    dish: "Costela ao Molho Barbecue",
    description: "Acompanha batatas rústicas e farofa",
    price: "R$ 49,90",
    swatches: ["#181B19", "#C89245", "#F3EDE2"],
    previewStyles: {
      boxBg: "#181B19",
      boxBorder: "rgba(200, 146, 69, 0.22)",
      sectionTitle: "#C89245",
      cardBg: "#252A26",
      cardBorder: "rgba(200, 146, 69, 0.18)",
      dishName: "#F3EDE2",
      dishDesc: "#BDB5A8",
      price: "#C89245"
    }
  }
];

function normalizeSelectableTheme(themeId) {
  return THEMES.some(theme => theme.id === themeId) ? themeId : "artesanal";
}

export function ThemePickerModal({
  isOpen = true,
  currentTheme = "artesanal",
  onSelectPreview,
  onClose,
  onSubmit,
  triggerRef
}) {
  const [selectedTheme, setSelectedTheme] = useState(() => normalizeSelectableTheme(currentTheme));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const modalRef = useRef(null);
  const previouslyFocusedElementRef = useRef(null);

  useEffect(() => {
    previouslyFocusedElementRef.current = triggerRef?.current || document.activeElement;
    setSelectedTheme(normalizeSelectableTheme(currentTheme));
    setError("");

    // Foco no card atualmente selecionado ou no modal
    const timer = setTimeout(() => {
      const activeCard = modalRef.current?.querySelector(".theme-card.active");
      if (activeCard) {
        activeCard.focus();
      } else {
        modalRef.current?.focus();
      }
    }, 50);

    return () => {
      clearTimeout(timer);
      if (previouslyFocusedElementRef.current?.focus) {
        previouslyFocusedElementRef.current.focus();
      }
    };
  }, [currentTheme, triggerRef]);

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === "Escape") {
        event.preventDefault();
        handleClose();
        return;
      }

      if (event.key === "Tab" && modalRef.current) {
        const focusableElements = modalRef.current.querySelectorAll(
          'button:not([disabled]), [tabindex="0"]'
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
  }, [selectedTheme]);

  function handleSelect(themeId) {
    setSelectedTheme(themeId);
    setError("");
    if (onSelectPreview) {
      onSelectPreview(themeId);
    }
  }

  function handleClose() {
    if (onClose) {
      onClose();
    }
  }

  async function handleSave() {
    setSaving(true);
    setError("");

    try {
      if (onSubmit) {
        await onSubmit(selectedTheme);
      }
    } catch (err) {
      setError(err?.message || "Não foi possível salvar o tema selecionado.");
    } finally {
      setSaving(false);
    }
  }

  if (!isOpen) {
    return null;
  }

  return (
    <div className="modal-backdrop" role="presentation" onClick={(e) => {
      if (e.target === e.currentTarget) {
        handleClose();
      }
    }}>
      <section
        ref={modalRef}
        className="product-modal theme-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="theme-modal-title"
        aria-describedby="theme-modal-description"
        tabIndex={-1}
      >
        <button
          className="modal-close"
          type="button"
          onClick={handleClose}
          aria-label="Fechar"
        >
          <X size={20} />
        </button>

        <div className="theme-modal-header">
          <h2 id="theme-modal-title">Aparência do cardápio</h2>
          <p id="theme-modal-description" className="theme-modal-subtitle">
            Escolha um estilo para o seu cardápio. As cores foram combinadas para manter boa leitura e consistência visual.
          </p>
        </div>

        <div className="theme-grid" role="radiogroup" aria-label="Temas disponíveis">
          {THEMES.map((theme) => {
            const isActive = selectedTheme === theme.id;
            return (
              <button
                key={theme.id}
                type="button"
                role="radio"
                aria-checked={isActive}
                tabIndex={0}
                className={`theme-card ${isActive ? "active" : ""}`}
                onClick={() => handleSelect(theme.id)}
                aria-label={`Tema ${theme.name}${isActive ? ", selecionado" : ""}`}
              >
                <div className="theme-card-header">
                  <span className="theme-card-title">{theme.name}</span>
                  {isActive && (
                    <span className="theme-card-badge">
                      <Check size={14} strokeWidth={3} />
                      <span>Selecionado</span>
                    </span>
                  )}
                </div>

                <div className="theme-swatches" aria-hidden="true">
                  {theme.swatches.map((color, index) => (
                    <span
                      key={index}
                      className="theme-swatch"
                      style={{ backgroundColor: color }}
                    />
                  ))}
                </div>

                <div
                  className="theme-preview-box"
                  style={{
                    backgroundColor: theme.previewStyles.boxBg,
                    border: `1px solid ${theme.previewStyles.boxBorder}`
                  }}
                  aria-hidden="true"
                >
                  <span
                    className="theme-preview-section-title"
                    style={{ color: theme.previewStyles.sectionTitle }}
                  >
                    {theme.category}
                  </span>

                  <div
                    className="theme-preview-item"
                    style={{
                      backgroundColor: theme.previewStyles.cardBg,
                      border: `1px solid ${theme.previewStyles.cardBorder}`
                    }}
                  >
                    <span
                      className="theme-preview-item-name"
                      style={{ color: theme.previewStyles.dishName }}
                    >
                      {theme.dish}
                    </span>
                    <span
                      className="theme-preview-item-desc"
                      style={{ color: theme.previewStyles.dishDesc }}
                    >
                      {theme.description}
                    </span>
                    <span
                      className="theme-preview-item-price"
                      style={{ color: theme.previewStyles.price }}
                    >
                      {theme.price}
                    </span>
                  </div>
                </div>
              </button>
            );
          })}
        </div>

        {error && <p className="form-error" role="alert">{error}</p>}

        <div className="theme-modal-actions">
          <button
            className="secondary-action"
            type="button"
            onClick={handleClose}
            disabled={saving}
          >
            Cancelar
          </button>
          <button
            className="primary-action"
            type="button"
            onClick={handleSave}
            disabled={saving}
          >
            {saving ? <Loader2 className="spin" size={18} /> : null}
            Salvar tema
          </button>
        </div>
      </section>
    </div>
  );
}
