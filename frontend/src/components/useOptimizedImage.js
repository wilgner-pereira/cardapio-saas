import { useEffect, useRef, useState } from "react";
import { optimizeImage } from "../api/imageOptimization.js";

export function useOptimizedImage(profile) {
  const [file, setFile] = useState(null);
  const [processing, setProcessing] = useState(false);
  const [error, setError] = useState("");
  const current = useRef(null);
  useEffect(() => () => current.current?.abort(), []);

  function clear() {
    current.current?.abort();
    setFile(null);
    setProcessing(false);
    setError("");
  }

  async function select(source) {
    clear();
    if (!source) return;
    const controller = new AbortController();
    current.current = controller;
    setProcessing(true);
    try {
      const optimized = await optimizeImage(source, profile, { signal: controller.signal });
      if (!controller.signal.aborted) setFile(optimized);
    } catch (failure) {
      if (!controller.signal.aborted) setError(failure.message);
    } finally {
      if (!controller.signal.aborted) setProcessing(false);
    }
  }

  return { file, processing, error, select, clear };
}
