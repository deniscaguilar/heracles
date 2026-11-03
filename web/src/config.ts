// Build-time settings (Vite `VITE_*`). Local defaults point at the docker compose stack and exist only in
// development and tests; a production build must set them explicitly.
function setting(name: string, localDefault: string): string {
  const value = import.meta.env[name] as string | undefined;
  if (value !== undefined) return value;
  if (import.meta.env.PROD) throw new Error(`${name} is required in a production build`);
  return localDefault;
}

export const config = {
  oidcAuthority: setting("VITE_OIDC_AUTHORITY", "http://localhost:8082/default"),
  // The API validates the token audience; the mock identity provider issues the client id as audience.
  oidcClientId: setting("VITE_OIDC_CLIENT_ID", "heracles-api"),
  // Empty means "same origin" (the dev server proxies /api to the backend).
  apiBaseUrl: setting("VITE_API_BASE_URL", ""),
};
