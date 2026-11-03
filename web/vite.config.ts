import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";
import { defineConfig } from "vitest/config";
import { manifest, shellWorkboxOptions } from "./pwa.config.ts";

export default defineConfig({
  plugins: [
    react(),
    VitePWA({ registerType: "autoUpdate", manifest, workbox: shellWorkboxOptions }),
  ],
  server: {
    port: 5173,
    // The backend runs on 8080 locally; the browser only ever talks to its own origin.
    proxy: { "/api": "http://localhost:8080" },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
