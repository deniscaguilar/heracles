import * as SQLite from "expo-sqlite";

// Prova de criptografia local (SQLCipher) do protótipo da P1. Chave FIXA, só para o teste:
// no app real ela vem do armazenamento seguro do sistema (ADR-0005).
const KEY = "chave-de-teste-nao-usar-em-producao";
const WRONG = "outra-chave";

function open(name: string, key?: string) {
  const db = SQLite.openDatabaseSync(name);
  if (key) db.execSync(`PRAGMA key = '${key}'`);
  return db;
}

function attempt<T>(fn: () => T): string {
  try { return "ok:" + JSON.stringify(fn()); } catch (e) { return "erro:" + String(e).slice(0, 90); }
}

export async function run(): Promise<Record<string, unknown>> {
  const out: Record<string, unknown> = {};
  const stamp = Date.now();
  const enc = `enc-${stamp}.db`, plain = `plain-${stamp}.db`;
  out.arquivo_cifrado = enc;

  // 1) banco cifrado: criar, gravar, ler
  let db = open(enc, KEY);
  out.cipher_version = attempt(() => db.getFirstSync("PRAGMA cipher_version"));
  db.execSync("CREATE TABLE t (id INTEGER PRIMARY KEY, v TEXT)");
  db.withTransactionSync(() => { for (let i = 0; i < 1000; i++) db.runSync("INSERT INTO t (v) VALUES (?)", ["dado-" + i]); });
  out.cifrado_contagem = attempt(() => db.getFirstSync("SELECT COUNT(*) AS n FROM t"));
  db.closeSync();

  // 2) reabrir SEM chave, com chave ERRADA e com a chave CERTA
  db = open(enc);
  out.sem_chave = attempt(() => db.getFirstSync("SELECT COUNT(*) AS n FROM t"));
  db.closeSync();
  db = open(enc, WRONG);
  out.chave_errada = attempt(() => db.getFirstSync("SELECT COUNT(*) AS n FROM t"));
  db.closeSync();
  db = open(enc, KEY);
  out.chave_certa = attempt(() => db.getFirstSync("SELECT COUNT(*) AS n FROM t"));
  db.closeSync();

  // 3) desempenho: 10 mil registros em transação, cifrado e não cifrado
  const bench = (name: string, key?: string) => {
    const d = open(name, key);
    d.execSync("CREATE TABLE IF NOT EXISTS b (id INTEGER PRIMARY KEY, v TEXT)");
    let t = Date.now();
    d.withTransactionSync(() => { for (let i = 0; i < 10_000; i++) d.runSync("INSERT INTO b (v) VALUES (?)", ["x" + i]); });
    const write = Date.now() - t;
    t = Date.now();
    d.getAllSync("SELECT * FROM b");
    const read = Date.now() - t;
    d.closeSync();
    return { gravar_10000_ms: write, ler_10000_ms: read };
  };
  out.desempenho_cifrado = bench(enc, KEY);
  out.desempenho_sem_cifra = bench(plain);
  out.arquivo_sem_cifra = plain;
  return out;
}
