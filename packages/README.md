# packages

Código TypeScript compartilhado entre `mobile/` e `web/`: cliente de API gerado do OpenAPI, tipos e design tokens.

| Pacote | O que é |
|---|---|
| `api-client` | Cliente tipado da API, gerado de `backend/openapi/openapi.json` com `openapi-typescript` e `openapi-fetch`. `src/schema.d.ts` é gerado e versionado; o CI o regenera e falha se estiver desatualizado. |

Para trabalhar (Node 22): `cd packages/api-client && npm ci && npm run check` (gera, compila e testa). Depois de mudar o contrato do backend (`-Dopenapi.update=true`), rode `npm run generate` e versione o `schema.d.ts`.
