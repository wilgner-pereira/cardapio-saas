import { useState } from "react";
import { api } from "../api/client.js";

function resolveCover(value) {
  if (!value || typeof value !== "string") return "";
  try {
    const resolved = api.resolveImageUrl(value.trim());
    const url = new URL(resolved);
    return ["https:", "http:", "blob:"].includes(url.protocol) ? resolved : "";
  } catch {
    return "";
  }
}

export function MenuHero({ name, imageUrl, isAdmin = false, children }) {
  const source = resolveCover(imageUrl);
  const [failedSource, setFailedSource] = useState("");
  const hasImage = Boolean(source && source !== failedSource);

  return (
    <section className={`hero${hasImage ? " hero-with-image" : ""}${isAdmin ? " hero-editor" : ""}${name.length > 45 ? " hero-long-name" : ""}`} aria-label="Topo do cardápio">
      {hasImage ? (
        <>
          <img key={source} className={`hero-cover${isAdmin ? "" : " menu-image-no-drag"}`} src={source} alt="" fetchpriority="high"
            draggable={isAdmin ? undefined : false}
            onError={() => setFailedSource(source)} />
          <div className="hero-overlay" aria-hidden="true" />
        </>
      ) : (
        <svg className="hero-waves" viewBox="0 0 1000 320" preserveAspectRatio="none" aria-hidden="true" focusable="false">
          <path className="hero-wave-back" d="M0 190 C210 390 490 320 725 155 C850 65 890 38 1000 45 L1000 320 L0 320Z" />
          <path className="hero-wave-front" d="M0 265 C260 365 510 330 720 245 C845 185 890 135 1000 195 L1000 320 L0 320Z" />
          <path className="hero-wave-line" d="M0 190 C210 390 490 320 725 155 C850 65 890 38 1000 45 M0 265 C260 365 510 330 720 245 C845 185 890 135 1000 195" />
        </svg>
      )}
      {children}
      <div className="hero-content">
        <div className="hero-title">
          <span>{isAdmin ? "Editor visual" : "Cardápio digital"}</span>
          <h1>{name}</h1>
        </div>
      </div>
    </section>
  );
}
