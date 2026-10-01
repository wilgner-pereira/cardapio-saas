import { imageProfile, validateImage, validateOptimizedImage } from "./uploadValidation.js";

export const MAX_SOURCE_DIMENSION = 8192;
export const MAX_SOURCE_PIXELS = 24_000_000;
const PROCESSING_ERROR = "Não foi possível processar essa imagem. Escolha outra foto JPEG, PNG ou WebP.";
const DIMENSION_ERROR = "Essa imagem tem dimensões muito grandes. Use uma foto de até 8192 px por lado e 24 milhões de pixels.";
const ENCODER_ERROR = "Seu navegador não conseguiu gerar WebP. Tente uma versão atual do Chrome, Edge ou Firefox.";
const QUALITIES = [0.85, 0.82, 0.78];

function checkDimensions(width, height) {
  if (!width || !height || width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION || width * height > MAX_SOURCE_PIXELS) {
    throw new Error(DIMENSION_ERROR);
  }
  return { width, height };
}

// Inspect dimensions before decoding. Input is bounded to 10 MiB; this does not replace a decoder.
function inspectSource(buffer, mime) {
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);
  const tag = offset => String.fromCharCode(...bytes.subarray(offset, offset + 4));
  if (mime === "image/png" && view.byteLength >= 33
      && view.getUint32(0) === 0x89504e47 && view.getUint32(4) === 0x0d0a1a0a && tag(12) === "IHDR") {
    return checkDimensions(view.getUint32(16), view.getUint32(20));
  }
  if (mime === "image/jpeg" && view.byteLength >= 4 && view.getUint16(0) === 0xffd8) {
    let dimensions;
    for (let offset = 2; offset + 4 <= bytes.length;) {
      if (bytes[offset++] !== 0xff) break;
      while (bytes[offset] === 0xff) offset++;
      const marker = bytes[offset++];
      if (marker === 0xda || marker === 0xd9) break;
      if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) continue;
      if (offset + 2 > bytes.length) break;
      const size = view.getUint16(offset);
      if (size < 2 || offset + size > bytes.length) break;
      if (marker >= 0xc0 && marker <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(marker)) {
        if (size < 8) break;
        dimensions = checkDimensions(view.getUint16(offset + 5), view.getUint16(offset + 3));
      }
      offset += size;
    }
    if (dimensions) return dimensions;
  }
  if (mime === "image/webp" && bytes.length >= 20 && tag(0) === "RIFF" && tag(8) === "WEBP"
      && view.getUint32(4, true) + 8 === bytes.length) {
    let dimensions;
    let frames = 0;
    for (let offset = 12; offset < bytes.length;) {
      if (offset + 8 > bytes.length) throw new Error(PROCESSING_ERROR);
      const chunk = tag(offset);
      const size = view.getUint32(offset + 4, true);
      const start = offset + 8;
      const end = start + size + (size % 2);
      if (end > bytes.length) throw new Error(PROCESSING_ERROR);
      let current;
      if (chunk === "ANIM" || chunk === "ANMF" || (chunk === "VP8X" && size >= 10 && (bytes[start] & 2))) {
        throw new Error("Use uma imagem sem animação.");
      }
      if (chunk === "VP8X" && size === 10) {
        const uint24 = index => bytes[index] + bytes[index + 1] * 256 + bytes[index + 2] * 65536;
        current = checkDimensions(1 + uint24(start + 4), 1 + uint24(start + 7));
      } else if (chunk === "VP8 " && size >= 10 && bytes[start + 3] === 0x9d && bytes[start + 4] === 0x01 && bytes[start + 5] === 0x2a) {
        const frameTag = bytes[start] | (bytes[start + 1] << 8) | (bytes[start + 2] << 16);
        if ((frameTag & 1) || ((frameTag >>> 1) & 7) > 3 || !(frameTag & 16)
            || !(frameTag >>> 5) || (frameTag >>> 5) > size - 10) throw new Error(PROCESSING_ERROR);
        current = checkDimensions(view.getUint16(start + 6, true) & 0x3fff, view.getUint16(start + 8, true) & 0x3fff);
        frames++;
      } else if (chunk === "VP8L" && size >= 5 && bytes[start] === 0x2f) {
        const bits = view.getUint32(start + 1, true);
        current = checkDimensions(1 + (bits & 0x3fff), 1 + ((bits >>> 14) & 0x3fff));
        frames++;
      }
      if (current) {
        if (dimensions && (current.width !== dimensions.width || current.height !== dimensions.height)) throw new Error(PROCESSING_ERROR);
        dimensions = current;
      }
      offset = end;
    }
    if (dimensions && frames === 1) return dimensions;
  }
  throw new Error(PROCESSING_ERROR);
}

async function decode(file) {
  if (typeof createImageBitmap === "function") {
    try {
      // Apply EXIF once before crop/resize; the output has no original EXIF/name.
      const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
      return { image: bitmap, width: bitmap.width, height: bitmap.height, release: () => bitmap.close() };
    } catch { /* Modern HTMLImageElement provides a safe fallback. */ }
  }
  if (typeof Image === "undefined") throw new Error(PROCESSING_ERROR);
  const url = URL.createObjectURL(file);
  const image = new Image();
  image.decoding = "async";
  try {
    await new Promise((resolve, reject) => {
      image.onload = resolve;
      image.onerror = () => reject(new Error(PROCESSING_ERROR));
      image.src = url;
    });
    return { image, width: image.naturalWidth, height: image.naturalHeight,
      release: () => { image.src = ""; URL.revokeObjectURL(url); } };
  } catch (error) {
    image.src = "";
    URL.revokeObjectURL(url);
    throw error;
  }
}

async function encode(canvas, quality) {
  const blob = typeof canvas.convertToBlob === "function"
    ? await canvas.convertToBlob({ type: "image/webp", quality })
    : await new Promise(resolve => canvas.toBlob(resolve, "image/webp", quality));
  // Unsupported encoders may silently return PNG. Never send that or the original.
  if (!blob || blob.type !== "image/webp") {
    throw new Error(ENCODER_ERROR);
  }
  return blob;
}

export async function processImage(file, profile) {
  validateImage(file);
  const limits = imageProfile(profile);
  let buffer;
  try { buffer = await file.arrayBuffer(); }
  catch { throw new Error(PROCESSING_ERROR); }
  inspectSource(buffer, file.type);
  buffer = null;
  const decoded = await decode(file);
  let canvas;
  try {
    const { width, height } = decoded;
    checkDimensions(width, height);
    const side = Math.min(width, height);
    const scale = Math.min(1, limits.maxDimension / Math.max(width, height));
    const outputWidth = profile === "product" ? 512 : Math.max(1, Math.round(width * scale));
    const outputHeight = profile === "product" ? 512 : Math.max(1, Math.round(height * scale));
    canvas = typeof document === "undefined" ? new OffscreenCanvas(outputWidth, outputHeight) : document.createElement("canvas");
    canvas.width = outputWidth;
    canvas.height = outputHeight;
    const context = canvas.getContext("2d");
    if (!context) throw new Error(PROCESSING_ERROR);
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = "high";
    if (profile === "product") {
      context.drawImage(decoded.image, (width - side) / 2, (height - side) / 2, side, side, 0, 0, outputWidth, outputHeight);
    } else {
      context.drawImage(decoded.image, 0, 0, width, height, 0, 0, outputWidth, outputHeight);
    }
    decoded.release();
    decoded.release = () => {};
    let blob;
    for (const quality of QUALITIES) {
      blob = await encode(canvas, quality);
      if (blob.size <= limits.maxBytes) break;
    }
    const optimized = new File([blob], "optimized.webp", { type: "image/webp" });
    validateOptimizedImage(optimized, profile);
    return optimized;
  } catch (failure) {
    const friendly = [DIMENSION_ERROR, ENCODER_ERROR, "A imagem processada ainda é muito grande. Escolha uma foto mais simples."];
    throw new Error(friendly.includes(failure.message) ? failure.message : PROCESSING_ERROR);
  } finally {
    decoded.release();
    if (canvas) { canvas.width = 1; canvas.height = 1; }
  }
}

