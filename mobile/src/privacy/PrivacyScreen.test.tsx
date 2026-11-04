import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { Linking } from "react-native";
import { ALL_TYPES, consentItem, fakeAuthenticator, meRoute, renderShell, routedBackend } from "../test-utils";
import { timing } from "./PrivacyScreen";

const erasure = { requestedAt: "2026-11-04T12:00:00Z", dueAt: "2026-11-11T12:00:00Z", status: "PENDING" };

function backend() {
  return routedBackend({
    "GET /api/v1/me": meRoute(),
    "GET /api/v1/me/consents": { status: 200, body: { items: ALL_TYPES.map((t) => consentItem(t, { granted: true })) } },
    "GET /api/v1/me/erasure-requests/current": { status: 404, body: { code: "erasure_not_found" } },
    "POST /api/v1/me/exports": { status: 202, body: { taskId: "task-1" } },
    "GET /api/v1/me/exports/task-1": { status: 200, body: { url: "https://files.example/export.json?sig=abc", fileExpiresAt: "2026-11-05T12:00:00Z" } },
    "POST /api/v1/me/erasure-requests": { status: 202, body: erasure },
    "DELETE /api/v1/me/erasure-requests/current": { status: 204 },
  });
}

async function open(api = backend()) {
  await renderShell({ authenticator: fakeAuthenticator("tok"), fetch: api.fetch });
  await fireEvent.press(await screen.findByRole("button", { name: "Privacidade" }));
  await screen.findByRole("header", { name: "Privacidade" });
  return api;
}

beforeEach(() => {
  jest.clearAllMocks();
  jest.spyOn(Linking, "openURL").mockResolvedValue();
  timing.exportPollMs = 10;
});
afterEach(() => {
  jest.restoreAllMocks();
  timing.exportPollMs = 2000;
});

describe("Privacidade (E2-S11.2, CA13, CA14)", () => {
  it("asks for the export and opens the link when the file is ready", async () => {
    const api = await open();
    await fireEvent.press(screen.getByRole("button", { name: "Exportar meus dados" }));
    await fireEvent.press(await screen.findByRole("button", { name: "Baixar meus dados" }));
    expect(Linking.openURL).toHaveBeenCalledWith("https://files.example/export.json?sig=abc");
    expect(screen.getByText(/15 minutos/)).toBeOnTheScreen();
    expect(api.called("POST /api/v1/me/exports")).toHaveLength(1);
  });

  it("waits while the file is not ready and keeps asking", async () => {
    const api = backend();
    let attempts = 0;
    api.routes["GET /api/v1/me/exports/task-1"] = () =>
      ++attempts < 3 ? { status: 409, body: { code: "export_not_ready" } } : { status: 200, body: { url: "https://files.example/e.json", fileExpiresAt: "2026-11-05T12:00:00Z" } };
    await open(api);
    await fireEvent.press(screen.getByRole("button", { name: "Exportar meus dados" }));
    expect(await screen.findByText("Preparando o seu arquivo…")).toBeOnTheScreen();
    expect(await screen.findByRole("button", { name: "Baixar meus dados" })).toBeOnTheScreen();
    expect(attempts).toBe(3);
  });

  it("says the file expired and lets the person ask again", async () => {
    const api = backend();
    api.routes["GET /api/v1/me/exports/task-1"] = { status: 410, body: { code: "export_expired" } };
    await open(api);
    await fireEvent.press(screen.getByRole("button", { name: "Exportar meus dados" }));
    expect(await screen.findByRole("alert", { name: /O arquivo expirou/ })).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Exportar meus dados" })).toBeEnabled();
  });

  it("deleting needs a confirmation that says what happens and the 7 days; nothing is sent before it", async () => {
    const api = await open();
    await fireEvent.press(screen.getByRole("button", { name: "Excluir minha conta" }));
    expect(screen.getByRole("header", { name: "Excluir minha conta?" })).toBeOnTheScreen();
    expect(screen.getByText(/7 dias/)).toBeOnTheScreen();
    expect(api.called("POST /api/v1/me/erasure-requests")).toHaveLength(0);

    await fireEvent.press(screen.getByRole("button", { name: "Voltar" }));
    expect(screen.queryByRole("header", { name: "Excluir minha conta?" })).not.toBeOnTheScreen();
    expect(api.called("POST /api/v1/me/erasure-requests")).toHaveLength(0);
  });

  it("after confirming, shows the date it will happen and the way to cancel", async () => {
    const api = await open();
    await fireEvent.press(screen.getByRole("button", { name: "Excluir minha conta" }));
    await fireEvent.press(screen.getByRole("button", { name: "Confirmar exclusão" }));
    expect(await screen.findByRole("header", { name: "Exclusão agendada" })).toBeOnTheScreen();
    expect(screen.getByText(/11\/11\/2026/)).toBeOnTheScreen();
    expect(api.called("POST /api/v1/me/erasure-requests")).toHaveLength(1);

    await fireEvent.press(screen.getByRole("button", { name: "Cancelar a exclusão" }));
    await waitFor(() => expect(api.called("DELETE /api/v1/me/erasure-requests/current")).toHaveLength(1));
    expect(await screen.findByRole("button", { name: "Excluir minha conta" })).toBeOnTheScreen();
  });

  it("opening the screen with a pending erasure shows it right away; a running one cannot be canceled", async () => {
    const api = backend();
    api.routes["GET /api/v1/me/erasure-requests/current"] = { status: 200, body: { ...erasure, status: "RUNNING" } };
    await open(api);
    expect(await screen.findByText("A exclusão está em andamento.")).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: "Cancelar a exclusão" })).not.toBeOnTheScreen();
  });

  it("tells the person when the erasure could not be requested", async () => {
    const api = backend();
    api.routes["POST /api/v1/me/erasure-requests"] = { status: 500, body: { code: "internal_error" } };
    await open(api);
    await fireEvent.press(screen.getByRole("button", { name: "Excluir minha conta" }));
    await fireEvent.press(screen.getByRole("button", { name: "Confirmar exclusão" }));
    expect(await screen.findByRole("alert", { name: /Não foi possível agendar a exclusão/ })).toBeOnTheScreen();
  });
});
