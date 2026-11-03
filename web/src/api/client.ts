import { createApiClient } from "@heracles/api-client";
import type { Authenticator } from "../auth/authenticator";

export type ApiClient = ReturnType<typeof createApiClient>;

export function createClient(authenticator: Authenticator, baseUrl: string, fetch?: typeof globalThis.fetch): ApiClient {
  return createApiClient({
    baseUrl: baseUrl || window.location.origin,
    getToken: () => authenticator.getAccessToken(),
    fetch,
  });
}
