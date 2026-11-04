import { fireEvent, screen } from "@testing-library/react-native";
import { ALL_TYPES, REQUIRED_TYPES, consentItem, fakeAuthenticator, meRoute, policyOf, renderShell, routedBackend } from "../test-utils";

function backend(pending: string[], over: Record<string, Parameters<typeof consentItem>[1]> = {}) {
  const state = { pending: new Set(pending) };
  const api = routedBackend({
    "GET /api/v1/me": meRoute(),
    "GET /api/v1/me/consents": () => ({
      status: 200,
      body: { items: ALL_TYPES.map((t) => consentItem(t, { pending: state.pending.has(t), granted: !state.pending.has(t), ...over[t] })) },
    }),
    "POST /api/v1/me/required-consents": ({ body }) => {
      const ids = (body as { versionIds: string[] }).versionIds;
      // like the backend: the current versions of the three required texts, no more and no less
      const all = REQUIRED_TYPES.map((t) => `v-${t}`);
      if (ids.length !== 3 || !all.every((id) => ids.includes(id))) return { status: 409, body: { code: "policy_version_outdated" } };
      state.pending = new Set();
      return { status: 204 };
    },
  });
  for (const t of ALL_TYPES) api.routes[`GET /api/v1/policies/${t}/current`] = { status: 200, body: policyOf(t) };
  return api;
}

const start = (api: ReturnType<typeof backend>) => renderShell({ authenticator: fakeAuthenticator("tok"), fetch: api.fetch });

describe("acceptance gate (E2-S11.1, CA1, CA3)", () => {
  it("first access: shows the pending texts and none of the app", async () => {
    await start(backend(REQUIRED_TYPES));
    expect(await screen.findByRole("header", { name: "Antes de continuar" })).toBeOnTheScreen();
    expect(await screen.findByText("Texto de HEALTH_DATA_CONSENT")).toBeOnTheScreen();
    expect(screen.queryByText("Anamnese")).not.toBeOnTheScreen();
    expect(screen.queryByText(/Você entrou como/)).not.toBeOnTheScreen();
  });

  it("the accept button stays off until every text is checked; then sends the three ids and the client", async () => {
    const api = backend(["TERMS_OF_USE", "PRIVACY_POLICY"]);
    await start(api);
    await screen.findByText("Texto de PRIVACY_POLICY");
    const button = screen.getByRole("button", { name: "Aceitar e continuar" });
    expect(button).toBeDisabled();
    await fireEvent.press(screen.getByRole("checkbox", { name: /Título de TERMS_OF_USE/ }));
    expect(button).toBeDisabled();
    await fireEvent.press(screen.getByRole("checkbox", { name: /Título de PRIVACY_POLICY/ }));
    expect(button).toBeEnabled();
    await fireEvent.press(button);

    expect(await screen.findByText(/Você entrou como/)).toBeOnTheScreen();
    expect(api.called("POST /api/v1/me/required-consents")[0]!.body).toEqual({
      versionIds: ["v-TERMS_OF_USE", "v-PRIVACY_POLICY", "v-HEALTH_DATA_CONSENT"], // all three, even if only two were pending
      client: "MOBILE",
    });
  });

  it("a new version reopens the gate for someone who had accepted the old one, and says so", async () => {
    await start(backend(["HEALTH_DATA_CONSENT"], { HEALTH_DATA_CONSENT: { acceptedVersionId: "old", currentVersion: 2 } }));
    expect(await screen.findByRole("header", { name: "Antes de continuar" })).toBeOnTheScreen();
    expect(screen.getByText(/mudaram/)).toBeOnTheScreen();
    expect(screen.queryByText("Texto de TERMS_OF_USE")).not.toBeOnTheScreen();
  });

  it("nothing pending: no gate; a pending optional consent never blocks", async () => {
    await start(backend(["AI_EXAMS_CONSENT"]));
    expect(await screen.findByText(/Você entrou como/)).toBeOnTheScreen();
    expect(screen.queryByRole("header", { name: "Antes de continuar" })).not.toBeOnTheScreen();
  });

  it("keeps the person out when the consents cannot be loaded, instead of opening the app", async () => {
    const api = backend([]);
    api.routes["GET /api/v1/me/consents"] = { status: 500, body: { code: "internal_error" } };
    await start(api);
    expect(await screen.findByRole("alert", { name: /Não foi possível carregar seus consentimentos/ })).toBeOnTheScreen();
    expect(screen.queryByText(/Você entrou como/)).not.toBeOnTheScreen();
  });

  it("keeps the gate and says so when the acceptance is refused", async () => {
    const api = backend(["TERMS_OF_USE"]);
    api.routes["POST /api/v1/me/required-consents"] = { status: 409, body: { code: "policy_version_outdated" } };
    await start(api);
    await fireEvent.press(await screen.findByRole("checkbox", { name: /Título de TERMS_OF_USE/ }));
    await fireEvent.press(screen.getByRole("button", { name: "Aceitar e continuar" }));
    expect(await screen.findByRole("alert", { name: /Não foi possível registrar o aceite/ })).toBeOnTheScreen();
    expect(screen.getByRole("header", { name: "Antes de continuar" })).toBeOnTheScreen();
  });
});
