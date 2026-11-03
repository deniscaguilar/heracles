import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import type { ApiClient } from "../api/client";
import type { Authenticator } from "./authenticator";

export type Role = "USER" | "ADMIN" | "MODERATOR";

export type Session =
  | { status: "loading" }
  | { status: "anonymous" }
  | { status: "authenticated"; userId: string; role: Role }
  | { status: "error" };

interface AuthContextValue {
  session: Session;
  login: () => Promise<void>;
  completeLogin: () => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const ROLES: readonly string[] = ["USER", "ADMIN", "MODERATOR"];

export function canSeeAdmin(session: Session): boolean {
  return session.status === "authenticated" && (session.role === "ADMIN" || session.role === "MODERATOR");
}

export function AuthProvider(props: { authenticator: Authenticator; api: ApiClient; children: ReactNode }) {
  const { authenticator, api } = props;
  const [session, setSession] = useState<Session>({ status: "loading" });

  // The role always comes from the backend (`GET /api/v1/me`), never from the token.
  const load = useCallback(async () => {
    if (!(await authenticator.getAccessToken())) return setSession({ status: "anonymous" });
    try {
      const { data, response } = await api.GET("/api/v1/me");
      if (response.status === 401) {
        await authenticator.logout();
        return setSession({ status: "anonymous" });
      }
      if (data && ROLES.includes(data.role)) {
        return setSession({ status: "authenticated", userId: data.id, role: data.role as Role });
      }
      setSession({ status: "error" });
    } catch {
      setSession({ status: "error" });
    }
  }, [authenticator, api]);

  useEffect(() => {
    void load();
  }, [load]);

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      login: () => authenticator.startLogin(),
      completeLogin: async () => {
        await authenticator.completeLogin();
        await load();
      },
      logout: async () => {
        await authenticator.logout();
        setSession({ status: "anonymous" });
      },
    }),
    [session, authenticator, load],
  );
  return <AuthContext.Provider value={value}>{props.children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
