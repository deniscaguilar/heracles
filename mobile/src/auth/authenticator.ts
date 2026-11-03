import * as AuthSession from "expo-auth-session";
import { secureTokenStore, type TokenStore } from "./tokenStore";

export type LoginResult = "signedIn" | "cancelled";

/** What the app needs from the identity provider; the real adapter is OIDC Authorization Code + PKCE. */
export interface Authenticator {
  /** Access token of the signed-in user, or nothing when signed out or expired. */
  getAccessToken(): Promise<string | undefined>;
  /** Opens the identity provider in the system browser and stores the tokens on success. */
  login(): Promise<LoginResult>;
  /** Forgets the tokens on this device. */
  logout(): Promise<void>;
}

export interface OidcSettings {
  authority: string;
  clientId: string;
  redirectScheme: string;
}

export function createOidcAuthenticator(
  settings: OidcSettings,
  store: TokenStore = secureTokenStore,
  now: () => number = Date.now,
): Authenticator {
  return {
    async getAccessToken() {
      const token = await store.load();
      if (!token) return undefined;
      if (token.expiresAt <= now()) {
        await store.clear();
        return undefined;
      }
      return token.accessToken;
    },

    async login() {
      const discovery = await AuthSession.fetchDiscoveryAsync(settings.authority);
      const redirectUri = AuthSession.makeRedirectUri({ scheme: settings.redirectScheme, path: "auth/callback" });
      const request = new AuthSession.AuthRequest({
        clientId: settings.clientId,
        redirectUri,
        scopes: ["openid"],
        responseType: AuthSession.ResponseType.Code,
        usePKCE: true,
      });
      const result = await request.promptAsync(discovery);
      if (result.type !== "success") return "cancelled";

      const tokens = await AuthSession.exchangeCodeAsync(
        {
          clientId: settings.clientId,
          code: result.params.code ?? "",
          redirectUri,
          extraParams: { code_verifier: request.codeVerifier ?? "" },
        },
        discovery,
      );
      await store.save({
        accessToken: tokens.accessToken,
        expiresAt: now() + (tokens.expiresIn ?? 0) * 1000,
      });
      return "signedIn";
    },

    logout: () => store.clear(),
  };
}
