import assert from "node:assert/strict";
import { test } from "node:test";
import { createApiClient } from "./index.js";

const json = (body: unknown, status = 200, contentType = "application/json") =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": contentType } });

test("getMe sends the bearer token and returns typed data", async () => {
  let seen: Request | undefined;
  const client = createApiClient({
    baseUrl: "http://api.test",
    getToken: () => "token-123",
    fetch: async (input) => {
      seen = input as Request;
      return json({ id: "11111111-1111-1111-1111-111111111111", role: "USER" });
    },
  });

  const { data, error } = await client.GET("/api/v1/me");

  assert.equal(seen?.url, "http://api.test/api/v1/me");
  assert.equal(seen?.headers.get("Authorization"), "Bearer token-123");
  assert.equal(error, undefined);
  // Typed: these compile only because the contract has `id` and `role`.
  const role: string | undefined = data?.role;
  assert.equal(role, "USER");
  assert.equal(data?.id, "11111111-1111-1111-1111-111111111111");
});

test("without a token no Authorization header is sent", async () => {
  let seen: Request | undefined;
  const client = createApiClient({
    baseUrl: "http://api.test",
    getToken: () => undefined,
    fetch: async (input) => {
      seen = input as Request;
      return json({ id: "x", role: "USER" });
    },
  });
  await client.GET("/api/v1/me");
  assert.equal(seen?.headers.has("Authorization"), false);
});

test("the audit trail takes its cursor and limit as typed query parameters", async () => {
  let seen: Request | undefined;
  const client = createApiClient({
    baseUrl: "http://api.test",
    getToken: () => "t",
    fetch: async (input) => {
      seen = input as Request;
      return json({ items: [], nextCursor: null });
    },
  });
  const { data } = await client.GET("/api/v1/admin/audit-events", { params: { query: { cursor: "abc", limit: 2 } } });
  assert.equal(new URL(seen!.url).searchParams.get("cursor"), "abc");
  assert.equal(new URL(seen!.url).searchParams.get("limit"), "2");
  assert.deepEqual(data?.items, []);
});

test("an error response comes back in `error`, not in `data`", async () => {
  const client = createApiClient({
    baseUrl: "http://api.test",
    getToken: () => "t",
    fetch: async () => json({ status: 401, code: "unauthenticated" }, 401, "application/problem+json"),
  });
  const { data, error, response } = await client.GET("/api/v1/me");
  assert.equal(response.status, 401);
  assert.equal(data, undefined);
  assert.ok(error);
});
