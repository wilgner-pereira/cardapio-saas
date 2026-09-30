// UX limit matches the backend default (5 MiB). Server validation remains authoritative.
export const MAX_IMAGE_BYTES = Number(import.meta.env.VITE_MAX_IMAGE_BYTES || 5 * 1024 * 1024);
if (!Number.isSafeInteger(MAX_IMAGE_BYTES) || MAX_IMAGE_BYTES <= 0) {
  throw new Error("VITE_MAX_IMAGE_BYTES deve ser um inteiro positivo");
}
export const IMAGE_ACCEPT = "image/png,image/jpeg,image/webp,image/gif";
const extensions = {
  "image/png": ["png"], "image/jpeg": ["jpg", "jpeg"],
  "image/webp": ["webp"], "image/gif": ["gif"]
};

export function validateImage(file) {
  if (!file || file.size === 0) throw new Error("Selecione uma imagem não vazia.");
  if (file.size > MAX_IMAGE_BYTES) throw new Error(`A imagem deve ter no máximo ${MAX_IMAGE_BYTES / (1024 * 1024)} MiB.`);
  const extension = file.name.split(".").pop().toLowerCase();
  if (!extensions[file.type]?.includes(extension)) {
    throw new Error("Use uma imagem JPEG, PNG, WEBP ou GIF com extensão correspondente.");
  }
}
