import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import type { components } from "@heracles/api-client";
import { useAuth } from "../auth/AuthContext";
import { PolicyBlocks, type Block } from "../consent/PolicyBlocks";

type Summary = components["schemas"]["PolicyVersionSummaryResponse"];
type PolicyType = components["schemas"]["ConsentStatusResponse"]["type"];

const TYPES = [
  "TERMS_OF_USE", "PRIVACY_POLICY", "HEALTH_DATA_CONSENT", "AI_EXAMS_CONSENT", "COMMUNITY_CONSENT", "NOTIFICATIONS_CONSENT", "PROMOTIONAL_CONSENT",
] as const;
const REQUIRED = new Set<string>(["TERMS_OF_USE", "PRIVACY_POLICY", "HEALTH_DATA_CONSENT"]);

const ERROR_KEYS: Record<string, string> = { policy_text_invalid: "invalidPolicyText", draft_already_exists: "draftAlreadyExists" };

type Panel =
  | { kind: "editor"; type: string; draftId?: string; title: string; markdown: string }
  | { kind: "preview"; title: string; blocks: Block[] }
  | { kind: "publish"; type: string; draftId: string };

/** Policy versions: what exists, how many accepted, and the draft, preview and publication of the next one (spec RF2). */
export function PoliciesPage() {
  const { t } = useTranslation();
  const { api } = useAuth();
  const [items, setItems] = useState<Summary[] | "error" | undefined>();
  const [panel, setPanel] = useState<Panel | undefined>();
  const [problem, setProblem] = useState<string | undefined>();

  const load = useCallback(async () => {
    try {
      const { data } = await api.GET("/api/v1/admin/policies");
      setItems(data ? data.items : "error");
    } catch {
      setItems("error");
    }
  }, [api]);
  useEffect(() => {
    void load();
  }, [load]);

  const fail = (code?: string) => setProblem(t(`policies.errors.${(code && ERROR_KEYS[code]) || "generic"}`));

  async function openDraft(draft: Summary, mode: "editor" | "preview") {
    setProblem(undefined);
    const { data } = await api.GET("/api/v1/admin/policy-versions/{id}/preview", { params: { path: { id: draft.id } } });
    if (!data) return fail();
    setPanel(
      mode === "editor"
        ? { kind: "editor", type: draft.type, draftId: draft.id, title: data.summary.title, markdown: data.markdown }
        : { kind: "preview", title: data.summary.title, blocks: data.blocks },
    );
  }

  async function saveDraft(panel: Extract<Panel, { kind: "editor" }>) {
    setProblem(undefined);
    const body = { title: panel.title, markdown: panel.markdown };
    const { response, error } = panel.draftId
      ? await api.PUT("/api/v1/admin/policy-drafts/{id}", { params: { path: { id: panel.draftId } }, body })
      : await api.POST("/api/v1/admin/policies/{type}/draft", { params: { path: { type: panel.type as PolicyType as never } }, body });
    if (!response.ok) return fail((error as { code?: string } | undefined)?.code);
    setPanel(undefined);
    await load();
  }

  async function discard(draft: Summary) {
    setProblem(undefined);
    const { response, error } = await api.DELETE("/api/v1/admin/policy-drafts/{id}", { params: { path: { id: draft.id } } });
    if (!response.ok) return fail((error as { code?: string } | undefined)?.code);
    setPanel(undefined);
    await load();
  }

  async function publish(draftId: string) {
    setProblem(undefined);
    const { response, error } = await api.POST("/api/v1/admin/policy-drafts/{id}/publish", { params: { path: { id: draftId } } });
    if (!response.ok) return fail((error as { code?: string } | undefined)?.code);
    setPanel(undefined);
    await load();
  }

  if (items === undefined) return <p>{t("auth.loading")}</p>;
  if (items === "error") return <p role="alert">{t("policies.loadError")}</p>;

  return (
    <>
      <h1>{t("policies.title")}</h1>
      {problem && <p role="alert">{problem}</p>}
      {TYPES.map((type) => {
        const mine = items.filter((i) => i.type === type);
        const draft = mine.find((i) => i.status === "DRAFT");
        const name = t(`consents.types.${type}`);
        return (
          <section key={type} aria-label={name}>
            <h2>{name}</h2>
            <ul>
              {mine.map((v) => (
                <li key={v.id}>
                  {v.status === "DRAFT" ? (
                    <>
                      <strong>{t("policies.draft")}</strong> {v.title}
                    </>
                  ) : (
                    <>
                      {t("policies.version", { version: v.version })} <strong>{t("policies.published")}</strong> {v.title}{" "}
                      <small>{v.accepted === 1 ? t("policies.acceptedOne") : t("policies.acceptedMany", { count: v.accepted })}</small>
                    </>
                  )}
                </li>
              ))}
            </ul>
            {draft ? (
              <>
                <button type="button" onClick={() => void openDraft(draft, "editor")}>{t("policies.editDraft")}</button>
                <button type="button" onClick={() => void openDraft(draft, "preview")}>{t("policies.preview")}</button>
                <button type="button" onClick={() => { setProblem(undefined); setPanel({ kind: "publish", type, draftId: draft.id }); }}>{t("policies.publish")}</button>
                <button type="button" onClick={() => void discard(draft)}>{t("policies.discard")}</button>
              </>
            ) : (
              <button type="button" onClick={() => { setProblem(undefined); setPanel({ kind: "editor", type, title: "", markdown: "" }); }}>{t("policies.newDraft")}</button>
            )}
            {panel?.kind === "editor" && panel.type === type && (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  void saveDraft(panel);
                }}
              >
                <label htmlFor="policy-title">{t("policies.fieldTitle")}</label>
                <input id="policy-title" value={panel.title} maxLength={120} onChange={(e) => setPanel({ ...panel, title: e.target.value })} />
                <label htmlFor="policy-markdown">{t("policies.fieldMarkdown")}</label>
                <p>{t("policies.markdownHelp")}</p>
                <textarea id="policy-markdown" rows={12} value={panel.markdown} onChange={(e) => setPanel({ ...panel, markdown: e.target.value })} />
                <button type="submit">{t("policies.saveDraft")}</button>
                <button type="button" onClick={() => setPanel(undefined)}>{t("policies.close")}</button>
              </form>
            )}
            {panel?.kind === "publish" && panel.type === type && (
              <div>
                <p>{REQUIRED.has(type) ? t("policies.publishRequired") : t("policies.publishOptional")}</p>
                <button type="button" onClick={() => void publish(panel.draftId)}>{t("policies.confirmPublish")}</button>
                <button type="button" onClick={() => setPanel(undefined)}>{t("policies.close")}</button>
              </div>
            )}
            {panel?.kind === "preview" && draft && mine.includes(draft) && (
              <div>
                <h3>{t("policies.previewTitle", { title: panel.title })}</h3>
                <PolicyBlocks blocks={panel.blocks} />
                <button type="button" onClick={() => setPanel(undefined)}>{t("policies.close")}</button>
              </div>
            )}
          </section>
        );
      })}
    </>
  );
}
