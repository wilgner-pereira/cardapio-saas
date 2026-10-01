import { imageProfile, validateImage, validateOptimizedImage } from "./uploadValidation.js";
import { processImage } from "./imageProcessing.js";
const PROCESSING_ERROR = "Não foi possível processar essa imagem.";
export async function optimizeImage(file, profile, { signal } = {}) {
  validateImage(file);
  imageProfile(profile);
  if (signal?.aborted) throw new DOMException("Processamento cancelado", "AbortError");
  if (typeof Worker !== "function" || typeof OffscreenCanvas !== "function" || typeof createImageBitmap !== "function") {
    return processImage(file, profile);
  }
  let worker;
  try { worker = new Worker(new URL("./imageOptimization.worker.js", import.meta.url), { type: "module" }); }
  catch { return processImage(file, profile); }
  return new Promise((resolve, reject) => {
    const finish = (error, result) => {
      clearTimeout(timeout);
      signal?.removeEventListener("abort", abort);
      worker.terminate();
      if (error) reject(error); else resolve(result);
    };
    const abort = () => finish(new DOMException("Processamento cancelado", "AbortError"));
    const timeout = setTimeout(() => finish(new Error(PROCESSING_ERROR)), 40_000);
    signal?.addEventListener("abort", abort, { once: true });
    worker.onmessage = ({ data }) => {
      try {
        if (data.error) throw new Error(data.error);
        validateOptimizedImage(data.file, profile);
        finish(null, data.file);
      } catch (error) { finish(error); }
    };
    worker.onerror = () => finish(new Error(PROCESSING_ERROR));
    try { worker.postMessage({ file, profile }); }
    catch { finish(new Error(PROCESSING_ERROR)); }
  });
}
