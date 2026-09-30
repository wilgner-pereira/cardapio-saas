import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { cspPlugin } from './csp.mjs';

export default defineConfig(({ mode }) => ({
  plugins: [react(), cspPlugin(loadEnv(mode, process.cwd(), 'VITE_').VITE_API_BASE_URL)],
  test: {
    environment: 'jsdom',
    setupFiles: './setupTests.js',
    globals: true
  }
}));
