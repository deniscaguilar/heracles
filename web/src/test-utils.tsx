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
    // The gate asks for the consents too: for these tests the person has accepted everything.
    if (new URL(request.url).pathname === "/api/v1/me/consents" && reply.status === 200) {
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { "Content-Type": "application/json" } });
    }
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

type Reply = { status: number; body?: unknown };
type Handler = Reply | ((call: { body: unknown; search: URLSearchParams }) => Reply);

/** A backend that answers by "METHOD /path" and records every call, so a test can say what was (not) sent. */
export function routedBackend(routes: Record<string, Handler>) {
  const calls: { key: string; body: unknown }[] = [];
  const table = { ...routes };
  const fetch: typeof globalThis.fetch = async (input) => {
    const request = input as Request;
    const url = new URL(request.url);
    const key = `${request.method} ${url.pathname}`;
    const text = request.method === "GET" ? "" : await request.clone().text();
    const body = text ? JSON.parse(text) : undefined;
    calls.push({ key, body });
    const handler = table[key];
    if (!handler) return new Response(JSON.stringify({ code: "not_found" }), { status: 404, headers: { "Content-Type": "application/json" } });
    const reply = typeof handler === "function" ? handler({ body, search: url.searchParams }) : handler;
    const noBody = reply.body === undefined || reply.status === 204;
    return new Response(noBody ? null : JSON.stringify(reply.body), {
      status: reply.status,
      headers: { "Content-Type": "application/json" },
    });
  };
  return { fetch, calls, routes: table, called: (key: string) => calls.filter((c) => c.key === key) };
}

export const USER_ID = "11111111-1111-1111-1111-111111111111";
export const meRoute = (role = "USER"): Reply => ({ status: 200, body: { id: USER_ID, role } });

export const consentItem = (
  type: string,
  over: Partial<{ required: boolean; granted: boolean; pending: boolean; currentVersionId: string | null; currentVersion: number | null; acceptedVersionId: string | null }> = {},
) => ({
  type,
  required: ["TERMS_OF_USE", "PRIVACY_POLICY", "HEALTH_DATA_CONSENT"].includes(type),
  granted: false,
  pending: false,
  currentVersionId: `v-${type}`,
  currentVersion: 1,
  acceptedVersionId: null,
  ...over,
});

export const ALL_TYPES = [
  "TERMS_OF_USE", "PRIVACY_POLICY", "HEALTH_DATA_CONSENT", "AI_EXAMS_CONSENT", "COMMUNITY_CONSENT", "NOTIFICATIONS_CONSENT", "PROMOTIONAL_CONSENT",
];

export const policyOf = (type: string, text = `Texto de ${type}`) => ({
  versionId: `v-${type}`,
  type,
  version: 1,
  title: `Título de ${type}`,
  blocks: [{ type: "paragraph", children: [{ type: "text", text }] }],
});
