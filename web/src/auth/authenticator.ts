import { UserManager, WebStorageStateStore } from "oidc-client-ts";

/** What the app needs from the identity provider; the real adapter is OIDC Authorization Code + PKCE. */
export interface Authenticator {
  /** Access token of the signed-in user, or nothing when signed out or expired. */
  getAccessToken(): Promise<string | undefined>;
  /** Sends the browser to the identity provider. */
  startLogin(): Promise<void>;
  /** Finishes the login on the redirect back (exchanges the code for tokens). */
  completeLogin(): Promise<void>;
  /** Forgets the tokens on this device. */
  logout(): Promise<void>;
}

/** The part of `UserManager` that the adapter uses, so tests can replace it. */
export interface UserManagerLike {
  getUser(): Promise<{ access_token: string; expired?: boolean } | null>;
  signinRedirect(): Promise<void>;
  signinRedirectCallback(): Promise<unknown>;
  removeUser(): Promise<void>;
}

export function oidcAuthenticator(manager: UserManagerLike): Authenticator {
  return {
    async getAccessToken() {
      const user = await manager.getUser();
      return user && !user.expired ? user.access_token : undefined;
    },
    startLogin: () => manager.signinRedirect(),
    async completeLogin() {
      await manager.signinRedirectCallback();
    },
    logout: () => manager.removeUser(),
  };
}

export function createOidcAuthenticator(settings: { authority: string; clientId: string }): Authenticator {
  const manager = new UserManager({
    authority: settings.authority,
    client_id: settings.clientId,
    redirect_uri: `${window.location.origin}/auth/callback`,
    response_type: "code", // Authorization Code with PKCE (oidc-client-ts always sends the S256 challenge)
    scope: "openid",
    // Tokens live only as long as the tab: never in localStorage.
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    automaticSilentRenew: false,
  });
  return oidcAuthenticator(manager);
}
