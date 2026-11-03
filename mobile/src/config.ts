// Build-time settings (Expo `EXPO_PUBLIC_*`, inlined by the bundler). The local defaults point at the
// docker compose stack; on the Android emulator run `adb reverse tcp:8080 tcp:8080 tcp:8082 tcp:8082`
// so that `localhost` reaches the host, which is also what the token issuer has to say.
export const config = {
  oidcAuthority: process.env.EXPO_PUBLIC_OIDC_AUTHORITY ?? "http://localhost:8082/default",
  // The API validates the token audience; the mock identity provider issues the client id as audience.
  oidcClientId: process.env.EXPO_PUBLIC_OIDC_CLIENT_ID ?? "heracles-api",
  apiBaseUrl: process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://localhost:8080",
  redirectScheme: "heracles",
};
