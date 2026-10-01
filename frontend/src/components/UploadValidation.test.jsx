import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ProductEditorModal } from "./ProductEditorModal.jsx";
import { EstablishmentEditorModal } from "./EstablishmentEditorModal.jsx";
import { MAX_SOURCE_IMAGE_BYTES, validateImage, validateOptimizedImage, IMAGE_ACCEPT } from "../api/uploadValidation.js";
import { api } from "../api/client.js";

describe("image validation before HTTP", () => {
  it.each([ProductEditorModal, EstablishmentEditorModal])("blocks oversized files in each editor", async Component => {
    const http = vi.fn();
    vi.stubGlobal("fetch", http);
    const onSubmit = vi.fn(async (_, file) => api.uploadImage(file, "product"));
    const { container } = render(<Component onSubmit={onSubmit} onClose={() => {}} />);
    const file = new File([new Uint8Array(MAX_SOURCE_IMAGE_BYTES + 1)], "large.png", { type: "image/png" });
    fireEvent.change(container.querySelector('input[type="file"]'), { target: { files: [file] } });
    // The cover picker validates at selection time; an invalid file must never reach submit.
    if (Component === ProductEditorModal) fireEvent.submit(container.querySelector("form"));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent(/até 10 MiB/));
    expect(onSubmit).not.toHaveBeenCalled();
    expect(http).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it("also blocks direct API upload calls and rejects MIME/extension mismatch", async () => {
    const http = vi.fn(); vi.stubGlobal("fetch", http);
    try {
      await expect(api.uploadImage(new File([new Uint8Array(500 * 1024 + 1)], "optimized.webp", { type: "image/webp" }), "product"))
        .rejects.toThrow("muito grande");
      await expect(api.uploadImage(new File(["x"], "script.svg", { type: "image/svg+xml" }), "hero")).rejects.toThrow("WebP");
      expect(() => validateImage(new File(["x"], "image.exe", { type: "image/png" }))).toThrow("extensão");
      expect(http).not.toHaveBeenCalled();
    } finally { vi.unstubAllGlobals(); }
  });

  it("accepts the boundary size and supported image types", () => {
    expect(() => validateImage(new File([new Uint8Array(MAX_SOURCE_IMAGE_BYTES)], "image.PNG", { type: "image/png" }))).not.toThrow();
    for (const [name, type] of [["x.jpg", "image/jpeg"], ["x.jpeg", "image/jpeg"], ["x.webp", "image/webp"]]) {
      expect(() => validateImage(new File(["x"], name, { type }))).not.toThrow();
    }
  });

  it.each([["x.gif", "image/gif"], ["x.heic", "image/heic"], ["x.svg", "image/svg+xml"], ["x.png", "image/jpeg"]])(
    "rejects unsupported or mismatched %s", (name, type) => {
      expect(() => validateImage(new File(["x"], name, { type }))).toThrow("JPEG, PNG ou WebP");
    });
  it("rejects empty sources and enforces output profile limits and explicit types", () => {
    expect(() => validateImage(new File([], "x.png", { type: "image/png" }))).toThrow("não vazia");
    expect(IMAGE_ACCEPT).not.toContain("gif");
    const file = new File([new Uint8Array(600 * 1024)], "optimized.webp", { type: "image/webp" });
    expect(() => validateOptimizedImage(file, "hero")).not.toThrow();
    expect(() => validateOptimizedImage(file, "product")).toThrow("muito grande");
    expect(() => validateOptimizedImage(file, "logo")).toThrow("Tipo");
    expect(() => validateOptimizedImage(file)).toThrow("Tipo");
  });
});
