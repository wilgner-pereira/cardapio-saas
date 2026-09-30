import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EstablishmentEditorModal } from "./EstablishmentEditorModal.jsx";

describe("hero image editing", () => {
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => "blob:http://localhost/preview");
    URL.revokeObjectURL = vi.fn();
  });

  it("previews upload immediately, sends file separately and releases local URLs", async () => {
    const onSubmit = vi.fn();
    const onHeroPreview = vi.fn();
    const establishment = { nome: "Pizzaria", logoUrl: "https://example.com/logo.png", heroImageUrl: "" };
    const { container, unmount } = render(<EstablishmentEditorModal establishment={establishment} onSubmit={onSubmit} onHeroPreview={onHeroPreview} />);
    const image = new File(["image"], "cover.jpg", { type: "image/jpeg" });
    fireEvent.change(screen.getByLabelText("Imagem de capa"), { target: { files: [image] } });
    expect(container.querySelector(".hero-cover")).toHaveAttribute("src", "blob:http://localhost/preview");
    expect(onHeroPreview).toHaveBeenCalledWith("blob:http://localhost/preview");
    fireEvent.submit(container.querySelector("form"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ heroImageUrl: "" }), image));
    expect(onSubmit.mock.calls[0][0]).not.toHaveProperty("logoUrl");
    expect(container.querySelectorAll('input[type="file"]')).toHaveLength(1);
    expect(screen.queryByText(/Trocar logo|Enviar logo/)).not.toBeInTheDocument();
    unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:http://localhost/preview");
    expect(onHeroPreview).toHaveBeenLastCalledWith(null);
  });

  it("removes a saved cover locally and saves an empty URL while keeping the logo", async () => {
    const onSubmit = vi.fn();
    const onHeroPreview = vi.fn();
    const establishment = { nome: "Pizzaria", logoUrl: "https://example.com/logo.png", heroImageUrl: "https://example.com/cover.jpg" };
    const { container } = render(<EstablishmentEditorModal establishment={establishment} onSubmit={onSubmit} onHeroPreview={onHeroPreview} />);
    fireEvent.click(screen.getByText("Remover imagem de capa"));
    expect(container.querySelector(".hero-waves")).toBeInTheDocument();
    expect(onHeroPreview).toHaveBeenCalledWith("");
    fireEvent.submit(container.querySelector("form"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ heroImageUrl: "" }), null));
  });

  it("rejects an unsupported cover before changing the preview", () => {
    const { container } = render(<EstablishmentEditorModal establishment={{ nome: "Pizzaria" }} onSubmit={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("Imagem de capa"), { target: { files: [new File(["svg"], "cover.svg", { type: "image/svg+xml" })] } });
    expect(screen.getByText(/Use uma imagem JPEG/)).toBeInTheDocument();
    expect(container.querySelector(".hero-waves")).toBeInTheDocument();
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });
});
