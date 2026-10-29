// Cliente de sincronização próprio (opção A da P1): fila de mutações local + push idempotente + pull por cursor.
// O armazenamento é um adaptador (node:sqlite nos testes, expo-sqlite no aparelho).

export interface Storage {
  exec(sql: string): void;
  run(sql: string, params?: unknown[]): void;
  all<T = Record<string, unknown>>(sql: string, params?: unknown[]): T[];
  /** Agrupa várias gravações numa transação (essencial para o desempenho em lote). */
  tx(fn: () => void): void;
}

export class AuthExpired extends Error {}

function uuid(): string {
  const c = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto;
  if (c?.randomUUID) return c.randomUUID();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (ch) => {
    const r = (Math.random() * 16) | 0;
    return (ch === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export class SyncClient {
  private db: Storage;
  private baseUrl: string;
  getToken: () => string | null;

  constructor(db: Storage, baseUrl: string, getToken: () => string | null) {
    this.db = db;
    this.baseUrl = baseUrl;
    this.getToken = getToken;
    db.exec(`
      CREATE TABLE IF NOT EXISTS outbox (mutation_id TEXT PRIMARY KEY, entity TEXT, op TEXT, entity_id TEXT, payload TEXT, seq INTEGER);
      CREATE TABLE IF NOT EXISTS local_records (entity TEXT, id TEXT, payload TEXT, PRIMARY KEY (entity, id));
      CREATE TABLE IF NOT EXISTS local_profile (field TEXT PRIMARY KEY, value TEXT);
      CREATE TABLE IF NOT EXISTS sync_state (key TEXT PRIMARY KEY, value TEXT);
      CREATE TABLE IF NOT EXISTS draft (key TEXT PRIMARY KEY, value TEXT);
    `);
  }

  private enqueue(entity: string, op: string, entityId: string, payload: unknown) {
    const n = this.db.all<{ n: number }>("SELECT COALESCE(MAX(seq), 0) + 1 AS n FROM outbox")[0].n;
    this.db.run("INSERT INTO outbox VALUES (?,?,?,?,?,?)", [uuid(), entity, op, entityId, JSON.stringify(payload), n]);
  }

  /** Registro de uso: id gerado no cliente, escrita local imediata, mutação na fila. */
  create(entity: "workout" | "meal", payload: Record<string, unknown>): string {
    const id = uuid();
    this.db.run("INSERT INTO local_records VALUES (?,?,?)", [entity, id, JSON.stringify(payload)]);
    this.enqueue(entity, "create", id, payload);
    return id;
  }

  setProfile(fields: Record<string, unknown>) {
    for (const [k, v] of Object.entries(fields)) this.db.run("INSERT OR REPLACE INTO local_profile VALUES (?,?)", [k, JSON.stringify(v)]);
    this.enqueue("profile", "set", "me", fields);
  }

  saveDraft(key: string, value: unknown) { this.db.run("INSERT OR REPLACE INTO draft VALUES (?,?)", [key, JSON.stringify(value)]); }
  draft(key: string) { const r = this.db.all<{ value: string }>("SELECT value FROM draft WHERE key = ?", [key])[0]; return r ? JSON.parse(r.value) : null; }
  pending() { return this.db.all<{ n: number }>("SELECT COUNT(*) AS n FROM outbox")[0].n; }
  records(entity: string) { return this.db.all<{ id: string }>("SELECT id FROM local_records WHERE entity = ?", [entity]).length; }
  profile() { return Object.fromEntries(this.db.all<{ field: string; value: string }>("SELECT * FROM local_profile").map((r) => [r.field, JSON.parse(r.value)])); }

  private async call(path: string, init?: RequestInit) {
    const token = this.getToken();
    const res = await fetch(this.baseUrl + path, { ...init, headers: { authorization: `Bearer ${token}`, "content-type": "application/json" } });
    if (res.status === 401) throw new AuthExpired();
    return res.json();
  }

  /** Envia a fila em lotes (a fila só encolhe depois da resposta) e depois puxa o que mudou. */
  async sync(batchSize = 200) {
    for (;;) {
      const rows = this.db.all<{ mutation_id: string; entity: string; op: string; entity_id: string; payload: string }>(
        "SELECT * FROM outbox ORDER BY seq LIMIT ?", [batchSize]);
      if (!rows.length) break;
      const body = JSON.stringify({ mutations: rows.map((r) => ({ ...r, payload: JSON.parse(r.payload) })) });
      const { results } = await this.call("/sync/push", { method: "POST", body }); // 401 lança e preserva a fila
      this.db.tx(() => {
        for (const r of results as { mutation_id: string }[]) this.db.run("DELETE FROM outbox WHERE mutation_id = ?", [r.mutation_id]);
      });
    }
    const since = Number(this.db.all<{ value: string }>("SELECT value FROM sync_state WHERE key = 'since'")[0]?.value ?? 0);
    const pulled = await this.call(`/sync/pull?since=${since}`);
    this.db.tx(() => {
      for (const c of pulled.changes as { entity: string; entity_id: string; payload: string }[]) {
        if (c.entity !== "profile") this.db.run("INSERT OR IGNORE INTO local_records VALUES (?,?,?)", [c.entity, c.entity_id, c.payload]);
      }
    });
    // perfil: o servidor é a autoridade (última escrita por campo, relógio do servidor)
    const pendingProfile = new Set(this.db.all<{ payload: string }>("SELECT payload FROM outbox WHERE entity = 'profile'")
      .flatMap((r) => Object.keys(JSON.parse(r.payload))));
    for (const p of pulled.profile as { field: string; value: string }[]) {
      if (!pendingProfile.has(p.field)) this.db.run("INSERT OR REPLACE INTO local_profile VALUES (?,?)", [p.field, p.value]);
    }
    this.db.run("INSERT OR REPLACE INTO sync_state VALUES ('since', ?)", [String(pulled.next_since)]);
  }

  /** Sair da conta: apaga tudo o que é dado de usuário no aparelho. */
  wipe() { for (const t of ["outbox", "local_records", "local_profile", "sync_state", "draft"]) this.db.run(`DELETE FROM ${t}`); }
}
