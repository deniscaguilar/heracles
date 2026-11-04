import { useEffect, useState } from "react";
import { Button, Linking, ScrollView, StyleSheet, Text, View } from "react-native";
import { useTranslation } from "react-i18next";
import type { components } from "@heracles/api-client";
import { useAuth } from "../auth/AuthContext";

type Erasure = components["schemas"]["ErasureResponse"];

/** How long to wait between two looks at the export file; tests make it short. */
export const timing = { exportPollMs: 2000 };

const day = (iso: string) => new Date(iso).toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo" });

/** "Privacidade": take the data out (export) and ask to erase the account, with 7 days to change one's mind (spec RF9, RF10). */
export function PrivacyScreen() {
  const { t } = useTranslation();
  return (
    <ScrollView contentContainerStyle={styles.box}>
      <Text accessibilityRole="header" style={styles.title}>{t("privacy.title")}</Text>
      <ExportSection />
      <ErasureSection />
    </ScrollView>
  );
}

type ExportState =
  | { status: "idle" }
  | { status: "waiting"; taskId: string }
  | { status: "ready"; url: string }
  | { status: "expired" }
  | { status: "failed" };

function ExportSection() {
  const { t } = useTranslation();
  const { api } = useAuth();
  const [state, setState] = useState<ExportState>({ status: "idle" });

  async function request() {
    try {
      const { data, response } = await api.POST("/api/v1/me/exports");
      if (!response.ok || !data) throw new Error("refused");
      setState({ status: "waiting", taskId: data.taskId });
    } catch {
      setState({ status: "failed" });
    }
  }

  const taskId = state.status === "waiting" ? state.taskId : undefined;
  useEffect(() => {
    if (!taskId) return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout>;
    const look = async () => {
      try {
        const { data, response } = await api.GET("/api/v1/me/exports/{taskId}", { params: { path: { taskId } } });
        if (cancelled) return;
        if (response.status === 200 && data) return setState({ status: "ready", url: data.url });
        if (response.status === 409) timer = setTimeout(() => void look(), timing.exportPollMs);
        else setState({ status: response.status === 410 ? "expired" : "failed" });
      } catch {
        if (!cancelled) setState({ status: "failed" });
      }
    };
    void look();
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [api, taskId]);

  return (
    <View style={styles.section}>
      <Text accessibilityRole="header" style={styles.subtitle}>{t("privacy.exportTitle")}</Text>
      <Text>{t("privacy.exportText")}</Text>
      {state.status === "waiting" && <Text accessibilityRole="progressbar">{t("privacy.exportWaiting")}</Text>}
      {state.status === "ready" && (
        <>
          <Button title={t("privacy.exportDownload")} onPress={() => void Linking.openURL(state.url)} />
          <Text>{t("privacy.exportLinkInfo")}</Text>
        </>
      )}
      {state.status === "expired" && <Text accessibilityRole="alert">{t("privacy.exportExpired")}</Text>}
      {state.status === "failed" && <Text accessibilityRole="alert">{t("privacy.exportFailed")}</Text>}
      <Button title={t("privacy.exportButton")} disabled={state.status === "waiting"} onPress={() => void request()} />
    </View>
  );
}

type ErasureState = { status: "loading" } | { status: "none" } | { status: "confirming" } | { status: "open"; erasure: Erasure };

function ErasureSection() {
  const { t } = useTranslation();
  const { api } = useAuth();
  const [state, setState] = useState<ErasureState>({ status: "loading" });
  const [failed, setFailed] = useState<"request" | "cancel" | undefined>();

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const { data } = await api.GET("/api/v1/me/erasure-requests/current");
        if (!cancelled) setState(data ? { status: "open", erasure: data } : { status: "none" });
      } catch {
        if (!cancelled) setState({ status: "none" });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [api]);

  async function confirm() {
    setFailed(undefined);
    try {
      const { data, response } = await api.POST("/api/v1/me/erasure-requests");
      if (!response.ok || !data) throw new Error("refused");
      setState({ status: "open", erasure: data });
    } catch {
      setFailed("request");
    }
  }

  async function cancel() {
    setFailed(undefined);
    try {
      const { response } = await api.DELETE("/api/v1/me/erasure-requests/current");
      if (!response.ok) throw new Error("refused");
      setState({ status: "none" });
    } catch {
      setFailed("cancel");
    }
  }

  if (state.status === "loading") return null;
  return (
    <View style={styles.section}>
      {state.status === "open" ? (
        <>
          <Text accessibilityRole="header" style={styles.subtitle}>{t("privacy.erasureScheduled")}</Text>
          {state.erasure.status === "RUNNING" ? (
            <Text>{t("privacy.erasureRunning")}</Text>
          ) : (
            <>
              <Text>{t("privacy.erasureDue", { date: day(state.erasure.dueAt) })}</Text>
              <Button title={t("privacy.erasureCancel")} onPress={() => void cancel()} />
            </>
          )}
        </>
      ) : state.status === "confirming" ? (
        <>
          <Text accessibilityRole="header" style={styles.subtitle}>{t("privacy.confirmTitle")}</Text>
          <Text>{t("privacy.confirmText")}</Text>
          <Button title={t("privacy.back")} onPress={() => setState({ status: "none" })} />
          <Button title={t("privacy.confirmButton")} color="#b00020" onPress={() => void confirm()} />
        </>
      ) : (
        <>
          <Text accessibilityRole="header" style={styles.subtitle}>{t("privacy.erasureTitle")}</Text>
          <Text>{t("privacy.erasureText")}</Text>
          <Button title={t("privacy.erasureButton")} onPress={() => setState({ status: "confirming" })} />
        </>
      )}
      {failed === "request" && <Text accessibilityRole="alert">{t("privacy.erasureRequestError")}</Text>}
      {failed === "cancel" && <Text accessibilityRole="alert">{t("privacy.erasureCancelError")}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { padding: 24, gap: 16 },
  section: { gap: 8 },
  title: { fontSize: 24, fontWeight: "700" },
  subtitle: { fontSize: 18, fontWeight: "700" },
});
