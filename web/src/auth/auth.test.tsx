import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { fakeAuthenticator, fakeBackend, renderApp } from "../test-utils";
import { oidcAuthenticator, type UserManagerLike } from "./authenticator";

const me = (role: string) => ({ status: 200, body: { id: "11111111-1111-1111-1111-111111111111", role } });

describe("signed out", () => {
  it("offers sign in, shows no admin link and never calls the API", async () => {
    const backend = fakeBackend(me("USER"));
    const authenticator = fakeAuthenticator(undefined);
    await renderApp("/", { authenticator, fetch: backend.fetch });

    await userEvent.click(screen.getByRole("button", { name: "Entrar" }));
    expect(authenticator.startLogin).toHaveBeenCalledOnce();
    expect(screen.queryByRole("link", { name: "Administração" })).not.toBeInTheDocument();
    expect(backend.authorizations).toHaveLength(0);
  });

  it("asks to sign in on /admin instead of showing it", async () => {
    await renderApp("/admin", { authenticator: fakeAuthenticator(undefined) });
    expect(screen.getByText("Entre para continuar.")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Administração" })).not.toBeInTheDocument();
  });
});

describe("shell by profile (CA20)", () => {
  it("sends the token to GET /api/v1/me and shows the role the backend answered", async () => {
    const backend = fakeBackend(me("USER"));
    await renderApp("/", { authenticator: fakeAuthenticator("abc"), fetch: backend.fetch });
    expect(backend.authorizations).toEqual(["Bearer abc"]);
    expect(screen.getByText("Você entrou como Usuário.")).toBeInTheDocument();
  });

  it("a regular user does not see /admin: no link and the route is refused", async () => {
    const backend = fakeBackend(me("USER"));
    await renderApp("/admin", { authenticator: fakeAuthenticator("abc"), fetch: backend.fetch });
    expect(screen.queryByRole("link", { name: "Administração" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Acesso negado" })).toBeInTheDocument();
    expect(screen.queryByText(/Área do backoffice/)).not.toBeInTheDocument();
  });

  it.each(["ADMIN", "MODERATOR"])("%s sees the admin link and the /admin area", async (role) => {
    const backend = fakeBackend(me(role));
    await renderApp("/admin", { authenticator: fakeAuthenticator("abc"), fetch: backend.fetch });
    expect(screen.getByRole("link", { name: "Administração" })).toBeInTheDocument();
    expect(screen.getByText(/Área do backoffice/)).toBeInTheDocument();
  });

  it("takes the role from the backend, never from the token", async () => {
    // A token that claims to be an administrator is just an opaque string to the app.
    const backend = fakeBackend(me("USER"));
    await renderApp("/admin", { authenticator: fakeAuthenticator("role=ADMIN"), fetch: backend.fetch });
    expect(screen.getByRole("heading", { name: "Acesso negado" })).toBeInTheDocument();
  });

  it("signs out when the API says the token is not valid (401)", async () => {
    const authenticator = fakeAuthenticator("expired");
    await renderApp("/", { authenticator, fetch: fakeBackend({ status: 401 }).fetch });
    expect(authenticator.logout).toHaveBeenCalledOnce();
    expect(screen.getByRole("button", { name: "Entrar" })).toBeInTheDocument();
  });

  it("shows an error, not a profile, when the backend fails or answers an unknown role", async () => {
    await renderApp("/", { authenticator: fakeAuthenticator("abc"), fetch: fakeBackend({ status: 500 }).fetch });
    expect(screen.getByRole("alert")).toHaveTextContent("Não foi possível carregar sua sessão");
    expect(screen.queryByRole("link", { name: "Administração" })).not.toBeInTheDocument();

    document.body.innerHTML = "";
    await renderApp("/", { authenticator: fakeAuthenticator("abc"), fetch: fakeBackend(me("SUPERUSER")).fetch });
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Administração" })).not.toBeInTheDocument();
  });

  it("sign out forgets the session and hides the admin link", async () => {
    const authenticator = fakeAuthenticator("abc");
    await renderApp("/", { authenticator, fetch: fakeBackend(me("ADMIN")).fetch });
    await userEvent.click(screen.getByRole("button", { name: "Sair" }));
    expect(authenticator.logout).toHaveBeenCalledOnce();
    expect(screen.queryByRole("link", { name: "Administração" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Entrar" })).toBeInTheDocument();
  });
});

describe("login callback", () => {
  it("exchanges the code once, loads the profile and goes home", async () => {
    const authenticator = fakeAuthenticator(undefined);
    await renderApp("/auth/callback?code=x&state=y", { authenticator, fetch: fakeBackend(me("ADMIN")).fetch });
    expect(await screen.findByText("Você entrou como Administrador.")).toBeInTheDocument();
    expect(authenticator.completeLogin).toHaveBeenCalledOnce();
    expect(screen.getByRole("link", { name: "Administração" })).toBeInTheDocument();
  });

  it("tells the user when the login could not be completed", async () => {
    const authenticator = fakeAuthenticator(undefined);
    authenticator.completeLogin.mockRejectedValueOnce(new Error("invalid state"));
    await renderApp("/auth/callback?code=x&state=bad", { authenticator });
    expect(await screen.findByRole("alert")).toHaveTextContent("Não foi possível concluir o login");
  });
});

describe("oidc adapter", () => {
  const manager = (user: { access_token: string; expired?: boolean } | null): UserManagerLike => ({
    getUser: async () => user,
    signinRedirect: async () => {},
    signinRedirectCallback: async () => ({}),
    removeUser: async () => {},
  });

  it("gives the token only while it is valid", async () => {
    expect(await oidcAuthenticator(manager({ access_token: "t", expired: false })).getAccessToken()).toBe("t");
    expect(await oidcAuthenticator(manager({ access_token: "t", expired: true })).getAccessToken()).toBeUndefined();
    expect(await oidcAuthenticator(manager(null)).getAccessToken()).toBeUndefined();
  });
});
