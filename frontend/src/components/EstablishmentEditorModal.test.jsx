import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EstablishmentEditorModal } from "./EstablishmentEditorModal.jsx";
import { optimizeImage } from "../api/imageOptimization.js";
import { validateImage } from "../api/uploadValidation.js";

vi.mock("../api/imageOptimization.js", () => ({ optimizeImage: vi.fn() }));

describe("hero image editing", () => {
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => "blob:http://localhost/preview");
    URL.revokeObjectURL = vi.fn();
    optimizeImage.mockImplementation(async file => {
      validateImage(file);
      return new File(["webp"], "optimized.webp", { type: "image/webp" });
    });
  });

  it("loads and submits edited social contacts through the existing payload", async () => {
    const onSubmit = vi.fn();
    const establishment = { nome: "Pizzaria", instagramUrl: "https://instagram.com/old", whatsapp: "+55 (34) 99999-9999" };
    const { container } = render(<EstablishmentEditorModal establishment={establishment} onSubmit={onSubmit} />);
    expect(screen.getByLabelText("Instagram")).toHaveValue(establishment.instagramUrl);
    expect(screen.getByLabelText("WhatsApp")).toHaveValue(establishment.whatsapp);
    fireEvent.change(screen.getByLabelText("Instagram"), { target: { value: "https://instagram.com/new" } });
    fireEvent.change(screen.getByLabelText("WhatsApp"), { target: { value: "+1 (202) 555-0123" } });
    fireEvent.submit(container.querySelector("form"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      instagramUrl: "https://instagram.com/new", whatsapp: "+1 (202) 555-0123"
    }), null));
    expect(onSubmit).toHaveBeenCalledTimes(1);
  });

  it("sends empty social fields to remove saved contacts and accepts absent values", async () => {
    const onSubmit = vi.fn();
    const { container, rerender } = render(<EstablishmentEditorModal establishment={{
      nome: "Pizzaria", instagramUrl: "https://instagram.com/old", whatsapp: "+55 34999999999"
    }} onSubmit={onSubmit} />);
    fireEvent.change(screen.getByLabelText("Instagram"), { target: { value: "" } });
    fireEvent.change(screen.getByLabelText("WhatsApp"), { target: { value: "" } });
    fireEvent.submit(container.querySelector("form"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ instagramUrl: "", whatsapp: "" }), null));
    rerender(<EstablishmentEditorModal establishment={{ nome: "Pizzaria" }} onSubmit={onSubmit} />);
    expect(screen.getByLabelText("Instagram")).toHaveValue("");
    expect(screen.getByLabelText("WhatsApp")).toHaveValue("");
  });

  it("previews upload immediately, sends file separately and releases local URLs", async () => {
    const onSubmit = vi.fn();
    const onHeroPreview = vi.fn();
    const establishment = { nome: "Pizzaria", logoUrl: "https://example.com/logo.png", heroImageUrl: "" };
    const { container, unmount } = render(<EstablishmentEditorModal establishment={establishment} onSubmit={onSubmit} onHeroPreview={onHeroPreview} />);
    const image = new File(["image"], "cover.jpg", { type: "image/jpeg" });
    fireEvent.change(screen.getByLabelText("Imagem de capa"), { target: { files: [image] } });
    await waitFor(() => expect(container.querySelector(".hero-cover")).toHaveAttribute("src", "blob:http://localhost/preview"));
    expect(onHeroPreview).toHaveBeenCalledWith("blob:http://localhost/preview");
    fireEvent.submit(container.querySelector("form"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ heroImageUrl: "" }), expect.objectContaining({ name: "optimized.webp", type: "image/webp" })));
    expect(onSubmit.mock.calls[0][1]).not.toBe(image);
    expect(optimizeImage).toHaveBeenCalledTimes(1);
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

  it("rejects an unsupported cover before changing the preview", async () => {
    const { container } = render(<EstablishmentEditorModal establishment={{ nome: "Pizzaria" }} onSubmit={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("Imagem de capa"), { target: { files: [new File(["svg"], "cover.svg", { type: "image/svg+xml" })] } });
    expect(await screen.findByRole("alert")).toHaveTextContent(/Use uma imagem JPEG/);
    expect(container.querySelector(".hero-waves")).toBeInTheDocument();
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });

  it("closes when pressing Escape or clicking backdrop", () => {
    const onClose = vi.fn();
    const { container } = render(<EstablishmentEditorModal establishment={{ nome: "Pizzaria" }} onClose={onClose} />);

    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);

    const backdrop = container.querySelector(".modal-backdrop");
    fireEvent.click(backdrop);
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
