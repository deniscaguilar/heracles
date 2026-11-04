import { render, screen } from "@testing-library/react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
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
    // The gate asks for the consents too: for these tests the person has accepted everything.
    if (new URL((input as Request).url).pathname === "/api/v1/me/consents" && reply.status === 200) {
      return new Response(JSON.stringify({ items: [] }), { status: 200, headers: { "Content-Type": "application/json" } });
    }
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
  /** The areas the device keeps for itself (notch, status bar, home indicator); a phone with a notch by default. */
  insets?: { top: number; bottom: number; left: number; right: number };
}) {
  const api = createClient(options.authenticator, "http://api.test", options.fetch);
  const insets = options.insets ?? { top: 47, bottom: 34, left: 0, right: 0 };
  await render(
    <SafeAreaProvider initialMetrics={{ frame: { x: 0, y: 0, width: 390, height: 844 }, insets }}>
      <AuthProvider authenticator={options.authenticator} api={api} storage={options.storage ?? fakeLocalStorage()}>
        <Shell />
      </AuthProvider>
    </SafeAreaProvider>,
  );
  return screen;
}

type Reply = { status: number; body?: unknown };
type Handler = Reply | ((call: { body: unknown }) => Reply);

/** A backend that answers by "METHOD /path" and records every call, so a test can say what was (not) sent. */
export function routedBackend(routes: Record<string, Handler>) {
  const calls: { key: string; body: unknown }[] = [];
  const table = { ...routes };
  const fetch: typeof globalThis.fetch = async (input) => {
    const request = input as Request;
    const key = `${request.method} ${new URL(request.url).pathname}`;
    const text = request.method === "GET" ? "" : await request.clone().text();
    const body = text ? JSON.parse(text) : undefined;
    calls.push({ key, body });
    const handler = table[key];
    if (!handler) return new Response(JSON.stringify({ code: "not_found" }), { status: 404, headers: { "Content-Type": "application/json" } });
    const reply = typeof handler === "function" ? handler({ body }) : handler;
    const noBody = reply.body === undefined || reply.status === 204;
    return new Response(noBody ? null : JSON.stringify(reply.body), { status: reply.status, headers: { "Content-Type": "application/json" } });
  };
  return { fetch, calls, routes: table, called: (key: string) => calls.filter((c) => c.key === key) };
}

export const USER_ID = "11111111-1111-1111-1111-111111111111";
export const meRoute = (role = "USER"): Reply => ({ status: 200, body: { id: USER_ID, role } });

export const REQUIRED_TYPES = ["TERMS_OF_USE", "PRIVACY_POLICY", "HEALTH_DATA_CONSENT"];
export const ALL_TYPES = [...REQUIRED_TYPES, "AI_EXAMS_CONSENT", "COMMUNITY_CONSENT", "NOTIFICATIONS_CONSENT", "PROMOTIONAL_CONSENT"];

export const consentItem = (
  type: string,
  over: Partial<{ granted: boolean; pending: boolean; currentVersion: number | null; acceptedVersionId: string | null }> = {},
) => ({
  type,
  required: REQUIRED_TYPES.includes(type),
  granted: false,
  pending: false,
  currentVersionId: `v-${type}`,
  currentVersion: 1,
  acceptedVersionId: null,
  ...over,
});

export const policyOf = (type: string, text = `Texto de ${type}`) => ({
  versionId: `v-${type}`,
  type,
  version: 1,
  title: `Título de ${type}`,
  blocks: [{ type: "paragraph", children: [{ type: "text", text }] }],
});
