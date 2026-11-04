import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ALL_TYPES, consentItem, fakeAuthenticator, meRoute, policyOf, renderApp, routedBackend } from "../test-utils";

/** The seven consents; `pending` lists the types whose current version the person has not accepted. */
function backend(pending: string[], over: Record<string, Partial<Parameters<typeof consentItem>[1]>> = {}) {
  const state = { pending: new Set(pending) };
  const api = routedBackend({
    "GET /api/v1/me": meRoute(),
    "GET /api/v1/me/consents": () => ({
      status: 200,
      body: { items: ALL_TYPES.map((t) => consentItem(t, { pending: state.pending.has(t), granted: !state.pending.has(t), ...over[t] })) },
    }),
    "POST /api/v1/me/required-consents": ({ body }) => {
      const ids = (body as { versionIds: string[] }).versionIds;
      // like the backend: the three current versions of the required texts, no more and no less
      const all = ["TERMS_OF_USE", "PRIVACY_POLICY", "HEALTH_DATA_CONSENT"].map((t) => `v-${t}`);
      if (ids.length !== 3 || !all.every((id) => ids.includes(id))) return { status: 409, body: { code: "policy_version_outdated" } };
      state.pending = new Set([...state.pending].filter((t) => !ids.includes(`v-${t}`)));
      return { status: 204 };
    },
    "GET /api/v1/me/anamnesis": { status: 404, body: { code: "not_found" } },
    "GET /api/v1/catalogs/OBJECTIVE": { status: 200, body: { kind: "OBJECTIVE", codes: [] } },
  });
  for (const t of ALL_TYPES) api.routes[`GET /api/v1/policies/${t}/current`] = { status: 200, body: policyOf(t) };
  return api;
}

describe("acceptance gate (E2-S10.1, CA1, CA3)", () => {
  it("first access: shows the pending texts and does not reach the anamnesis until they are accepted", async () => {
    const api = backend(["TERMS_OF_USE", "PRIVACY_POLICY", "HEALTH_DATA_CONSENT"], {
      TERMS_OF_USE: { granted: false }, PRIVACY_POLICY: { granted: false }, HEALTH_DATA_CONSENT: { granted: false },
    });
    await renderApp("/anamnesis", { authenticator: fakeAuthenticator("tok"), fetch: api.fetch });

    expect(await screen.findByRole("heading", { name: "Antes de continuar" })).toBeInTheDocument();
    expect(await screen.findByText("Texto de TERMS_OF_USE")).toBeInTheDocument();
    expect(screen.getByText("Texto de HEALTH_DATA_CONSENT")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Sua anamnese" })).not.toBeInTheDocument();
    expect(api.called("GET /api/v1/me/anamnesis")).toHaveLength(0);
  });

  it("the accept button stays off until every text is checked, then sends the version ids and the client", async () => {
    const api = backend(["TERMS_OF_USE", "PRIVACY_POLICY"]);
    await renderApp("/", { authenticator: fakeAuthenticator("tok"), fetch: api.fetch });
    const button = await screen.findByRole("button", { name: "Aceitar e continuar" });
    await screen.findByText("Texto de PRIVACY_POLICY");
    expect(button).toBeDisabled();

    await userEvent.click(screen.getByRole("checkbox", { name: /Título de TERMS_OF_USE/ }));
    expect(button).toBeDisabled();
    await userEvent.click(screen.getByRole("checkbox", { name: /Título de PRIVACY_POLICY/ }));
    expect(button).toBeEnabled();
    await userEvent.click(button);

    await waitFor(() => expect(screen.queryByRole("heading", { name: "Antes de continuar" })).not.toBeInTheDocument());
    expect(api.called("POST /api/v1/me/required-consents")[0]!.body).toEqual({
      versionIds: ["v-TERMS_OF_USE", "v-PRIVACY_POLICY", "v-HEALTH_DATA_CONSENT"], // all three, even if only two were pending
      client: "WEB",
    });
    expect(screen.getByRole("heading", { name: "Heracles" })).toBeInTheDocument();
  });

  it("a new version reopens the gate for a person who had accepted the old one, and says so", async () => {
    const api = backend(["HEALTH_DATA_CONSENT"], { HEALTH_DATA_CONSENT: { acceptedVersionId: "old", currentVersion: 2 } });
    await renderApp("/", { authenticator: fakeAuthenticator("tok"), fetch: api.fetch });

    expect(await screen.findByRole("heading", { name: "Antes de continuar" })).toBeInTheDocument();
    expect(screen.getByText(/mudaram/)).toBeInTheDocument();
    expect(screen.queryByText("Texto de TERMS_OF_USE")).not.toBeInTheDocument(); // only what is pending
  });

  it("nothing pending: the page opens with no gate; a pending optional consent never blocks", async () => {
    const api = backend(["AI_EXAMS_CONSENT"]);
    await renderApp("/", { authenticator: fakeAuthenticator("tok"), fetch: api.fetch });
    expect(await screen.findByRole("heading", { name: "Heracles" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Antes de continuar" })).not.toBeInTheDocument();
  });

  it("a signed-out visitor never sees the gate and the API is not called", async () => {
    const api = backend(["TERMS_OF_USE"]);
    await renderApp("/", { authenticator: fakeAuthenticator(undefined), fetch: api.fetch });
    expect(screen.queryByRole("heading", { name: "Antes de continuar" })).not.toBeInTheDocument();
    expect(api.calls).toHaveLength(0);
  });

  it("keeps the person out when the state of the consents cannot be loaded, instead of opening the app", async () => {
    const api = backend([]);
    api.routes["GET /api/v1/me/consents"] = { status: 500, body: { code: "internal_error" } };
    await renderApp("/anamnesis", { authenticator: fakeAuthenticator("tok"), fetch: api.fetch });
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível carregar seus consentimentos");
    expect(screen.queryByRole("heading", { name: "Sua anamnese" })).not.toBeInTheDocument();
  });

  it("shows a message and keeps the gate when the acceptance is refused", async () => {
    const api = backend(["TERMS_OF_USE"]);
    api.routes["POST /api/v1/me/required-consents"] = { status: 409, body: { code: "stale_version" } };
    await renderApp("/", { authenticator: fakeAuthenticator("tok"), fetch: api.fetch });
    await userEvent.click(await screen.findByRole("checkbox", { name: /Título de TERMS_OF_USE/ }));
    await userEvent.click(screen.getByRole("button", { name: "Aceitar e continuar" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível registrar o aceite");
    expect(screen.getByRole("heading", { name: "Antes de continuar" })).toBeInTheDocument();
  });
});
