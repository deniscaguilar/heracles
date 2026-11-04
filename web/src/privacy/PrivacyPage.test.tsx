import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { ALL_TYPES, consentItem, fakeAuthenticator, meRoute, renderApp, routedBackend } from "../test-utils";
import { timing } from "./PrivacyPage";

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
  await renderApp("/privacy", { authenticator: fakeAuthenticator("tok"), fetch: api.fetch });
  await screen.findByRole("heading", { name: "Privacidade" });
  return api;
}

afterEach(() => {
  timing.exportPollMs = 2000;
});

describe("Privacidade (E2-S10.3, CA13, CA14)", () => {
  it("asks for the export and offers the link when the file is ready", async () => {
    timing.exportPollMs = 10;
    const api = await open();
    await userEvent.click(screen.getByRole("button", { name: "Exportar meus dados" }));
    const link = await screen.findByRole("link", { name: "Baixar meus dados" });
    expect(link).toHaveAttribute("href", "https://files.example/export.json?sig=abc");
    expect(screen.getByText(/15 minutos/)).toBeInTheDocument();
    expect(api.called("POST /api/v1/me/exports")).toHaveLength(1);
  });

  it("waits while the file is not ready and keeps asking", async () => {
    timing.exportPollMs = 10;
    const api = backend();
    let attempts = 0;
    api.routes["GET /api/v1/me/exports/task-1"] = () =>
      ++attempts < 3
        ? { status: 409, body: { code: "export_not_ready" } }
        : { status: 200, body: { url: "https://files.example/e.json", fileExpiresAt: "2026-11-05T12:00:00Z" } };
    await open(api);
    await userEvent.click(screen.getByRole("button", { name: "Exportar meus dados" }));
    expect(await screen.findByText("Preparando o seu arquivo…")).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "Baixar meus dados" })).toBeInTheDocument();
    expect(attempts).toBe(3);
  });

  it("says the file expired and lets the person ask again", async () => {
    timing.exportPollMs = 10;
    const api = backend();
    api.routes["GET /api/v1/me/exports/task-1"] = { status: 410, body: { code: "export_expired" } };
    await open(api);
    await userEvent.click(screen.getByRole("button", { name: "Exportar meus dados" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("O arquivo expirou");
    expect(screen.getByRole("button", { name: "Exportar meus dados" })).toBeEnabled();
  });

  it("deleting needs a confirmation that says what happens and the 7 days; nothing is sent before it", async () => {
    const api = await open();
    await userEvent.click(screen.getByRole("button", { name: "Excluir minha conta" }));

    expect(screen.getByRole("heading", { name: "Excluir minha conta?" })).toBeInTheDocument();
    expect(screen.getByText(/7 dias/)).toBeInTheDocument();
    expect(api.called("POST /api/v1/me/erasure-requests")).toHaveLength(0);

    await userEvent.click(screen.getByRole("button", { name: "Voltar" }));
    expect(screen.queryByRole("heading", { name: "Excluir minha conta?" })).not.toBeInTheDocument();
    expect(api.called("POST /api/v1/me/erasure-requests")).toHaveLength(0);
  });

  it("after confirming, shows the date it will happen and the way to cancel", async () => {
    const api = await open();
    await userEvent.click(screen.getByRole("button", { name: "Excluir minha conta" }));
    await userEvent.click(screen.getByRole("button", { name: "Confirmar exclusão" }));

    expect(await screen.findByRole("heading", { name: "Exclusão agendada" })).toBeInTheDocument();
    expect(screen.getByText(/11\/11\/2026/)).toBeInTheDocument();
    expect(api.called("POST /api/v1/me/erasure-requests")).toHaveLength(1);
    expect(screen.queryByRole("button", { name: "Excluir minha conta" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Cancelar a exclusão" }));
    await waitFor(() => expect(api.called("DELETE /api/v1/me/erasure-requests/current")).toHaveLength(1));
    expect(await screen.findByRole("button", { name: "Excluir minha conta" })).toBeInTheDocument();
  });

  it("opening the page with a pending erasure shows it right away", async () => {
    const api = backend();
    api.routes["GET /api/v1/me/erasure-requests/current"] = { status: 200, body: erasure };
    await open(api);
    expect(await screen.findByRole("heading", { name: "Exclusão agendada" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Confirmar exclusão" })).not.toBeInTheDocument();
  });

  it("a running erasure can no longer be canceled", async () => {
    const api = backend();
    api.routes["GET /api/v1/me/erasure-requests/current"] = { status: 200, body: { ...erasure, status: "RUNNING" } };
    await open(api);
    expect(await screen.findByText("A exclusão está em andamento.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancelar a exclusão" })).not.toBeInTheDocument();
  });

  it("tells the person when the erasure could not be requested", async () => {
    const api = backend();
    api.routes["POST /api/v1/me/erasure-requests"] = { status: 500, body: { code: "internal_error" } };
    await open(api);
    await userEvent.click(screen.getByRole("button", { name: "Excluir minha conta" }));
    await userEvent.click(screen.getByRole("button", { name: "Confirmar exclusão" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível agendar a exclusão");
  });
});
