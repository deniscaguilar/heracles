import { render, screen } from "@testing-library/react-native";
import { createClient } from "./api/client";
import { AuthProvider } from "./auth/AuthContext";
import type { Authenticator, LoginResult } from "./auth/authenticator";
import type { LocalStorage } from "./storage/localStorage";
import { Shell } from "./Shell";

export interface FakeAuthenticator extends Authenticator {
  login: jest.Mock<Promise<LoginResult>, []>;
  logout: jest.Mock<Promise<void>, []>;
  /** Replaces the token the fake holds, as a login would. */
  setToken(token: string | undefined): void;
}

export function fakeAuthenticator(token: string | undefined): FakeAuthenticator {
  let current = token;
  return {
    getAccessToken: async () => current,
    login: jest.fn(async () => {
      current = "token-after-login";
      return "signedIn" as const;
    }),
    logout: jest.fn(async () => {
      current = undefined;
    }),
    setToken: (t) => {
      current = t;
    },
  };
}

export interface FakeLocalStorage extends LocalStorage {
  open: jest.Mock<Promise<void>, [string]>;
  erase: jest.Mock<Promise<void>, []>;
}

export function fakeLocalStorage(): FakeLocalStorage {
  return { open: jest.fn(async (_userId: string) => {}), erase: jest.fn(async () => {}) };
}

/** A backend that answers `GET /api/v1/me` and records the Authorization header of each call. */
export function fakeBackend(reply: { status: number; body?: unknown }) {
  const authorizations: (string | null)[] = [];
  const fetch: typeof globalThis.fetch = async (input) => {
    authorizations.push((input as Request).headers.get("Authorization"));
    return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
      status: reply.status,
      headers: { "Content-Type": "application/json" },
    });
  };
  return { fetch, authorizations };
}

export async function renderShell(options: {
  authenticator: Authenticator;
  fetch?: typeof globalThis.fetch;
  storage?: LocalStorage;
}) {
  const api = createClient(options.authenticator, "http://api.test", options.fetch);
  await render(
    <AuthProvider authenticator={options.authenticator} api={api} storage={options.storage ?? fakeLocalStorage()}>
      <Shell />
    </AuthProvider>,
  );
  return screen;
}
