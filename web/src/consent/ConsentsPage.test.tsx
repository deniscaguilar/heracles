import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ALL_TYPES, consentItem, fakeAuthenticator, meRoute, policyOf, renderApp, routedBackend } from "../test-utils";

function backend(granted: string[] = []) {
  const state = { granted: new Set(granted) };
  const api = routedBackend({
    "GET /api/v1/me": meRoute(),
    "GET /api/v1/me/consents": () => ({
      status: 200,
      body: {
        items: ALL_TYPES.map((t) => {
          const required = ["TERMS_OF_USE", "PRIVACY_POLICY", "HEALTH_DATA_CONSENT"].includes(t);
          return consentItem(t, { granted: required || state.granted.has(t), acceptedVersionId: required ? `v-${t}` : null, currentVersion: 3 });
        }),
      },
    }),
  });
  for (const t of ALL_TYPES) {
    api.routes[`PUT /api/v1/me/consents/${t}`] = ({ body }) => {
      // like the backend: the version the person saw is always required, to turn on and to turn off
      if (!(body as { versionId?: string }).versionId) return { status: 400, body: { code: "validation_failed", fields: ["versionId"] } };
      if ((body as { granted: boolean }).granted) state.granted.add(t);
      else state.granted.delete(t);
      return { status: 204 };
    };
    api.routes[`GET /api/v1/policies/${t}/current`] = { status: 200, body: policyOf(t) };
  }
  return api;
}

const row = (name: string) => screen.getByRole("listitem", { name });

async function open(api = backend()) {
  await renderApp("/consents", { authenticator: fakeAuthenticator("tok"), fetch: api.fetch });
  await screen.findByRole("heading", { name: "Meus consentimentos" });
  return api;
}

describe("Meus consentimentos (E2-S10.3, CA2, CA4)", () => {
  it("lists the seven consents: the three required ones accepted, the four optional ones off", async () => {
    await open();
    expect(screen.getAllByRole("listitem")).toHaveLength(7);
    expect(within(row("Termos de uso")).getByText("Aceito")).toBeInTheDocument();
    expect(within(row("Uso de IA nos exames")).getByText("Desativado")).toBeInTheDocument();
    expect(within(row("Notificações promocionais")).getByText("Desativado")).toBeInTheDocument();
  });

  it("a required consent has no switch: it is the condition to use the app", async () => {
    await open();
    expect(within(row("Termos de uso")).queryByRole("button", { name: /Ativar|Revogar/ })).not.toBeInTheDocument();
  });

  it("turns an optional consent on in the current version, from the web", async () => {
    const api = await open();
    await userEvent.click(within(row("Comunidade")).getByRole("button", { name: "Ativar" }));
    await waitFor(() => expect(api.called("PUT /api/v1/me/consents/COMMUNITY_CONSENT")).toHaveLength(1));
    expect(api.called("PUT /api/v1/me/consents/COMMUNITY_CONSENT")[0]!.body).toEqual({
      granted: true,
      versionId: "v-COMMUNITY_CONSENT",
      client: "WEB",
    });
    expect(await within(row("Comunidade")).findByText("Ativado")).toBeInTheDocument();
  });

  it("revokes with one click, naming the version it saw", async () => {
    const api = await open(backend(["AI_EXAMS_CONSENT"]));
    expect(within(row("Uso de IA nos exames")).getByText("Ativado")).toBeInTheDocument();
    await userEvent.click(within(row("Uso de IA nos exames")).getByRole("button", { name: "Revogar" }));
    await waitFor(() => expect(api.called("PUT /api/v1/me/consents/AI_EXAMS_CONSENT")).toHaveLength(1));
    expect(api.called("PUT /api/v1/me/consents/AI_EXAMS_CONSENT")[0]!.body).toEqual({ granted: false, versionId: "v-AI_EXAMS_CONSENT", client: "WEB" });
    expect(await within(row("Uso de IA nos exames")).findByText("Desativado")).toBeInTheDocument();
  });

  it("shows the text of the version before it is turned on", async () => {
    await open();
    await userEvent.click(within(row("Notificações")).getByRole("button", { name: "Ler o texto" }));
    expect(await screen.findByText("Texto de NOTIFICATIONS_CONSENT")).toBeInTheDocument();
  });

  it("says so, and keeps the old state, when the change fails", async () => {
    const api = backend();
    api.routes["PUT /api/v1/me/consents/PROMOTIONAL_CONSENT"] = { status: 500, body: { code: "internal_error" } };
    await open(api);
    await userEvent.click(within(row("Notificações promocionais")).getByRole("button", { name: "Ativar" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível alterar o consentimento");
    expect(within(row("Notificações promocionais")).getByText("Desativado")).toBeInTheDocument();
  });

  it("an optional consent whose text was never published cannot be turned on: it says so instead of failing", async () => {
    const api = backend();
    const original = api.routes["GET /api/v1/me/consents"] as () => { status: number; body: { items: Record<string, unknown>[] } };
    api.routes["GET /api/v1/me/consents"] = () => {
      const reply = original();
      reply.body.items = reply.body.items.map((i) => (i.type === "AI_EXAMS_CONSENT" ? { ...i, currentVersionId: null, currentVersion: null } : i));
      return reply;
    };
    await open(api);
    expect(within(row("Uso de IA nos exames")).getByText("Texto ainda não publicado")).toBeInTheDocument();
    expect(within(row("Uso de IA nos exames")).queryByRole("button", { name: "Ativar" })).not.toBeInTheDocument();
    expect(within(row("Uso de IA nos exames")).queryByRole("button", { name: "Ler o texto" })).not.toBeInTheDocument();
  });
});
