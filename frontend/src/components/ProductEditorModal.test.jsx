import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProductEditorModal } from "./ProductEditorModal.jsx";
import { optimizeImage } from "../api/imageOptimization.js";

vi.mock("../api/imageOptimization.js", () => ({ optimizeImage: vi.fn() }));

describe("ProductEditorModal accessibility and dismiss behavior", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    URL.createObjectURL = vi.fn(() => "blob:product-preview");
    URL.revokeObjectURL = vi.fn();
    optimizeImage.mockResolvedValue(new File(["webp"], "optimized.webp", { type: "image/webp" }));
  });

  it("previews and submits the same optimized file once, revoking URLs on replacement and unmount", async () => {
    const onSubmit = vi.fn();
    const { container, unmount } = render(<ProductEditorModal onSubmit={onSubmit} />);
    const input = screen.getByLabelText("Imagem do produto");
    const original = new File(["source"], "private.jpg", { type: "image/jpeg" });
    fireEvent.change(input, { target: { files: [original] } });
    await screen.findByAltText("Prévia da imagem do produto");
    const optimized = URL.createObjectURL.mock.calls[0][0];
    expect(optimized.type).toBe("image/webp");
    expect(optimized).not.toBe(original);
    fireEvent.submit(container.querySelector("form"));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.any(Object), optimized));
    expect(optimizeImage).toHaveBeenCalledTimes(1);
    const next = new File(["next"], "optimized.webp", { type: "image/webp" });
    optimizeImage.mockResolvedValueOnce(next);
    fireEvent.change(input, { target: { files: [original] } });
    await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalledWith(next));
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1);
    unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2);
  });

  it("blocks save during processing and aborts an unfinished selection on unmount", async () => {
    let resolve;
    optimizeImage.mockImplementationOnce(() => new Promise(done => { resolve = done; }));
    const onSubmit = vi.fn();
    const { container, unmount } = render(<ProductEditorModal onSubmit={onSubmit} />);
    fireEvent.change(screen.getByLabelText("Imagem do produto"), { target: { files: [new File(["x"], "x.jpg", { type: "image/jpeg" })] } });
    expect(screen.getByRole("button", { name: "Processando imagem…" })).toBeDisabled();
    fireEvent.submit(container.querySelector("form"));
    expect(onSubmit).not.toHaveBeenCalled();
    const signal = optimizeImage.mock.calls.at(-1)[2].signal;
    unmount();
    expect(signal.aborted).toBe(true);
    await act(async () => resolve(new File(["late"], "optimized.webp", { type: "image/webp" })));
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });

  it("ignores an older decode that finishes after another selection", async () => {
    let resolve;
    optimizeImage.mockImplementationOnce(() => new Promise(done => { resolve = done; }));
    render(<ProductEditorModal onSubmit={vi.fn()} />);
    const input = screen.getByLabelText("Imagem do produto");
    const original = new File(["x"], "x.jpg", { type: "image/jpeg" });
    fireEvent.change(input, { target: { files: [original] } });
    const firstSignal = optimizeImage.mock.calls.at(-1)[2].signal;
    fireEvent.change(input, { target: { files: [original] } });
    await screen.findByAltText("Prévia da imagem do produto");
    expect(firstSignal.aborted).toBe(true);
    await act(async () => resolve(new File(["obsolete"], "old.webp", { type: "image/webp" })));
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
  });
  it("dismisses the modal on Escape key press", () => {
    const onClose = vi.fn();
    render(<ProductEditorModal onClose={onClose} onSubmit={vi.fn()} />);

    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("dismisses the modal when clicking the backdrop outside section content", () => {
    const onClose = vi.fn();
    const { container } = render(<ProductEditorModal onClose={onClose} onSubmit={vi.fn()} />);

    const backdrop = container.querySelector(".modal-backdrop");
    fireEvent.click(backdrop);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("does not dismiss when clicking inside the modal content", () => {
    const onClose = vi.fn();
    render(<ProductEditorModal onClose={onClose} onSubmit={vi.fn()} />);

    const title = screen.getByText("Novo item");
    fireEvent.click(title);
    expect(onClose).not.toHaveBeenCalled();
  });

  it("displays existing product image preview when editing a product with an imageUrl", () => {
    const existingProduct = {
      id: "p1",
      nome: "Hambúrguer",
      descricao: "Delicioso hambúrguer artesanal",
      preco: 32.5,
      categoria: "Lanches",
      imageUrl: "https://example.com/burguer.jpg"
    };

    render(<ProductEditorModal product={existingProduct} onClose={vi.fn()} onSubmit={vi.fn()} />);

    const img = screen.getByAltText("Prévia da imagem do produto");
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute("src", "https://example.com/burguer.jpg");
  });

  it("renders form errors with role alert", async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error("Preço inválido"));
    const { container } = render(<ProductEditorModal onClose={vi.fn()} onSubmit={onSubmit} />);

    fireEvent.change(container.querySelector('input[name="nome"]'), { target: { value: "Suco" } });
    fireEvent.change(container.querySelector('textarea[name="descricao"]'), { target: { value: "Suco natural" } });
    fireEvent.change(container.querySelector('input[name="preco"]'), { target: { value: "10" } });
    fireEvent.change(container.querySelector('input[name="categoria"]'), { target: { value: "Bebidas" } });

    fireEvent.submit(container.querySelector("form"));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Preço inválido");
  });
});
