import { fireEvent, screen } from "@testing-library/react-native";
import { fakeAuthenticator, fakeBackend, renderShell } from "./test-utils";

const me = (role: string) => ({ status: 200, body: { id: "11111111-1111-1111-1111-111111111111", role } });

describe("mobile shell (CA20)", () => {
  it("signed out: offers sign in and calls the API only after the login", async () => {
    const backend = fakeBackend(me("USER"));
    const authenticator = fakeAuthenticator(undefined);
    await renderShell({ authenticator, fetch: backend.fetch });

    await fireEvent.press(await screen.findByText("Entrar"));
    expect(authenticator.login).toHaveBeenCalledTimes(1);
    expect(await screen.findByText("Você entrou como Usuário.")).toBeOnTheScreen();
    expect(backend.authorizations).toEqual(["Bearer token-after-login"]); // the API is called only after the login
  });

  it("signs in, calls GET /api/v1/me with the generated client and shows the role the backend answered", async () => {
    const backend = fakeBackend(me("USER"));
    await renderShell({ authenticator: fakeAuthenticator("abc"), fetch: backend.fetch });

    expect(await screen.findByText("Você entrou como Usuário.")).toBeOnTheScreen();
    expect(backend.authorizations).toEqual(["Bearer abc"]);
    expect(screen.queryByText("A administração fica no app web.")).not.toBeOnTheScreen();
  });

  it.each([
    ["ADMIN", "Administrador"],
    ["MODERATOR", "Moderador"],
  ])("%s sees the role and the pointer to the web back office", async (role, label) => {
    await renderShell({ authenticator: fakeAuthenticator("abc"), fetch: fakeBackend(me(role)).fetch });
    expect(await screen.findByText(`Você entrou como ${label}.`)).toBeOnTheScreen();
    expect(screen.getByText("A administração fica no app web.")).toBeOnTheScreen();
  });

  it("takes the role from the backend, never from the token", async () => {
    await renderShell({ authenticator: fakeAuthenticator("role=ADMIN"), fetch: fakeBackend(me("USER")).fetch });
    expect(await screen.findByText("Você entrou como Usuário.")).toBeOnTheScreen();
    expect(screen.queryByText("A administração fica no app web.")).not.toBeOnTheScreen();
  });

  it("forgets the token and asks to sign in when the API answers 401", async () => {
    const authenticator = fakeAuthenticator("expired");
    await renderShell({ authenticator, fetch: fakeBackend({ status: 401 }).fetch });
    expect(await screen.findByText("Entrar")).toBeOnTheScreen();
    expect(authenticator.logout).toHaveBeenCalledTimes(1);
  });

  it("shows an error, not a profile, when the backend fails or answers an unknown role", async () => {
    await renderShell({ authenticator: fakeAuthenticator("abc"), fetch: fakeBackend({ status: 500 }).fetch });
    expect(await screen.findByText("Não foi possível carregar sua sessão.")).toBeOnTheScreen();
    expect(screen.queryByText(/Você entrou como/)).not.toBeOnTheScreen();
  });

  it("shows an error when the login itself fails", async () => {
    const authenticator = fakeAuthenticator(undefined);
    authenticator.login.mockRejectedValueOnce(new Error("network"));
    await renderShell({ authenticator });
    await fireEvent.press(await screen.findByText("Entrar"));
    expect(await screen.findByText("Não foi possível carregar sua sessão.")).toBeOnTheScreen();
  });

  it("sign out forgets the session", async () => {
    const authenticator = fakeAuthenticator("abc");
    await renderShell({ authenticator, fetch: fakeBackend(me("ADMIN")).fetch });
    await fireEvent.press(await screen.findByText("Sair"));
    expect(await screen.findByText("Entrar")).toBeOnTheScreen();
    expect(authenticator.logout).toHaveBeenCalledTimes(1);
  });
});
