import { useEffect, useState } from "react";
import { Text, View, StyleSheet } from "react-native";
import { run as bench } from "./src/bench";
import { run as sqlcipher } from "./src/sqlcipher";

const MODE: "bench" | "sqlcipher" = "sqlcipher";
const HOST = "http://10.0.2.2:8787";

export default function App() {
  const [text, setText] = useState("rodando...");
  useEffect(() => {
    (MODE === "sqlcipher" ? sqlcipher() : bench()).then(async (r) => {
      const s = JSON.stringify(r);
      console.log("RELATORIO_SQLCIPHER " + s);
      setText(JSON.stringify(r, null, 1));
      if (MODE === "bench") await fetch(`${HOST}/dev/report`, { method: "POST", body: s });
    }).catch((e) => { console.log("RELATORIO_SQLCIPHER_ERRO " + String(e)); setText("erro: " + String(e)); });
  }, []);
  return <View style={s.c}><Text style={s.t}>{text}</Text></View>;
}
const s = StyleSheet.create({ c: { flex: 1, padding: 24, paddingTop: 80, backgroundColor: "#fff" }, t: { fontFamily: "monospace" } });
