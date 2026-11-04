import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { useMemo } from "react";
import { createClient } from "./src/api/client";
import { AuthProvider } from "./src/auth/AuthContext";
import { createOidcAuthenticator } from "./src/auth/authenticator";
import { config } from "./src/config";
import { createLocalStorage } from "./src/storage/localStorage";
import "./src/i18n";
import { Shell } from "./src/Shell";

export default function App() {
  const { authenticator, api, storage } = useMemo(() => {
    const authenticator = createOidcAuthenticator({
      authority: config.oidcAuthority,
      clientId: config.oidcClientId,
      redirectScheme: config.redirectScheme,
    });
    return { authenticator, api: createClient(authenticator, config.apiBaseUrl), storage: createLocalStorage() };
  }, []);

  return (
    <SafeAreaProvider>
      <AuthProvider authenticator={authenticator} api={api} storage={storage}>
        <Shell />
        <StatusBar style="auto" />
      </AuthProvider>
    </SafeAreaProvider>
  );
}
