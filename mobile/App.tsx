import { StatusBar } from "expo-status-bar";
import { useMemo } from "react";
import { createClient } from "./src/api/client";
import { AuthProvider } from "./src/auth/AuthContext";
import { createOidcAuthenticator } from "./src/auth/authenticator";
import { config } from "./src/config";
import "./src/i18n";
import { Shell } from "./src/Shell";

export default function App() {
  const { authenticator, api } = useMemo(() => {
    const authenticator = createOidcAuthenticator({
      authority: config.oidcAuthority,
      clientId: config.oidcClientId,
      redirectScheme: config.redirectScheme,
    });
    return { authenticator, api: createClient(authenticator, config.apiBaseUrl) };
  }, []);

  return (
    <AuthProvider authenticator={authenticator} api={api}>
      <Shell />
      <StatusBar style="auto" />
    </AuthProvider>
  );
}
