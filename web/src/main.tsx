import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router";
import { registerSW } from "virtual:pwa-register";
import { createClient } from "./api/client";
import { App } from "./App";
import { AuthProvider } from "./auth/AuthContext";
import { createOidcAuthenticator } from "./auth/authenticator";
import { config } from "./config";
import "./i18n";
import "./styles.css";

registerSW({ immediate: true });

const authenticator = createOidcAuthenticator({ authority: config.oidcAuthority, clientId: config.oidcClientId });
const api = createClient(authenticator, config.apiBaseUrl);

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider authenticator={authenticator} api={api}>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
