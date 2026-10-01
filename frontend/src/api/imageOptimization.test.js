import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { processImage } from "./imageProcessing.js";
import { optimizeImage } from "./imageOptimization.js";

function source(width = 4032, height = 3024, mime = "image/png") {
  let data;
  if (mime === "image/png") {
    data = new Uint8Array(33);
    data.set([137, 80, 78, 71, 13, 10, 26, 10], 0);
    data.set([73, 72, 68, 82], 12);
    new DataView(data.buffer).setUint32(16, width);
    new DataView(data.buffer).setUint32(20, height);
  } else if (mime === "image/jpeg") {
    data = new Uint8Array([255, 216, 255, 192, 0, 8, 8, height >> 8, height & 255, width >> 8, width & 255, 1, 255, 218]);
  } else {
    data = new Uint8Array(26);
    data.set(Array.from("RIFF", c => c.charCodeAt(0)), 0);
    data.set(Array.from("WEBPVP8L", c => c.charCodeAt(0)), 8);
    const view = new DataView(data.buffer);
    view.setUint32(4, 18, true);
    view.setUint32(16, 5, true);
    data[20] = 47;
    view.setUint32(21, (width - 1) | ((height - 1) << 14), true);
  }
  const file = new File([data], `private-name.${mime === "image/jpeg" ? "jpg" : mime.split("/")[1]}`, { type: mime });
  file.arrayBuffer = async () => data.buffer;
  return file;
}

describe("native image processing", () => {
  let canvas, context, bitmap, encode, createElement;
  beforeEach(() => {
    bitmap = { width: 4032, height: 3024, close: vi.fn() };
    vi.stubGlobal("createImageBitmap", vi.fn(async () => bitmap));
    vi.stubGlobal("Worker", undefined);
    context = { drawImage: vi.fn() };
    encode = vi.fn((callback, type) => callback(new Blob([new Uint8Array(100)], { type })));
    canvas = { width: 0, height: 0, getContext: vi.fn(() => context), toBlob: encode };
    createElement = vi.spyOn(document, "createElement").mockReturnValue(canvas);
  });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it.each(["image/jpeg", "image/png", "image/webp"])("crops %s centrally without stretching and produces one anonymous WebP", async mime => {
    const file = await optimizeImage(source(4032, 3024, mime), "product");
    expect(context.drawImage).toHaveBeenCalledWith(bitmap, 504, 0, 3024, 3024, 0, 0, 512, 512);
    expect(file.name).toBe("optimized.webp");
    expect(file.type).toBe("image/webp");
    expect(file.size).toBeLessThanOrEqual(500 * 1024);
    expect(encode).toHaveBeenCalledExactlyOnceWith(expect.any(Function), "image/webp", 0.85);
    expect(createImageBitmap).toHaveBeenCalledExactlyOnceWith(expect.any(File), { imageOrientation: "from-image" });
    expect(bitmap.close).toHaveBeenCalledTimes(1);
    expect(canvas.width).toBe(1);
    expect(canvas.height).toBe(1);
  });

  it.each([[4000, 3000, 1920, 1440], [3000, 4000, 1440, 1920], [800, 600, 800, 600]])(
    "preserves hero proportion for %sx%s without enlarging", async (width, height, outWidth, outHeight) => {
      bitmap.width = width; bitmap.height = height;
      const result = await processImage(source(width, height), "hero");
      expect(context.drawImage).toHaveBeenCalledWith(bitmap, 0, 0, width, height, 0, 0, outWidth, outHeight);
      expect(result.type).toBe("image/webp");
    });

  it("uses the oriented decode dimensions for a portrait crop", async () => {
    bitmap.width = 3024; bitmap.height = 4032;
    await processImage(source(), "product");
    expect(context.drawImage).toHaveBeenCalledWith(bitmap, 0, 504, 3024, 3024, 0, 0, 512, 512);
  });

  it.each([[8193, 1], [6000, 5000], [0, 100]])("rejects unsafe %sx%s dimensions before decode", async (width, height) => {
    await expect(processImage(source(width, height), "hero")).rejects.toThrow("dimensões");
    expect(createImageBitmap).not.toHaveBeenCalled();
    expect(createElement).not.toHaveBeenCalled();
  });

  it("does not trust MIME or inconsistent WebP dimension headers", async () => {
    const fake = source(); fake.arrayBuffer = async () => new Uint8Array(33).buffer;
    await expect(processImage(fake, "product")).rejects.toThrow("processar");
    expect(createImageBitmap).not.toHaveBeenCalled();
    const data = new Uint8Array(44);
    data.set(Array.from("RIFF", c => c.charCodeAt(0)), 0);
    data.set(Array.from("WEBPVP8X", c => c.charCodeAt(0)), 8);
    const view = new DataView(data.buffer);
    view.setUint32(4, 36, true); view.setUint32(16, 10, true);
    data.set(Array.from("VP8L", c => c.charCodeAt(0)), 30);
    view.setUint32(34, 5, true); data[38] = 47;
    view.setUint32(39, 1999 | (1999 << 14), true);
    const webp = new File([data], "x.webp", { type: "image/webp" });
    webp.arrayBuffer = async () => data.buffer;
    await expect(processImage(webp, "hero")).rejects.toThrow("processar");
    data[20] = 2;
    await expect(processImage(webp, "hero")).rejects.toThrow("sem animação");
    expect(createImageBitmap).not.toHaveBeenCalled();
  });

  it("rejects dimensions returned by decode above the source bound and releases the bitmap", async () => {
    bitmap.width = 20000;
    await expect(processImage(source(), "hero")).rejects.toThrow("dimensões");
    expect(bitmap.close).toHaveBeenCalledTimes(1);
    expect(context.drawImage).not.toHaveBeenCalled();
  });

  it("retries only to 0.78, then rejects rather than degrading indefinitely", async () => {
    encode.mockImplementation((callback, type) => callback(new Blob([new Uint8Array(500 * 1024 + 1)], { type })));
    await expect(processImage(source(), "product")).rejects.toThrow("ainda é muito grande");
    expect(encode.mock.calls.map(call => call[2])).toEqual([0.85, 0.82, 0.78]);
    expect(bitmap.close).toHaveBeenCalledTimes(1);
    expect(canvas.width).toBe(1);
  });

  it("stops after the first acceptable retry", async () => {
    encode.mockImplementationOnce((callback, type) => callback(new Blob([new Uint8Array(500 * 1024 + 1)], { type })));
    await processImage(source(), "product");
    expect(encode.mock.calls.map(call => call[2])).toEqual([0.85, 0.82]);
  });

  it("rejects silent PNG fallback and null encoder results while releasing resources", async () => {
    encode.mockImplementationOnce(callback => callback(new Blob(["png"], { type: "image/png" })));
    await expect(processImage(source(), "hero")).rejects.toThrow("navegador");
    encode.mockImplementationOnce(callback => callback(null));
    await expect(processImage(source(), "hero")).rejects.toThrow("navegador");
    expect(bitmap.close).toHaveBeenCalledTimes(2);
    expect(canvas.width).toBe(1);
  });

  it("falls back to an oriented HTML image and revokes the decode URL", async () => {
    vi.stubGlobal("createImageBitmap", vi.fn().mockRejectedValue(new Error("unsupported")));
    const image = { naturalWidth: 800, naturalHeight: 600, set src(value) { if (value) queueMicrotask(() => this.onload()); } };
    vi.stubGlobal("Image", vi.fn(function () { return image; }));
    URL.createObjectURL = vi.fn(() => "blob:decode"); URL.revokeObjectURL = vi.fn();
    await processImage(source(800, 600), "hero");
    expect(context.drawImage).toHaveBeenCalledWith(image, 0, 0, 800, 600, 0, 0, 800, 600);
    expect(URL.revokeObjectURL).toHaveBeenCalledExactlyOnceWith("blob:decode");
  });

  it("revokes the fallback URL when HTML decode fails", async () => {
    vi.stubGlobal("createImageBitmap", undefined);
    const image = { set src(value) { if (value) queueMicrotask(() => this.onerror()); } };
    vi.stubGlobal("Image", vi.fn(function () { return image; }));
    URL.createObjectURL = vi.fn(() => "blob:failed"); URL.revokeObjectURL = vi.fn();
    await expect(processImage(source(), "hero")).rejects.toThrow("processar");
    expect(URL.revokeObjectURL).toHaveBeenCalledExactlyOnceWith("blob:failed");
  });

  it("uses native canvas fallback if a worker cannot be constructed", async () => {
    vi.stubGlobal("Worker", vi.fn(function () { throw new Error("unavailable"); }));
    vi.stubGlobal("OffscreenCanvas", vi.fn());
    await expect(optimizeImage(source(), "product")).resolves.toMatchObject({ type: "image/webp" });
    expect(bitmap.close).toHaveBeenCalledTimes(1);
  });

  it("terminates worker processing on cancellation", async () => {
    const worker = { postMessage: vi.fn(), terminate: vi.fn() };
    vi.stubGlobal("Worker", vi.fn(function () { return worker; }));
    vi.stubGlobal("OffscreenCanvas", vi.fn());
    const controller = new AbortController();
    const result = optimizeImage(source(), "hero", { signal: controller.signal });
    controller.abort();
    await expect(result).rejects.toMatchObject({ name: "AbortError" });
    expect(worker.terminate).toHaveBeenCalledTimes(1);
    expect(createImageBitmap).not.toHaveBeenCalled();
  });

  it("returns worker WebP and terminates the worker on success or error", async () => {
    const worker = { postMessage: vi.fn(), terminate: vi.fn() };
    vi.stubGlobal("Worker", vi.fn(function () { return worker; }));
    vi.stubGlobal("OffscreenCanvas", vi.fn());
    const result = optimizeImage(source(), "product");
    const optimized = new File(["webp"], "optimized.webp", { type: "image/webp" });
    worker.onmessage({ data: { file: optimized } });
    expect(await result).toBe(optimized);
    expect(worker.terminate).toHaveBeenCalledTimes(1);
    const failed = optimizeImage(source(), "hero");
    worker.onerror();
    await expect(failed).rejects.toThrow("processar");
    expect(worker.terminate).toHaveBeenCalledTimes(2);
  });

  it("terminates a stalled worker at the processing deadline", async () => {
    vi.useFakeTimers();
    try {
      const worker = { postMessage: vi.fn(), terminate: vi.fn() };
      vi.stubGlobal("Worker", vi.fn(function () { return worker; }));
      vi.stubGlobal("OffscreenCanvas", vi.fn());
      const result = expect(optimizeImage(source(), "hero")).rejects.toThrow("processar");
      await vi.advanceTimersByTimeAsync(40_000);
      await result;
      expect(worker.terminate).toHaveBeenCalledTimes(1);
    } finally { vi.useRealTimers(); }
  });
});
