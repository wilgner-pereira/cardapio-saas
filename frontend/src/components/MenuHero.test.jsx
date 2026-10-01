import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MenuHero } from "./MenuHero.jsx";

describe("shared menu hero", () => {
  it("uses vector waves and a left title without a logo by default", () => {
    const { container } = render(<MenuHero name="Pizzaria Forno Alto" />);
    expect(screen.getByRole("heading", { name: "Pizzaria Forno Alto" })).toBeInTheDocument();
    expect(screen.getByText("Cardápio digital")).toBeInTheDocument();
    expect(container.querySelector(".hero-waves")).toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
  });

  it("prioritizes the cover with overlay and restores waves on removal or load failure", () => {
    const { container, rerender } = render(<MenuHero name="Menu" imageUrl="https://example.com/cover.jpg" isAdmin />);
    expect(screen.getByText("Editor visual")).toBeInTheDocument();
    expect(container.querySelector(".hero-waves")).toBeNull();
    expect(container.querySelector(".hero-overlay")).toBeInTheDocument();
    expect(container.querySelector(".hero-cover")).toHaveAttribute("fetchpriority", "high");
    expect(container.querySelector(".hero-cover")).not.toHaveAttribute("draggable");
    fireEvent.error(container.querySelector(".hero-cover"));
    expect(container.querySelector(".hero-waves")).toBeInTheDocument();
    rerender(<MenuHero name="Menu" imageUrl="https://example.com/new.jpg" />);
    expect(container.querySelector(".hero-cover")).toHaveAttribute("src", "https://example.com/new.jpg");
    expect(container.querySelector(".hero-cover")).toHaveAttribute("draggable", "false");
    expect(container.querySelector(".hero-cover")).toHaveClass("menu-image-no-drag");
    rerender(<MenuHero name="Menu" imageUrl="" />);
    expect(container.querySelector(".hero-waves")).toBeInTheDocument();
  });

  it.each(["", "not-a-url", "javascript:alert(1)", "data:image/png;base64,abc"])("falls back for invalid or unsupported input %s", imageUrl => {
    const { container } = render(<MenuHero name="Menu" imageUrl={imageUrl} />);
    expect(container.querySelector(".hero-waves")).toBeInTheDocument();
  });

  it("falls back when an image URL with invalid encoding fails to load", () => {
    const imageUrl = "https://example.com/%bad/storage/v1/object/public/images/%";
    const { container } = render(<MenuHero name="Menu" imageUrl={imageUrl} />);
    fireEvent.error(container.querySelector(".hero-cover"));
    expect(container.querySelector(".hero-waves")).toBeInTheDocument();
  });
});
