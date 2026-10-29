import { DatabaseSync } from "node:sqlite";
import type { Storage } from "./sync-client.ts";

export function nodeStorage(path = ":memory:"): Storage {
  const db = new DatabaseSync(path);
  return {
    exec: (sql) => db.exec(sql),
    run: (sql, params = []) => { db.prepare(sql).run(...(params as never[])); },
    all: <T>(sql: string, params: unknown[] = []) => db.prepare(sql).all(...(params as never[])) as T[],
    tx: (fn) => { db.exec("BEGIN"); try { fn(); db.exec("COMMIT"); } catch (e) { db.exec("ROLLBACK"); throw e; } },
  };
}
