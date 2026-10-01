import { processImage } from "./imageProcessing.js";

self.onmessage = async ({ data: { file, profile } }) => {
  try {
    self.postMessage({ file: await processImage(file, profile) });
  } catch (error) {
    self.postMessage({ error: error.message });
  }
};
