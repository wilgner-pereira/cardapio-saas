import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ProductEditorModal } from "./ProductEditorModal.jsx";

describe("ProductEditorModal accessibility and dismiss behavior", () => {
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
