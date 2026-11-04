import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import type { components } from "@heracles/api-client";
import { useAuth } from "../auth/AuthContext";

type Erasure = components["schemas"]["ErasureResponse"];

/** How long to wait between two looks at the export file; tests make it short. */
export const timing = { exportPollMs: 2000 };

const day = (iso: string) => new Date(iso).toLocaleDateString("pt-BR", { timeZone: "America/Sao_Paulo" });

/** "Privacidade": take the data out (export) and ask to erase the account, with 7 days to change one's mind (spec RF9, RF10). */
export function PrivacyPage() {
  const { t } = useTranslation();
  return (
    <section aria-labelledby="privacy-title">
      <h1 id="privacy-title">{t("privacy.title")}</h1>
      <ExportSection />
      <ErasureSection />
    </section>
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
    <section aria-labelledby="export-title">
      <h2 id="export-title">{t("privacy.exportTitle")}</h2>
      <p>{t("privacy.exportText")}</p>
      {state.status === "waiting" && <p role="status">{t("privacy.exportWaiting")}</p>}
      {state.status === "ready" && (
        <p>
          <a href={state.url}>{t("privacy.exportDownload")}</a> {t("privacy.exportLinkInfo")}
        </p>
      )}
      {state.status === "expired" && <p role="alert">{t("privacy.exportExpired")}</p>}
      {state.status === "failed" && <p role="alert">{t("privacy.exportFailed")}</p>}
      <button type="button" disabled={state.status === "waiting"} onClick={() => void request()}>
        {t("privacy.exportButton")}
      </button>
    </section>
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
    <section aria-labelledby="erasure-title">
      {state.status === "open" ? (
        <>
          <h2 id="erasure-title">{t("privacy.erasureScheduled")}</h2>
          {state.erasure.status === "RUNNING" ? (
            <p>{t("privacy.erasureRunning")}</p>
          ) : (
            <>
              <p>{t("privacy.erasureDue", { date: day(state.erasure.dueAt) })}</p>
              <button type="button" onClick={() => void cancel()}>
                {t("privacy.erasureCancel")}
              </button>
            </>
          )}
        </>
      ) : state.status === "confirming" ? (
        <>
          <h2 id="erasure-title">{t("privacy.confirmTitle")}</h2>
          <p>{t("privacy.confirmText")}</p>
          <button type="button" onClick={() => setState({ status: "none" })}>
            {t("privacy.back")}
          </button>
          <button type="button" onClick={() => void confirm()}>
            {t("privacy.confirmButton")}
          </button>
        </>
      ) : (
        <>
          <h2 id="erasure-title">{t("privacy.erasureTitle")}</h2>
          <p>{t("privacy.erasureText")}</p>
          <button type="button" onClick={() => setState({ status: "confirming" })}>
            {t("privacy.erasureButton")}
          </button>
        </>
      )}
      {failed === "request" && <p role="alert">{t("privacy.erasureRequestError")}</p>}
      {failed === "cancel" && <p role="alert">{t("privacy.erasureCancelError")}</p>}
    </section>
  );
}
