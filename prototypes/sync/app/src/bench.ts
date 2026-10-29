import { SyncClient } from "./sync-client";
import { expoStorage } from "./expo-storage";

const HOST = "http://10.0.2.2:8787"; // o emulador Android enxerga o computador por 10.0.2.2

export async function run(): Promise<Record<string, unknown>> {
  const out: Record<string, unknown> = {};
  const { token } = await (await fetch(`${HOST}/dev/token`)).json();
  const store = expoStorage(`p1-${Date.now()}.db`);
  const a = new SyncClient(store, HOST, () => token);
  let t = Date.now();
  store.db.withTransactionSync(() => { for (let i = 0; i < 10_000; i++) a.create("workout", { exercise: "x", i }); });
  out.criar_10000_local_ms = Date.now() - t;
  t = Date.now();
  out.contar_registros_local = a.records("workout");
  out.contar_ms = Date.now() - t;
  t = Date.now();
  await a.sync(500);
  out.sync_10000_para_servidor_ms = Date.now() - t;
  out.fila_depois = a.pending();
  const b = new SyncClient(expoStorage(`p1b-${Date.now()}.db`), HOST, () => token);
  t = Date.now();
  await b.sync();
  out.pull_10000_aparelho_novo_ms = Date.now() - t;
  out.registros_no_aparelho_novo = b.records("workout");
  b.wipe();
  out.apos_wipe = b.records("workout");
  return out;
}

