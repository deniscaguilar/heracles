import { useEffect, useState } from "react";
import { Platform, Text, View, StyleSheet } from "react-native";

export default function App() {
  const [text, setText] = useState("rodando...");
  useEffect(() => {
    const run: () => Promise<Record<string, unknown>> =
      Platform.OS === "ios" ? require("./src/ios").run : require("./src/android").run;
    run().then((r) => { const s = JSON.stringify(r); console.log("RELATORIO_SAUDE " + s); setText(JSON.stringify(r, null, 1)); })
      .catch((e) => { console.log("RELATORIO_SAUDE_ERRO " + String(e)); setText("erro: " + String(e)); });
  }, []);
  return <View style={s.c}><Text style={s.t}>{text}</Text></View>;
}
const s = StyleSheet.create({ c: { flex: 1, padding: 24, paddingTop: 80, backgroundColor: "#fff" }, t: { fontFamily: "monospace" } });
