import { fireEvent, screen } from "@testing-library/react-native";
import { StyleSheet } from "react-native";
import { fakeAuthenticator, fakeBackend, fakeLocalStorage, renderShell } from "./test-utils";

const me = (role: string) => ({ status: 200, body: { id: "11111111-1111-1111-1111-111111111111", role } });

describe("mobile shell (CA20)", () => {
  it("signed out: offers sign in and calls the API only after the login", async () => {
    const backend = fakeBackend(me("USER"));
    const authenticator = fakeAuthenticator(undefined);
    await renderShell({ authenticator, fetch: backend.fetch });

    await fireEvent.press(await screen.findByText("Entrar"));
    expect(authenticator.login).toHaveBeenCalledTimes(1);
    expect(await screen.findByText("Você entrou como Usuário.")).toBeOnTheScreen();
    expect(backend.authorizations.length).toBeGreaterThan(0); // the API is called only after the login
    expect(backend.authorizations.every((a) => a === "Bearer token-after-login")).toBe(true);
  });

  it("signs in, calls GET /api/v1/me with the generated client and shows the role the backend answered", async () => {
    const backend = fakeBackend(me("USER"));
    await renderShell({ authenticator: fakeAuthenticator("abc"), fetch: backend.fetch });

    expect(await screen.findByText("Você entrou como Usuário.")).toBeOnTheScreen();
    expect(backend.authorizations.every((a) => a === "Bearer abc")).toBe(true); // /me, then the consents the gate asks for
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

describe("safe area", () => {
  const padding = () => StyleSheet.flatten(screen.getByTestId("shell").props.style);

  it("keeps the content below the notch and the status bar, and above the home indicator", async () => {
    await renderShell({ authenticator: fakeAuthenticator(undefined), insets: { top: 47, bottom: 34, left: 0, right: 0 } });
    await screen.findByText("Entrar");
    expect(padding().paddingTop).toBeGreaterThanOrEqual(47);
    expect(padding().paddingBottom).toBeGreaterThanOrEqual(34);
  });

  it("follows the device: no inset, no extra padding beyond the base", async () => {
    await renderShell({ authenticator: fakeAuthenticator(undefined), insets: { top: 0, bottom: 0, left: 0, right: 0 } });
    await screen.findByText("Entrar");
    expect(padding().paddingTop).toBeLessThan(47);
  });
});

describe("local storage follows the session (S7.5)", () => {
  const userId = "11111111-1111-1111-1111-111111111111";

  it("opens the encrypted database of the signed-in user, and only after the backend confirmed who they are", async () => {
    const storage = fakeLocalStorage();
    await renderShell({ authenticator: fakeAuthenticator("abc"), fetch: fakeBackend(me("USER")).fetch, storage });
    expect(await screen.findByText("Você entrou como Usuário.")).toBeOnTheScreen();
    expect(storage.open).toHaveBeenCalledWith(userId);
  });

  it("opens nothing when nobody is signed in, and erases leftovers of an interrupted sign-out", async () => {
    const storage = fakeLocalStorage();
    await renderShell({ authenticator: fakeAuthenticator(undefined), storage });
    expect(await screen.findByText("Entrar")).toBeOnTheScreen();
    expect(storage.open).not.toHaveBeenCalled();
    expect(storage.erase).toHaveBeenCalledTimes(1);
  });

  it("sign out erases the local data and the tokens", async () => {
    const storage = fakeLocalStorage();
    const authenticator = fakeAuthenticator("abc");
    await renderShell({ authenticator, fetch: fakeBackend(me("USER")).fetch, storage });
    await fireEvent.press(await screen.findByText("Sair"));
    expect(await screen.findByText("Entrar")).toBeOnTheScreen();
    expect(storage.erase).toHaveBeenCalledTimes(1);
    expect(authenticator.logout).toHaveBeenCalledTimes(1);
  });

  it("signs out even when erasing fails, and tries again at the next start", async () => {
    const storage = fakeLocalStorage();
    const authenticator = fakeAuthenticator("abc");
    await renderShell({ authenticator, fetch: fakeBackend(me("USER")).fetch, storage });
    await screen.findByText("Sair");
    storage.erase.mockRejectedValueOnce(new Error("disk"));
    await fireEvent.press(screen.getByText("Sair"));
    expect(await screen.findByText("Entrar")).toBeOnTheScreen();
    expect(authenticator.logout).toHaveBeenCalledTimes(1); // the tokens go anyway
  });

  it("erases the local data when the API says the token is not valid (401)", async () => {
    const storage = fakeLocalStorage();
    await renderShell({ authenticator: fakeAuthenticator("expired"), fetch: fakeBackend({ status: 401 }).fetch, storage });
    expect(await screen.findByText("Entrar")).toBeOnTheScreen();
    expect(storage.erase).toHaveBeenCalled();
    expect(storage.open).not.toHaveBeenCalled();
  });

  it("does not show a profile when the database cannot be opened, and lets the user sign out", async () => {
    const storage = fakeLocalStorage();
    storage.open.mockRejectedValueOnce(new Error("SQLCipher is not available"));
    await renderShell({ authenticator: fakeAuthenticator("abc"), fetch: fakeBackend(me("USER")).fetch, storage });
    expect(await screen.findByText("Não foi possível carregar sua sessão.")).toBeOnTheScreen();
    expect(screen.queryByText(/Você entrou como/)).not.toBeOnTheScreen();
    await fireEvent.press(screen.getByText("Sair"));
    expect(await screen.findByText("Entrar")).toBeOnTheScreen();
  });
});
