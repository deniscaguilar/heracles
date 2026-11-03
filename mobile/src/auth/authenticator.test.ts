import * as AuthSession from "expo-auth-session";
import { createOidcAuthenticator } from "./authenticator";
import type { StoredToken, TokenStore } from "./tokenStore";

jest.mock("expo-auth-session", () => ({
  ResponseType: { Code: "code" },
  fetchDiscoveryAsync: jest.fn(async () => ({ authorizationEndpoint: "a", tokenEndpoint: "t" })),
  makeRedirectUri: jest.fn(() => "heracles://auth/callback"),
  exchangeCodeAsync: jest.fn(),
  AuthRequest: jest.fn(),
}));

const settings = { authority: "http://idp/default", clientId: "heracles-api", redirectScheme: "heracles" };

function memoryStore(initial: StoredToken | null = null): TokenStore & { current: StoredToken | null } {
  const store = {
    current: initial,
    load: async () => store.current,
    save: async (t: StoredToken) => {
      store.current = t;
    },
    clear: async () => {
      store.current = null;
    },
  };
  return store;
}

function mockPrompt(result: { type: string; params?: Record<string, string> }) {
  (AuthSession.AuthRequest as unknown as jest.Mock).mockImplementation(() => ({
    codeVerifier: "verifier-123",
    promptAsync: jest.fn(async () => result),
  }));
}

describe("oidc authenticator", () => {
  beforeEach(() => jest.clearAllMocks());

  it("asks for the authorization code flow with PKCE and exchanges the code with the verifier", async () => {
    mockPrompt({ type: "success", params: { code: "the-code" } });
    (AuthSession.exchangeCodeAsync as jest.Mock).mockResolvedValue({ accessToken: "access-1", expiresIn: 3600 });
    const store = memoryStore();

    const result = await createOidcAuthenticator(settings, store, () => 1_000).login();

    expect(result).toBe("signedIn");
    expect(AuthSession.AuthRequest).toHaveBeenCalledWith(
      expect.objectContaining({ clientId: "heracles-api", responseType: "code", usePKCE: true, scopes: ["openid"] }),
    );
    expect(AuthSession.exchangeCodeAsync).toHaveBeenCalledWith(
      expect.objectContaining({ code: "the-code", extraParams: { code_verifier: "verifier-123" } }),
      expect.anything(),
    );
    expect(store.current).toEqual({ accessToken: "access-1", expiresAt: 1_000 + 3_600_000 });
  });

  it("stores nothing when the user cancels or the provider answers an error", async () => {
    for (const type of ["cancel", "dismiss", "error"]) {
      mockPrompt({ type });
      const store = memoryStore();
      expect(await createOidcAuthenticator(settings, store).login()).toBe("cancelled");
      expect(store.current).toBeNull();
    }
    expect(AuthSession.exchangeCodeAsync).not.toHaveBeenCalled();
  });

  it("gives the token only while it is valid and forgets an expired one", async () => {
    const store = memoryStore({ accessToken: "t", expiresAt: 5_000 });
    const auth = createOidcAuthenticator(settings, store, () => 4_999);
    expect(await auth.getAccessToken()).toBe("t");

    expect(await createOidcAuthenticator(settings, store, () => 5_000).getAccessToken()).toBeUndefined();
    expect(store.current).toBeNull();
  });

  it("has no token when nothing is stored, and logout clears it", async () => {
    const store = memoryStore({ accessToken: "t", expiresAt: Date.now() + 60_000 });
    const auth = createOidcAuthenticator(settings, store);
    await auth.logout();
    expect(store.current).toBeNull();
    expect(await auth.getAccessToken()).toBeUndefined();
  });
});
