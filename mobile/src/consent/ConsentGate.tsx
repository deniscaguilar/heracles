import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Button, ScrollView, StyleSheet, Text, View } from "react-native";
import { useTranslation } from "react-i18next";
import type { components } from "@heracles/api-client";
import { useAuth } from "../auth/AuthContext";
import { Choice } from "../ui";
import { PolicyBlocks } from "./PolicyBlocks";

export type ConsentStatus = components["schemas"]["ConsentStatusResponse"];
type Policy = components["schemas"]["PolicyResponse"];

type State = { status: "loading" } | { status: "error" } | { status: "ready"; items: ConsentStatus[] };

/** The consents of the signed-in person. `refresh` reloads them after any change. */
export function useConsents() {
  const { api } = useAuth();
  const [state, setState] = useState<State>({ status: "loading" });
  const refresh = useCallback(async () => {
    try {
      const { data } = await api.GET("/api/v1/me/consents");
      setState(data && Array.isArray(data.items) ? { status: "ready", items: data.items } : { status: "error" });
    } catch {
      setState({ status: "error" });
    }
  }, [api]);
  useEffect(() => {
    void refresh();
  }, [refresh]);
  return { state, refresh };
}

/**
 * Nothing of the app opens while a required text is pending, on first access and on every new version (spec RF3). The
 * backend refuses health routes too (403 `consent_required`); this is the screen that explains and collects the acceptance.
 */
export function ConsentGate(props: { children: ReactNode }) {
  const { t } = useTranslation();
  const { state, refresh } = useConsents();
  if (state.status === "loading") return <Text>{t("auth.loading")}</Text>;
  if (state.status === "error") {
    return (
      <View style={styles.box}>
        <Text accessibilityRole="alert">{t("gate.loadError")}</Text>
        <Button title={t("gate.retry")} onPress={() => void refresh()} />
      </View>
    );
  }
  const pending = state.items.filter((c) => c.required && c.pending);
  if (pending.length === 0) return <>{props.children}</>;
  return <AcceptPending pending={pending} required={state.items.filter((c) => c.required)} onAccepted={refresh} />;
}

// The backend wants the current version of all three required texts: the ones read now, and the ones already accepted.
function versionIds(read: Policy[], required: ConsentStatus[]): string[] {
  const byType = new Map(read.map((p) => [p.type as string, p.versionId]));
  return required.map((c) => byType.get(c.type) ?? c.currentVersionId!).filter(Boolean);
}

function AcceptPending(props: { pending: ConsentStatus[]; required: ConsentStatus[]; onAccepted: () => Promise<void> }) {
  const { t } = useTranslation();
  const { api } = useAuth();
  const [policies, setPolicies] = useState<Policy[] | "error" | undefined>();
  const [checked, setChecked] = useState<Set<string>>(new Set());
  const [failed, setFailed] = useState(false);
  const [sending, setSending] = useState(false);
  const types = props.pending.map((c) => c.type).join(",");

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const loaded = await Promise.all(
          props.pending.map(async (c) => {
            const { data } = await api.GET("/api/v1/policies/{type}/current", { params: { path: { type: c.type as never } } });
            if (!data) throw new Error("no policy");
            return data;
          }),
        );
        if (!cancelled) setPolicies(loaded);
      } catch {
        if (!cancelled) setPolicies("error");
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [api, types]);

  const returning = props.pending.some((c) => c.acceptedVersionId);
  const all = Array.isArray(policies) && policies.every((p) => checked.has(p.versionId));

  async function accept() {
    if (!Array.isArray(policies)) return;
    setSending(true);
    setFailed(false);
    try {
      const { response } = await api.POST("/api/v1/me/required-consents", {
        body: { versionIds: versionIds(policies, props.required), client: "MOBILE" },
      });
      if (!response.ok) throw new Error("refused");
      await props.onAccepted();
    } catch {
      setFailed(true);
    } finally {
      setSending(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.box}>
      <Text accessibilityRole="header" style={styles.title}>{t("gate.title")}</Text>
      <Text>{returning ? t("gate.changed") : t("gate.intro")}</Text>
      {policies === "error" && <Text accessibilityRole="alert">{t("gate.loadError")}</Text>}
      {Array.isArray(policies) &&
        policies.map((policy) => (
          <View key={policy.versionId} style={styles.policy}>
            <Text accessibilityRole="header" style={styles.subtitle}>{policy.title}</Text>
            <PolicyBlocks blocks={policy.blocks} />
            <Choice
              role="checkbox"
              label={t("gate.read", { title: policy.title })}
              selected={checked.has(policy.versionId)}
              onPress={() =>
                setChecked((prev) => {
                  const next = new Set(prev);
                  if (next.has(policy.versionId)) next.delete(policy.versionId);
                  else next.add(policy.versionId);
                  return next;
                })
              }
            />
          </View>
        ))}
      {failed && <Text accessibilityRole="alert">{t("gate.acceptError")}</Text>}
      <Button title={t("gate.accept")} disabled={!all || sending} onPress={() => void accept()} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  box: { padding: 24, gap: 12 },
  title: { fontSize: 24, fontWeight: "700" },
  subtitle: { fontSize: 18, fontWeight: "700" },
  policy: { gap: 8 },
});
