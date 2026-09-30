import { useEffect, useId, useRef } from "react";
import { X } from "../components/icons.js";

export function PlatformModal({ title, description, busy = false, onClose, children }) {
  const titleId = useId();
  const descriptionId = useId();
  const dialog = useRef(null);

  useEffect(() => {
    const trigger = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    dialog.current?.focus();
    return () => {
      document.body.style.overflow = previousOverflow;
      if (trigger?.isConnected) trigger.focus();
    };
  }, []);

  function handleKeyDown(event) {
    if (event.key === "Escape") {
      event.preventDefault();
      if (!busy) onClose();
    }
    if (event.key !== "Tab") return;
    const controls = [...dialog.current.querySelectorAll(
      'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), a[href]'
    )];
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (!first) {
      event.preventDefault();
      return;
    }
    if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialog.current)) {
      event.preventDefault();
      first.focus();
    }
  }

  return <div className="modal-backdrop platform-backdrop" role="presentation" onClick={event => {
    if (event.target === event.currentTarget && !busy) onClose();
  }}>
    <section className="product-modal platform-modal" ref={dialog} role="dialog" aria-modal="true"
      aria-labelledby={titleId} aria-describedby={description ? descriptionId : undefined}
      aria-busy={busy} tabIndex={-1} onKeyDown={handleKeyDown}>
      <button className="modal-close" type="button" disabled={busy} onClick={onClose} aria-label="Fechar">
        <X size={20} aria-hidden="true" />
      </button>
      <h2 id={titleId}>{title}</h2>
      {description && <p className="platform-muted" id={descriptionId}>{description}</p>}
      {children}
    </section>
  </div>;
}
