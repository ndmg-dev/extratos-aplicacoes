import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Proxy /api para o backend em dev (evita CORS e mantém mesma origem).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: process.env.VITE_API_TARGET ?? "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
