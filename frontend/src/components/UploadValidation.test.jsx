import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ProductEditorModal } from "./ProductEditorModal.jsx";
import { EstablishmentEditorModal } from "./EstablishmentEditorModal.jsx";
import { MAX_IMAGE_BYTES, validateImage } from "../api/uploadValidation.js";
import { api } from "../api/client.js";

describe("image validation before HTTP", () => {
  it.each([ProductEditorModal, EstablishmentEditorModal])("blocks oversized files in each editor", async Component => {
    const http = vi.fn();
    vi.stubGlobal("fetch", http);
    const onSubmit = vi.fn(async (_, file) => api.uploadImage(file));
    const { container } = render(<Component onSubmit={onSubmit} onClose={() => {}} />);
    const file = new File([new Uint8Array(MAX_IMAGE_BYTES + 1)], "large.png", { type: "image/png" });
    fireEvent.change(container.querySelector('input[type="file"]'), { target: { files: [file] } });
    // The cover picker validates at selection time; an invalid file must never reach submit.
    if (Component === ProductEditorModal) fireEvent.submit(container.querySelector("form"));
    await waitFor(() => expect(screen.getByText(/no máximo 5 MiB/)).toBeInTheDocument());
    expect(onSubmit).not.toHaveBeenCalled();
    expect(http).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });

  it("also blocks direct API upload calls and rejects MIME/extension mismatch", async () => {
    const http = vi.fn(); vi.stubGlobal("fetch", http);
    try {
      await expect(api.uploadImage(new File([new Uint8Array(MAX_IMAGE_BYTES + 1)], "large.png", { type: "image/png" })))
        .rejects.toThrow("5 MiB");
      await expect(api.uploadImage(new File(["x"], "script.svg", { type: "image/svg+xml" }))).rejects.toThrow("JPEG");
      expect(() => validateImage(new File(["x"], "image.exe", { type: "image/png" }))).toThrow("extensão");
      expect(http).not.toHaveBeenCalled();
    } finally { vi.unstubAllGlobals(); }
  });

  it("accepts the boundary size and supported image types", () => {
    expect(() => validateImage(new File([new Uint8Array(MAX_IMAGE_BYTES)], "image.PNG", { type: "image/png" }))).not.toThrow();
    for (const [name, type] of [["x.jpg", "image/jpeg"], ["x.webp", "image/webp"], ["x.gif", "image/gif"]]) {
      expect(() => validateImage(new File(["x"], name, { type }))).not.toThrow();
    }
  });
});
