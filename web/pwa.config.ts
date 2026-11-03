import type { ManifestOptions, VitePWAOptions } from "vite-plugin-pwa";

// Service worker for the app shell only. The API carries health data, so no API response is ever
// cached: there is no `runtimeCaching`, and navigation fallback never applies to API paths.
export const shellWorkboxOptions: NonNullable<VitePWAOptions["workbox"]> = {
  globPatterns: ["**/*.{js,css,html,svg,png,webmanifest,woff2}"],
  navigateFallback: "/index.html",
  navigateFallbackDenylist: [/^\/api\//, /^\/v3\/api-docs/],
  cleanupOutdatedCaches: true,
  clientsClaim: true,
  skipWaiting: true,
};

export const manifest: Partial<ManifestOptions> = {
  name: "Heracles",
  short_name: "Heracles",
  description: "Treino, nutrição e saúde",
  lang: "pt-BR",
  start_url: "/",
  scope: "/",
  display: "standalone",
  background_color: "#111827",
  theme_color: "#111827",
  icons: [
    { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
    { src: "/icon-512.png", sizes: "512x512", type: "image/png" },
    { src: "/icon-512-maskable.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
  ],
};
