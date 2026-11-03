import { act, render } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { vi } from "vitest";
import { createClient } from "./api/client";
import { App } from "./App";
import { AuthProvider } from "./auth/AuthContext";
import type { Authenticator } from "./auth/authenticator";

export interface FakeAuthenticator extends Authenticator {
  startLogin: ReturnType<typeof vi.fn<() => Promise<void>>>;
  completeLogin: ReturnType<typeof vi.fn<() => Promise<void>>>;
  logout: ReturnType<typeof vi.fn<() => Promise<void>>>;
}

export function fakeAuthenticator(token: string | undefined): FakeAuthenticator {
  let current = token;
  return {
    getAccessToken: async () => current,
    startLogin: vi.fn(async () => {}),
    completeLogin: vi.fn(async () => {
      current = "token-after-login";
    }),
    logout: vi.fn(async () => {
      current = undefined;
    }),
  };
}

/** A backend that answers `GET /api/v1/me` and records the Authorization header of each call. */
export function fakeBackend(reply: { status: number; body?: unknown }) {
  const authorizations: (string | null)[] = [];
  const fetch: typeof globalThis.fetch = async (input) => {
    const request = input as Request;
    authorizations.push(request.headers.get("Authorization"));
    return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
      status: reply.status,
      headers: { "Content-Type": "application/json" },
    });
  };
  return { fetch, authorizations };
}

export async function renderApp(
  path: string,
  options: { authenticator: Authenticator; fetch?: typeof globalThis.fetch },
) {
  const api = createClient(options.authenticator, "http://api.test", options.fetch);
  const view = render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider authenticator={options.authenticator} api={api}>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
  await act(async () => {}); // let the session load
  return view;
}
