import { fireEvent, screen, waitFor, within } from "@testing-library/react-native";
import { ALL_TYPES, REQUIRED_TYPES, consentItem, fakeAuthenticator, meRoute, policyOf, renderShell, routedBackend } from "../test-utils";

function backend(granted: string[] = []) {
  const state = { granted: new Set(granted) };
  const api = routedBackend({
    "GET /api/v1/me": meRoute(),
    "GET /api/v1/me/consents": () => ({
      status: 200,
      body: {
        items: ALL_TYPES.map((t) => {
          const required = REQUIRED_TYPES.includes(t);
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

const row = (name: string) => screen.getByLabelText(name);

async function open(api = backend()) {
  await renderShell({ authenticator: fakeAuthenticator("tok"), fetch: api.fetch });
  await fireEvent.press(await screen.findByRole("button", { name: "Meus consentimentos" }));
  await screen.findByRole("header", { name: "Meus consentimentos" });
  return api;
}

describe("Meus consentimentos (E2-S11.2, CA2, CA4)", () => {
  it("lists the seven consents: the three required ones accepted, the four optional ones off", async () => {
    await open();
    expect(within(row("Termos de uso")).getByText("Aceito")).toBeOnTheScreen();
    expect(within(row("Uso de IA nos exames")).getByText("Desativado")).toBeOnTheScreen();
    expect(within(row("Notificações promocionais")).getByText("Desativado")).toBeOnTheScreen();
    expect(screen.getAllByText(/\(versão 3\)/)).toHaveLength(7);
  });

  it("a required consent has no switch", async () => {
    await open();
    expect(within(row("Termos de uso")).queryByRole("button", { name: /Ativar|Revogar/ })).not.toBeOnTheScreen();
  });

  it("turns an optional consent on in the current version, from the mobile", async () => {
    const api = await open();
    await fireEvent.press(within(row("Comunidade")).getByRole("button", { name: "Ativar" }));
    await waitFor(() => expect(api.called("PUT /api/v1/me/consents/COMMUNITY_CONSENT")).toHaveLength(1));
    expect(api.called("PUT /api/v1/me/consents/COMMUNITY_CONSENT")[0]!.body).toEqual({ granted: true, versionId: "v-COMMUNITY_CONSENT", client: "MOBILE" });
    expect(await within(row("Comunidade")).findByText("Ativado")).toBeOnTheScreen();
  });

  it("revokes with one press, naming the version it saw", async () => {
    const api = await open(backend(["AI_EXAMS_CONSENT"]));
    await fireEvent.press(within(row("Uso de IA nos exames")).getByRole("button", { name: "Revogar" }));
    await waitFor(() => expect(api.called("PUT /api/v1/me/consents/AI_EXAMS_CONSENT")).toHaveLength(1));
    expect(api.called("PUT /api/v1/me/consents/AI_EXAMS_CONSENT")[0]!.body).toEqual({ granted: false, versionId: "v-AI_EXAMS_CONSENT", client: "MOBILE" });
    expect(await within(row("Uso de IA nos exames")).findByText("Desativado")).toBeOnTheScreen();
  });

  it("shows the text of the version before it is turned on", async () => {
    await open();
    await fireEvent.press(within(row("Notificações")).getByRole("button", { name: "Ler o texto" }));
    expect(await screen.findByText("Texto de NOTIFICATIONS_CONSENT")).toBeOnTheScreen();
  });

  it("says so, and keeps the old state, when the change fails", async () => {
    const api = backend();
    api.routes["PUT /api/v1/me/consents/PROMOTIONAL_CONSENT"] = { status: 500, body: { code: "internal_error" } };
    await open(api);
    await fireEvent.press(within(row("Notificações promocionais")).getByRole("button", { name: "Ativar" }));
    expect(await screen.findByRole("alert", { name: /Não foi possível alterar o consentimento/ })).toBeOnTheScreen();
    expect(within(row("Notificações promocionais")).getByText("Desativado")).toBeOnTheScreen();
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
    expect(within(row("Uso de IA nos exames")).getByText("Texto ainda não publicado")).toBeOnTheScreen();
    expect(within(row("Uso de IA nos exames")).queryByRole("button", { name: "Ativar" })).not.toBeOnTheScreen();
    expect(within(row("Uso de IA nos exames")).queryByRole("button", { name: "Ler o texto" })).not.toBeOnTheScreen();
  });
});
