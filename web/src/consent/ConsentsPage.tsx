import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { components } from "@heracles/api-client";
import { useAuth } from "../auth/AuthContext";
import { useConsents, type ConsentStatus } from "./ConsentGate";
import { PolicyBlocks } from "./PolicyBlocks";

type Policy = components["schemas"]["PolicyResponse"];
type ConsentType = components["schemas"]["ConsentStatusResponse"]["type"];

/** "Meus consentimentos": where each of the seven stands, and the switch of the four optional ones (spec RF4). */
export function ConsentsPage() {
  const { t } = useTranslation();
  const { state, refresh } = useConsents();
  const [failed, setFailed] = useState(false);

  if (state.status === "loading") return <p>{t("auth.loading")}</p>;
  if (state.status === "error") return <p role="alert">{t("gate.loadError")}</p>;
  return (
    <section aria-labelledby="consents-title">
      <h1 id="consents-title">{t("consents.title")}</h1>
      <p>{t("consents.intro")}</p>
      {failed && <p role="alert">{t("consents.changeError")}</p>}
      <ul>
        {state.items.map((item) => (
          <ConsentRow key={item.type} item={item} onChanged={refresh} onFailed={setFailed} />
        ))}
      </ul>
    </section>
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
    const { data } = await api.GET("/api/v1/policies/{type}/current", { params: { path: { type: item.type as ConsentType as never } } });
    if (data) setPolicy(data);
  }

  async function change(granted: boolean) {
    setBusy(true);
    props.onFailed(false);
    try {
      const { response } = await api.PUT("/api/v1/me/consents/{type}", {
        params: { path: { type: item.type as never } },
        body: { granted, versionId: item.currentVersionId, client: "WEB" }, // the backend wants the version also to turn off
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
    <li aria-label={name}>
      <strong>{name}</strong> <span>{status}</span>
      {item.currentVersion != null && <small> {t("consents.version", { version: item.currentVersion })}</small>}{" "}
      {item.currentVersionId == null && <small> {t("consents.unavailable")}</small>}
      {item.currentVersionId != null && (
        <button type="button" onClick={() => void readText()}>
          {t("consents.read")}
        </button>
      )}
      {!item.required && item.currentVersionId != null && (
        <button type="button" disabled={busy} onClick={() => void change(!item.granted)}>
          {item.granted ? t("consents.revoke") : t("consents.turnOn")}
        </button>
      )}
      {policy && (
        <div>
          <h2>{policy.title}</h2>
          <PolicyBlocks blocks={policy.blocks} />
        </div>
      )}
    </li>
  );
}
