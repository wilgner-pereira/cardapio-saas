// Original files stay in the browser. Only the smaller WebP is sent to the server.
export const MAX_SOURCE_IMAGE_BYTES = Number(import.meta.env.VITE_MAX_SOURCE_IMAGE_BYTES || 10 * 1024 * 1024);
if (!Number.isSafeInteger(MAX_SOURCE_IMAGE_BYTES) || MAX_SOURCE_IMAGE_BYTES <= 0 || MAX_SOURCE_IMAGE_BYTES > 10 * 1024 * 1024) {
  throw new Error("VITE_MAX_SOURCE_IMAGE_BYTES deve ser um inteiro positivo de até 10485760");
}
export const IMAGE_ACCEPT = "image/png,image/jpeg,image/webp";
export const IMAGE_PROFILES = Object.freeze({
  product: Object.freeze({ maxDimension: 512, maxBytes: 500 * 1024 }),
  hero: Object.freeze({ maxDimension: 1920, maxBytes: 1024 * 1024 })
});
const extensions = { "image/png": ["png"], "image/jpeg": ["jpg", "jpeg"], "image/webp": ["webp"] };

export function imageProfile(profile) {
  if (!Object.hasOwn(IMAGE_PROFILES, profile)) throw new Error("Tipo de imagem inválido.");
  return IMAGE_PROFILES[profile];
}

export function validateImage(file) {
  if (!file || file.size === 0) throw new Error("Selecione uma imagem não vazia.");
  if (file.size > MAX_SOURCE_IMAGE_BYTES) throw new Error(`Essa imagem é muito grande. Escolha uma foto de até ${MAX_SOURCE_IMAGE_BYTES / (1024 * 1024)} MiB.`);
  const extension = file.name?.split(".").pop().toLowerCase();
  if (!extensions[file.type]?.includes(extension)) {
    throw new Error("Use uma imagem JPEG, PNG ou WebP com extensão correspondente. HEIC, HEIF e GIF não são aceitos.");
  }
}

export function validateOptimizedImage(file, profile) {
  const limits = imageProfile(profile);
  if (!file || file.size === 0 || file.type !== "image/webp" || !file.name?.endsWith(".webp")) {
    throw new Error("Não foi possível processar essa imagem como WebP.");
  }
  if (file.size > limits.maxBytes) throw new Error("A imagem processada ainda é muito grande. Escolha uma foto mais simples.");
}
