import { useCallback, useEffect, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Outlet } from "react-router";
import type { components } from "@heracles/api-client";
import { useAuth } from "../auth/AuthContext";
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
  const { session } = useAuth();
  if (session.status !== "authenticated") return <>{props.children}</>;
  return <AuthenticatedGate>{props.children}</AuthenticatedGate>;
}

/** A layout route: the login callback stays outside it, so finishing the login does not remount the callback. */
export function GatedRoutes() {
  return (
    <ConsentGate>
      <Outlet />
    </ConsentGate>
  );
}

function AuthenticatedGate(props: { children: ReactNode }) {
  const { t } = useTranslation();
  const { state, refresh } = useConsents();
  if (state.status === "loading") return <p>{t("auth.loading")}</p>;
  if (state.status === "error") return <p role="alert">{t("gate.loadError")}</p>;
  const pending = state.items.filter((c) => c.required && c.pending);
  if (pending.length === 0) return <>{props.children}</>;
  return <AcceptPending pending={pending} required={state.items.filter((c) => c.required)} onAccepted={refresh} />;
}

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
            const { data } = await api.GET("/api/v1/policies/{type}/current", {
              params: { path: { type: c.type as Policy["type"] as never } },
            });
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
        // The backend wants the current version of all three required texts: the ones read now, and the ones already accepted.
        body: { versionIds: versionIds(policies, props.required), client: "WEB" },
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
    <section aria-labelledby="gate-title">
      <h1 id="gate-title">{t("gate.title")}</h1>
      <p>{returning ? t("gate.changed") : t("gate.intro")}</p>
      {policies === "error" && <p role="alert">{t("gate.loadError")}</p>}
      {Array.isArray(policies) &&
        policies.map((policy) => (
          <article key={policy.versionId}>
            <h2>{policy.title}</h2>
            <PolicyBlocks blocks={policy.blocks} />
            <label>
              <input
                type="checkbox"
                checked={checked.has(policy.versionId)}
                onChange={(e) =>
                  setChecked((prev) => {
                    const next = new Set(prev);
                    if (e.target.checked) next.add(policy.versionId);
                    else next.delete(policy.versionId);
                    return next;
                  })
                }
              />{" "}
              {t("gate.read", { title: policy.title })}
            </label>
          </article>
        ))}
      {failed && <p role="alert">{t("gate.acceptError")}</p>}
      <button type="button" disabled={!all || sending} onClick={() => void accept()}>
        {t("gate.accept")}
      </button>
    </section>
  );
}
