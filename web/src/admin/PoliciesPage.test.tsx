import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ALL_TYPES, consentItem, fakeAuthenticator, meRoute, renderApp, routedBackend } from "../test-utils";

const version = (over: Record<string, unknown>) => ({
  id: "x", type: "TERMS_OF_USE", version: 1, title: "Termos", status: "PUBLISHED",
  createdAt: "2026-10-01T10:00:00Z", publishedAt: "2026-10-01T10:00:00Z", accepted: 0, ...over,
});

function backend(items: unknown[], role = "ADMIN") {
  const state = { items };
  const api = routedBackend({
    "GET /api/v1/me": meRoute(role),
    "GET /api/v1/me/consents": { status: 200, body: { items: ALL_TYPES.map((t) => consentItem(t, { granted: true })) } },
    "GET /api/v1/admin/policies": () => ({ status: 200, body: { items: state.items } }),
    "POST /api/v1/admin/policies/TERMS_OF_USE/draft": { status: 201, body: { id: "draft-new" } },
    "PUT /api/v1/admin/policy-drafts/draft-1": { status: 204 },
    "DELETE /api/v1/admin/policy-drafts/draft-1": () => {
      state.items = state.items.filter((i) => (i as { id: string }).id !== "draft-1");
      return { status: 204 };
    },
    "POST /api/v1/admin/policy-drafts/draft-1/publish": () => {
      state.items = [version({ id: "v2", version: 2, accepted: 0 }), version({ id: "v1", version: 1, accepted: 5 })];
      return { status: 200, body: { id: "v2", version: 2 } };
    },
    "GET /api/v1/admin/policy-versions/draft-1/preview": {
      status: 200,
      body: {
        summary: version({ id: "draft-1", version: null, status: "DRAFT", publishedAt: null, title: "Termos novos" }),
        markdown: "Texto **novo** do rascunho",
        blocks: [{ type: "paragraph", children: [{ type: "text", text: "Texto " }, { type: "strong", children: [{ type: "text", text: "novo" }] }, { type: "text", text: " do rascunho" }] }],
      },
    },
  });
  return api;
}

const published = [version({ id: "v1", version: 1, accepted: 12 })];
const withDraft = [...published, version({ id: "draft-1", version: null, status: "DRAFT", publishedAt: null, title: "Termos novos", accepted: 0 })];

async function open(api: ReturnType<typeof backend>) {
  await renderApp("/admin/policies", { authenticator: fakeAuthenticator("tok"), fetch: api.fetch });
}
const section = (name: string) => screen.getByRole("region", { name });

describe("/admin/policies (E2-S10.4, CA1, CA3)", () => {
  it("lists the versions of each text with their status and how many people accepted", async () => {
    await open(backend(withDraft));
    const terms = await screen.findByRole("region", { name: "Termos de uso" });
    expect(within(terms).getByText(/versão 1/)).toBeInTheDocument();
    expect(within(terms).getByText("Publicada")).toBeInTheDocument();
    expect(within(terms).getByText("12 aceites")).toBeInTheDocument();
    expect(within(terms).getByText("Rascunho")).toBeInTheDocument();
    expect(screen.getAllByRole("region")).toHaveLength(7);
  });

  it("only an administrator gets in: a regular user and a moderator are refused and no list is asked for", async () => {
    for (const role of ["USER", "MODERATOR"]) {
      const api = backend(published, role);
      const { unmount } = await renderApp("/admin/policies", { authenticator: fakeAuthenticator("tok"), fetch: api.fetch });
      expect(await screen.findByRole("heading", { name: "Acesso negado" })).toBeInTheDocument();
      expect(api.called("GET /api/v1/admin/policies")).toHaveLength(0);
      unmount();
    }
  });

  it("creates a draft from a title and a restricted Markdown text", async () => {
    const api = backend(published);
    await open(api);
    await userEvent.click(within(await screen.findByRole("region", { name: "Termos de uso" })).getByRole("button", { name: "Novo rascunho" }));
    await userEvent.type(screen.getByLabelText("Título"), "Termos 2");
    await userEvent.type(screen.getByLabelText("Texto (Markdown)"), "Primeiro parágrafo");
    await userEvent.click(screen.getByRole("button", { name: "Salvar rascunho" }));
    await waitFor(() => expect(api.called("POST /api/v1/admin/policies/TERMS_OF_USE/draft")).toHaveLength(1));
    expect(api.called("POST /api/v1/admin/policies/TERMS_OF_USE/draft")[0]!.body).toEqual({ title: "Termos 2", markdown: "Primeiro parágrafo" });
  });

  it("edits an existing draft starting from its text", async () => {
    const api = backend(withDraft);
    await open(api);
    await userEvent.click(within(await screen.findByRole("region", { name: "Termos de uso" })).getByRole("button", { name: "Editar rascunho" }));
    const text = await screen.findByLabelText("Texto (Markdown)");
    expect(text).toHaveValue("Texto **novo** do rascunho");
    expect(screen.getByLabelText("Título")).toHaveValue("Termos novos");
    await userEvent.type(text, " e mais");
    await userEvent.click(screen.getByRole("button", { name: "Salvar rascunho" }));
    await waitFor(() => expect(api.called("PUT /api/v1/admin/policy-drafts/draft-1")).toHaveLength(1));
    expect(api.called("PUT /api/v1/admin/policy-drafts/draft-1")[0]!.body).toEqual({ title: "Termos novos", markdown: "Texto **novo** do rascunho e mais" });
  });

  it("previews the draft the way the person will read it", async () => {
    await open(backend(withDraft));
    await userEvent.click(within(await screen.findByRole("region", { name: "Termos de uso" })).getByRole("button", { name: "Pré-visualizar" }));
    const preview = await screen.findByRole("heading", { name: "Pré-visualização: Termos novos" });
    expect(preview).toBeInTheDocument();
    expect(screen.getByText("novo").tagName).toBe("STRONG");
  });

  it("publishing asks for confirmation, says everyone must accept again, then shows the new version", async () => {
    const api = backend(withDraft);
    await open(api);
    await userEvent.click(within(await screen.findByRole("region", { name: "Termos de uso" })).getByRole("button", { name: "Publicar" }));
    expect(screen.getByText(/precisarão aceitar de novo/)).toBeInTheDocument();
    expect(api.called("POST /api/v1/admin/policy-drafts/draft-1/publish")).toHaveLength(0);

    await userEvent.click(screen.getByRole("button", { name: "Confirmar publicação" }));
    await waitFor(() => expect(api.called("POST /api/v1/admin/policy-drafts/draft-1/publish")).toHaveLength(1));
    expect(await within(section("Termos de uso")).findByText(/versão 2/)).toBeInTheDocument();
    expect(within(section("Termos de uso")).queryByText("Rascunho")).not.toBeInTheDocument();
  });

  it("an optional text is published without the warning about accepting again", async () => {
    const api = backend([version({ id: "draft-1", type: "COMMUNITY_CONSENT", version: null, status: "DRAFT", publishedAt: null })]);
    await open(api);
    await userEvent.click(within(await screen.findByRole("region", { name: "Comunidade" })).getByRole("button", { name: "Publicar" }));
    expect(screen.queryByText(/precisarão aceitar de novo/)).not.toBeInTheDocument();
    expect(screen.getByText(/quem tinha ativado/)).toBeInTheDocument();
  });

  it("discards a draft", async () => {
    const api = backend(withDraft);
    await open(api);
    await userEvent.click(within(await screen.findByRole("region", { name: "Termos de uso" })).getByRole("button", { name: "Descartar rascunho" }));
    await waitFor(() => expect(api.called("DELETE /api/v1/admin/policy-drafts/draft-1")).toHaveLength(1));
    await waitFor(() => expect(within(section("Termos de uso")).queryByText("Rascunho")).not.toBeInTheDocument());
  });

  it("explains a refusal: text outside the allowed format, and a draft that already exists", async () => {
    const api = backend(published);
    api.routes["POST /api/v1/admin/policies/TERMS_OF_USE/draft"] = { status: 400, body: { code: "policy_text_invalid" } };
    await open(api);
    await userEvent.click(within(await screen.findByRole("region", { name: "Termos de uso" })).getByRole("button", { name: "Novo rascunho" }));
    await userEvent.type(screen.getByLabelText("Título"), "T");
    await userEvent.type(screen.getByLabelText("Texto (Markdown)"), "x");
    await userEvent.click(screen.getByRole("button", { name: "Salvar rascunho" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("O texto usa algo fora do formato permitido");

    api.routes["POST /api/v1/admin/policies/TERMS_OF_USE/draft"] = { status: 409, body: { code: "draft_already_exists" } };
    await userEvent.click(screen.getByRole("button", { name: "Salvar rascunho" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Já existe um rascunho"));
  });
});
