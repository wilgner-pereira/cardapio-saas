import { useEffect, useState } from "react";
import { ArrowUp } from "./icons.js";

export function ScrollToTopButton({ categoryPositionRef }) {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const marker = categoryPositionRef.current;
    if (!marker || typeof IntersectionObserver !== "function") return;

    const observer = new IntersectionObserver(([entry]) => {
      // Observe the original position: the category bar itself remains sticky.
      // A marker below the viewport has not been passed yet (for example, with a tall hero).
      setVisible(!entry.isIntersecting && entry.boundingClientRect.bottom <= (entry.rootBounds?.top ?? 0));
    }, { threshold: 0 });

    observer.observe(marker);
    return () => observer.disconnect();
  }, [categoryPositionRef]);

  return (
    <button
      type="button"
      className={`floating-action scroll-to-top-button${visible ? " is-visible" : ""}`}
      aria-label="Voltar ao topo"
      aria-hidden={!visible}
      disabled={!visible}
      tabIndex={visible ? 0 : -1}
      onClick={event => {
        // Hiding a focused button during scrolling can interrupt the smooth return.
        event.currentTarget.blur();
        window.scrollTo({ top: 0, behavior: "smooth" });
      }}
    >
      <ArrowUp size={20} aria-hidden="true" />
    </button>
  );
}
