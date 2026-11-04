import { useState } from "react";
import { Button, ScrollView, StyleSheet, Text, View } from "react-native";
import { useTranslation } from "react-i18next";
import type { components } from "@heracles/api-client";
import { useAuth } from "../auth/AuthContext";
import { useConsents, type ConsentStatus } from "./ConsentGate";
import { PolicyBlocks } from "./PolicyBlocks";

type Policy = components["schemas"]["PolicyResponse"];

/** "Meus consentimentos": where each of the seven stands, and the switch of the four optional ones (spec RF4). */
export function ConsentsScreen() {
  const { t } = useTranslation();
  const { state, refresh } = useConsents();
  const [failed, setFailed] = useState(false);

  if (state.status === "loading") return <Text>{t("auth.loading")}</Text>;
  if (state.status === "error") return <Text accessibilityRole="alert">{t("gate.loadError")}</Text>;
  return (
    <ScrollView contentContainerStyle={styles.box}>
      <Text accessibilityRole="header" style={styles.title}>{t("consents.title")}</Text>
      <Text>{t("consents.intro")}</Text>
      {failed && <Text accessibilityRole="alert">{t("consents.changeError")}</Text>}
      {state.items.map((item) => (
        <ConsentRow key={item.type} item={item} onChanged={refresh} onFailed={setFailed} />
      ))}
    </ScrollView>
  );
}

function ConsentRow(props: { item: ConsentStatus; onChanged: () => Promise<void>; onFailed: (failed: boolean) => void }) {
  const { t } = useTranslation();
  const { api } = useAuth();
  const { item } = props;
  const [policy, setPolicy] = useState<Policy | undefined>();
  const [busy, setBusy] = useState(false);
  const name = t(`consents.types.${item.type}`, { defaultValue: item.type });

  async function readText() {
    if (policy) return setPolicy(undefined);
    const { data } = await api.GET("/api/v1/policies/{type}/current", { params: { path: { type: item.type as never } } });
    if (data) setPolicy(data);
  }

  async function change(granted: boolean) {
    setBusy(true);
    props.onFailed(false);
    try {
      const { response } = await api.PUT("/api/v1/me/consents/{type}", {
        params: { path: { type: item.type as never } },
        body: { granted, versionId: item.currentVersionId, client: "MOBILE" }, // the backend wants the version also to turn off
      });
      if (!response.ok) throw new Error("refused");
      await props.onChanged();
    } catch {
      props.onFailed(true);
    } finally {
      setBusy(false);
    }
  }

  const status = item.required ? (item.granted ? t("consents.accepted") : t("consents.pending")) : item.granted ? t("consents.on") : t("consents.off");
  return (
    <View accessibilityLabel={name} style={styles.row}>
      <Text style={styles.name}>{name}</Text>
      <Text>{status}</Text>
      {item.currentVersion != null && <Text>{t("consents.version", { version: item.currentVersion })}</Text>}
      {item.currentVersionId == null && <Text>{t("consents.unavailable")}</Text>}
      {item.currentVersionId != null && <Button title={t("consents.read")} onPress={() => void readText()} />}
      {!item.required && item.currentVersionId != null && <Button title={item.granted ? t("consents.revoke") : t("consents.turnOn")} disabled={busy} onPress={() => void change(!item.granted)} />}
      {policy && (
        <View style={styles.policy}>
          <Text accessibilityRole="header" style={styles.name}>{policy.title}</Text>
          <PolicyBlocks blocks={policy.blocks} />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { padding: 24, gap: 12 },
  title: { fontSize: 24, fontWeight: "700" },
  row: { gap: 6, paddingVertical: 8 },
  name: { fontWeight: "700", fontSize: 16 },
  policy: { gap: 8 },
});
