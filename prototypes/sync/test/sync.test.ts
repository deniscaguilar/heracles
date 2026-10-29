import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "../server/server.ts";
import { SyncClient, AuthExpired } from "../client/sync-client.ts";
import { nodeStorage } from "../client/node-storage.ts";

async function setup() {
  let clock = 1_000_000;
  const server = createServer({ now: () => clock });
  const port = await server.listen();
  const url = `http://127.0.0.1:${port}`;
  const device = (token: string) => new SyncClient(nodeStorage(), url, () => token);
  return { server, url, device, tick: (ms: number) => { clock += ms; }, close: () => server.close() };
}

test("C1: registros criados offline chegam ao servidor ao reconectar", async () => {
  const s = await setup();
  const token = s.server.issueToken("u1");
  const a = s.device(token);
  for (let i = 0; i < 3; i++) a.create("workout", { exercise: "agachamento", sets: i + 1 });
  for (let i = 0; i < 2; i++) a.create("meal", { food: "arroz", grams: 150 });
  assert.equal(a.pending(), 5);          // tudo local e na fila, sem rede
  assert.equal(a.records("workout"), 3);
  await a.sync();
  assert.equal(a.pending(), 0);
  assert.equal(s.server.count("workout"), 3);
  assert.equal(s.server.count("meal"), 2);
  await s.close();
});

test("C2: a mesma mutação duas vezes não duplica o efeito", async () => {
  const s = await setup();
  const token = s.server.issueToken("u1");
  const a = s.device(token);
  a.create("workout", { exercise: "supino" });
  // simula resposta perdida: o servidor aplica, mas o app não apaga a fila e reenvia
  const body = JSON.stringify({ mutations: (a as any).db.all("SELECT * FROM outbox").map((r: any) => ({ ...r, payload: JSON.parse(r.payload) })) });
  for (let i = 0; i < 2; i++) {
    await fetch(`${s.url}/sync/push`, { method: "POST", body, headers: { authorization: `Bearer ${token}`, "content-type": "application/json" } });
  }
  await a.sync();
  assert.equal(s.server.count("workout"), 1);
  await s.close();
});

test("C3: perfil editado em dois aparelhos converge (última escrita, relógio do servidor)", async () => {
  const s = await setup();
  const token = s.server.issueToken("u1");
  const a = s.device(token), b = s.device(token);
  a.setProfile({ goal: "hipertrofia", level: "intermediario" });
  b.setProfile({ goal: "definicao" });          // mesmo campo, offline nos dois
  await a.sync(); s.tick(1000); await b.sync(); // b chega depois: vence em "goal"
  await a.sync();
  assert.deepEqual(s.server.profileOf("u1"), { goal: "definicao", level: "intermediario" });
  assert.deepEqual(a.profile(), b.profile());   // os dois convergem
  assert.equal(a.profile().goal, "definicao");
  await s.close();
});

test("C4: sessão expirada no meio da fila não perde nada e retoma depois", async () => {
  const s = await setup();
  const expired = s.server.issueToken("u1", -1);
  const a = s.device(expired);
  a.saveDraft("workout_in_progress", { exercise: "remada", done_sets: 2 }); // treino em andamento
  a.create("workout", { exercise: "remada" });
  await assert.rejects(() => a.sync(), AuthExpired);
  assert.equal(a.pending(), 1);                                           // fila preservada
  assert.deepEqual(a.draft("workout_in_progress"), { exercise: "remada", done_sets: 2 });
  let token = s.server.issueToken("u1");                                   // novo login
  (a as any).getToken = () => token;
  await a.sync();
  assert.equal(a.pending(), 0);
  assert.equal(s.server.count("workout"), 1);
  await s.close();
});

test("C5: sair da conta apaga os dados do aparelho", async () => {
  const s = await setup();
  const a = s.device(s.server.issueToken("u1"));
  a.create("meal", { food: "ovo" }); a.setProfile({ goal: "x" }); a.saveDraft("k", 1);
  a.wipe();
  assert.equal(a.pending(), 0); assert.equal(a.records("meal"), 0);
  assert.deepEqual(a.profile(), {}); assert.equal(a.draft("k"), null);
  await s.close();
});

test("C6: 10 mil registros em lotes (tempo de ida e volta)", async () => {
  const s = await setup();
  const a = s.device(s.server.issueToken("u1", 3_600_000));
  for (let i = 0; i < 10_000; i++) a.create("workout", { exercise: "x", i });
  const t0 = performance.now();
  await a.sync(500);
  const ms = performance.now() - t0;
  console.log(`DESEMPENHO sync de 10000 registros em lotes de 500: ${ms.toFixed(0)} ms`);
  assert.equal(s.server.count("workout"), 10_000);
  const b = s.device(s.server.issueToken("u1", 3_600_000));
  const t1 = performance.now();
  await b.sync();
  console.log(`DESEMPENHO pull de 10000 registros num aparelho novo: ${(performance.now() - t1).toFixed(0)} ms`);
  assert.equal(b.records("workout"), 10_000);
  await s.close();
});
