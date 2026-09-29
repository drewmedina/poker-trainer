import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The Java server runs on 7070. Proxying /api keeps the browser on one origin, so no CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:7070',
    },
  },
});
