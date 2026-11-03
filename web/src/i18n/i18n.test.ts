import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import i18n from "./index";
import ptBR from "../locales/pt-BR.json";

function keys(node: unknown, prefix = ""): string[] {
  if (typeof node !== "object" || node === null) return [prefix];
  return Object.entries(node).flatMap(([k, v]) => keys(v, prefix ? `${prefix}.${k}` : k));
}

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sources(path);
    return /\.tsx?$/.test(name) && !/\.test\./.test(name) ? [path] : [];
  });
}

describe("i18n", () => {
  it("uses pt-BR", () => {
    expect(i18n.language).toBe("pt-BR");
  });

  it("keeps every key in English (ASCII, camelCase segments)", () => {
    for (const key of keys(ptBR)) {
      expect(key).toMatch(/^[a-z][A-Za-z0-9]*(\.[a-z][A-Za-z0-9]*)*$/);
    }
  });

  it("has a translation for every key used in the code", () => {
    const known = new Set(keys(ptBR));
    const used = sources(join(__dirname, ".."))
      .flatMap((file) => [...readFileSync(file, "utf8").matchAll(/\bt\("([^"]+)"\)/g)])
      .map((m) => m[1]!);
    expect(used.length).toBeGreaterThan(0);
    for (const key of used) expect(known, key).toContain(key);
  });
});
