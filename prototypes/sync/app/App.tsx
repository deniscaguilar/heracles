import { useEffect, useState } from "react";
import { Text, View, StyleSheet } from "react-native";
import { SyncClient } from "./src/sync-client";
import { expoStorage } from "./src/expo-storage";

const HOST = "http://10.0.2.2:8787"; // o emulador Android enxerga o computador por 10.0.2.2

async function run(): Promise<Record<string, unknown>> {
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

export default function App() {
  const [text, setText] = useState("rodando...");
  useEffect(() => {
    run().then(async (r) => {
      setText(JSON.stringify(r, null, 1));
      await fetch(`${HOST}/dev/report`, { method: "POST", body: JSON.stringify(r) });
    }).catch((e) => setText("erro: " + String(e)));
  }, []);
  return <View style={s.c}><Text style={s.t}>{text}</Text></View>;
}
const s = StyleSheet.create({ c: { flex: 1, padding: 24, paddingTop: 80, backgroundColor: "#fff" }, t: { fontFamily: "monospace" } });
