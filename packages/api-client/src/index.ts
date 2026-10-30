import createClient from "openapi-fetch";
import type { paths } from "./schema.js";

export type { paths, components } from "./schema.js";

export interface ApiClientOptions {
  /** Base URL of the API, for example `https://api.example.com`. */
  baseUrl: string;
  /** Returns the access token of the signed-in user, or nothing when signed out. Called on every request. */
  getToken: () => string | undefined | Promise<string | undefined>;
  /** Replaceable for tests. */
  fetch?: typeof globalThis.fetch;
}

/** Client typed by the OpenAPI contract: a path or a field that does not exist does not compile. */
export function createApiClient(options: ApiClientOptions) {
  const client = createClient<paths>({ baseUrl: options.baseUrl, fetch: options.fetch });
  client.use({
    async onRequest({ request }) {
      const token = await options.getToken();
      if (token) request.headers.set("Authorization", `Bearer ${token}`);
      return request;
    },
  });
  return client;
}
