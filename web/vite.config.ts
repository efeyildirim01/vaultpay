import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the Vite server forwards /api to the Spring Boot app, so the browser sees one
// origin and the backend needs no CORS configuration.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
