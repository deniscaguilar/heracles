// Servidor de teste da P1 (descartável): aplica mutações de forma idempotente e serve mudanças por cursor.
// Só módulos nativos do Node (node:http e node:sqlite). Não é o backend de produção.
import http from "node:http";
import { DatabaseSync } from "node:sqlite";

export type Clock = () => number;
type Mutation = {
  mutation_id: string;
  entity: "workout" | "meal" | "profile";
  op: "create" | "set";
  entity_id: string;
  payload: Record<string, unknown>;
};

export function createServer(opts: { now?: Clock; dev?: boolean } = {}) {
  const now = opts.now ?? (() => Date.now());
  const db = new DatabaseSync(":memory:");
  db.exec(`
    CREATE TABLE applied_mutations (mutation_id TEXT PRIMARY KEY, result TEXT NOT NULL);
    CREATE TABLE changes (seq INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL, entity TEXT NOT NULL,
                          entity_id TEXT NOT NULL, payload TEXT NOT NULL, server_ts INTEGER NOT NULL);
    CREATE TABLE records (entity TEXT NOT NULL, id TEXT NOT NULL, user_id TEXT NOT NULL, payload TEXT NOT NULL,
                          PRIMARY KEY (entity, id));
    CREATE TABLE profile (user_id TEXT NOT NULL, field TEXT NOT NULL, value TEXT, updated_at INTEGER NOT NULL,
                          PRIMARY KEY (user_id, field));
  `);
  const tokens = new Map<string, { userId: string; exp: number }>();
  const issue = (userId: string, ttlMs: number) => { const t = crypto.randomUUID(); tokens.set(t, { userId, exp: now() + ttlMs }); return t; };

  function applyMutation(userId: string, m: Mutation): string {
    const seen = db.prepare("SELECT result FROM applied_mutations WHERE mutation_id = ?").get(m.mutation_id) as
      | { result: string } | undefined;
    if (seen) return "duplicate";
    let result = "applied";
    const ts = now();
    if (m.op === "create") {
      // registros de uso: somente acréscimo; o id vem do cliente, então repetir não duplica
      const r = db.prepare("INSERT OR IGNORE INTO records (entity, id, user_id, payload) VALUES (?, ?, ?, ?)")
        .run(m.entity, m.entity_id, userId, JSON.stringify(m.payload));
      if (Number(r.changes) === 0) result = "duplicate";
      else db.prepare("INSERT INTO changes (user_id, entity, entity_id, payload, server_ts) VALUES (?,?,?,?,?)")
        .run(userId, m.entity, m.entity_id, JSON.stringify(m.payload), ts);
    } else if (m.op === "set") {
      // perfil: última escrita vence, por campo, com o relógio do SERVIDOR (não o do aparelho)
      for (const [field, value] of Object.entries(m.payload)) {
        db.prepare(`INSERT INTO profile (user_id, field, value, updated_at) VALUES (?,?,?,?)
                    ON CONFLICT(user_id, field) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`)
          .run(userId, field, JSON.stringify(value), ts);
      }
      db.prepare("INSERT INTO changes (user_id, entity, entity_id, payload, server_ts) VALUES (?,?,?,?,?)")
        .run(userId, "profile", userId, JSON.stringify(m.payload), ts);
    }
    db.prepare("INSERT INTO applied_mutations (mutation_id, result) VALUES (?, ?)").run(m.mutation_id, result);
    return result;
  }

  const server = http.createServer((req, res) => {
    const send = (code: number, body: unknown) => { res.writeHead(code, { "content-type": "application/json" }); res.end(JSON.stringify(body)); };
    if (opts.dev && req.url === "/dev/token") return send(200, { token: issue("dev", 3_600_000) });
    if (opts.dev && req.method === "POST" && req.url === "/dev/report") {
      let b = ""; req.on("data", (c) => (b += c)); req.on("end", () => { console.log("RELATORIO " + b); send(200, {}); }); return;
    }
    const auth = /^Bearer (.+)$/.exec(req.headers.authorization ?? "");
    const session = auth ? tokens.get(auth[1]) : undefined;
    if (!session || session.exp <= now()) return send(401, { error: "unauthorized" });
    const url = new URL(req.url ?? "/", "http://x");
    if (req.method === "POST" && url.pathname === "/sync/push") {
      let body = "";
      req.on("data", (c) => (body += c));
      req.on("end", () => {
        const { mutations } = JSON.parse(body) as { mutations: Mutation[] };
        db.exec("BEGIN");
        const results = mutations.map((m) => ({ mutation_id: m.mutation_id, result: applyMutation(session.userId, m) }));
        db.exec("COMMIT");
        send(200, { results });
      });
    } else if (req.method === "GET" && url.pathname === "/sync/pull") {
      const since = Number(url.searchParams.get("since") ?? 0);
      const rows = db.prepare("SELECT seq, entity, entity_id, payload, server_ts FROM changes WHERE user_id = ? AND seq > ? ORDER BY seq")
        .all(session.userId, since) as { seq: number }[];
      const profile = db.prepare("SELECT field, value, updated_at FROM profile WHERE user_id = ?").all(session.userId);
      send(200, { changes: rows, profile, next_since: rows.length ? rows[rows.length - 1].seq : since });
    } else send(404, { error: "not found" });
  });

  return {
    listen: (port = 0, host = "127.0.0.1") => new Promise<number>((ok) => server.listen(port, host, () => ok((server.address() as { port: number }).port))),
    close: () => new Promise<void>((ok) => server.close(() => ok())),
    issueToken: (userId: string, ttlMs = 60_000) => issue(userId, ttlMs),
    count: (entity: string) => (db.prepare("SELECT COUNT(*) AS n FROM records WHERE entity = ?").get(entity) as { n: number }).n,
    profileOf: (userId: string) => Object.fromEntries((db.prepare("SELECT field, value FROM profile WHERE user_id = ?").all(userId) as { field: string; value: string }[]).map((r) => [r.field, JSON.parse(r.value)])),
  };
}
