import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { ApiClient } from "../api/client";
import type { LocalStorage } from "../storage/localStorage";
import type { Authenticator } from "./authenticator";

export type Role = "USER" | "ADMIN" | "MODERATOR";

export type Session =
  | { status: "loading" }
  | { status: "anonymous" }
  | { status: "authenticated"; userId: string; role: Role }
  | { status: "error" };

interface AuthContextValue {
  session: Session;
  /** The API client of the session: screens use it so they all send the same token. */
  api: ApiClient;
  login: () => Promise<void>;
  logout: () => Promise<void>;
  retry: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const ROLES: readonly string[] = ["USER", "ADMIN", "MODERATOR"];

export function AuthProvider(props: {
  authenticator: Authenticator;
  api: ApiClient;
  storage: LocalStorage;
  children: ReactNode;
}) {
  const { authenticator, api, storage } = props;
  const [session, setSession] = useState<Session>({ status: "loading" });

  // Whoever is not signed in has nothing on the device: leftovers of an interrupted sign-out go now.
  const signedOut = useCallback(async () => {
    try {
      await storage.erase();
    } catch {
      // The pointer stays, so the next start tries again.
    }
    setSession({ status: "anonymous" });
  }, [storage]);

  // The role always comes from the backend (`GET /api/v1/me`), never from the token.
  const load = useCallback(async () => {
    setSession({ status: "loading" });
    if (!(await authenticator.getAccessToken())) return signedOut();
    try {
      const { data, response } = await api.GET("/api/v1/me");
      if (response.status === 401) {
        await authenticator.logout();
        return signedOut();
      }
      if (data && ROLES.includes(data.role)) {
        await storage.open(data.id);
        return setSession({ status: "authenticated", userId: data.id, role: data.role as Role });
      }
      setSession({ status: "error" });
    } catch {
      setSession({ status: "error" });
    }
  }, [authenticator, api, storage, signedOut]);

  useEffect(() => {
    void load();
  }, [load]);

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      api,
      login: async () => {
        try {
          if ((await authenticator.login()) === "signedIn") await load();
        } catch {
          setSession({ status: "error" });
        }
      },
      logout: async () => {
        // Local data first, then the tokens; the tokens go even if erasing fails.
        try {
          await storage.erase();
        } catch {
          // Retried at the next start (the pointer to the user's data is kept).
        }
        await authenticator.logout();
        setSession({ status: "anonymous" });
      },
      retry: load,
    }),
    [session, api, authenticator, storage, load],
  );
  return <AuthContext.Provider value={value}>{props.children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
