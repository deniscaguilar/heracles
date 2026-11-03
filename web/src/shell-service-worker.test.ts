// @vitest-environment node
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { generateSW } from "workbox-build";
import { describe, expect, it } from "vitest";
import { manifest, shellWorkboxOptions } from "../pwa.config.ts";

// The API carries health data, so the service worker may only precache the static shell.
async function buildServiceWorker(): Promise<string> {
  const dir = mkdtempSync(join(tmpdir(), "heracles-sw-"));
  mkdirSync(join(dir, "assets"));
  writeFileSync(join(dir, "index.html"), "<!doctype html>");
  writeFileSync(join(dir, "assets", "app.js"), "console.log(1)");
  writeFileSync(join(dir, "assets", "app.css"), "body{}");
  writeFileSync(join(dir, "data.json"), "{}");
  await generateSW({ ...shellWorkboxOptions, globDirectory: dir, swDest: join(dir, "sw.js") });
  return readFileSync(join(dir, "sw.js"), "utf8");
}

describe("shell service worker", () => {
  it("has no runtime caching, so no API response is ever stored", async () => {
    expect(shellWorkboxOptions.runtimeCaching).toBeUndefined();
    const sw = await buildServiceWorker();
    expect(sw).not.toMatch(/CacheFirst|NetworkFirst|StaleWhileRevalidate|CacheableResponse/);
    // The only routes are the precache and the navigation fallback.
    expect(sw.match(/registerRoute\(/g)).toHaveLength(1);
  });

  it("never answers API navigations with the shell", async () => {
    const sw = await buildServiceWorker();
    expect(sw).toMatch(/denylist:\s*\[\/\^\\\/api\\\//);
  });

  it("precaches only static shell files", async () => {
    const sw = await buildServiceWorker();
    expect(sw).toContain("index.html");
    expect(sw).toContain("assets/app.js");
    expect(sw).not.toContain("data.json");
  });

  it("is installable: standalone manifest with 192, 512 and maskable icons", () => {
    expect(manifest.display).toBe("standalone");
    expect(manifest.start_url).toBe("/");
    const sizes = (manifest.icons ?? []).map((i) => `${i.sizes}${i.purpose ? `:${i.purpose}` : ""}`);
    expect(sizes).toEqual(expect.arrayContaining(["192x192", "512x512", "512x512:maskable"]));
  });
});
