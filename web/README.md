# web

App web em React, uma única base: PWA instalável do cliente e backoffice em `/admin` (`docs/stage-0/p10-pwa.md`). Service worker só para o shell, sem cache de resposta da API.

Estado: shell com i18n (pt-BR), aviso de "sem conexão", PWA instalável (service worker só para o shell), login OIDC (Authorization Code + PKCE, tokens só em `sessionStorage`) e área `/admin` só para `ADMIN` e `MODERATOR`. O perfil vem de `GET /api/v1/me` pelo cliente gerado (`@heracles/api-client`), nunca do token.

Comandos (Node 22; antes, `npm ci && npm run build` em `packages/api-client`, que o web usa como dependência local): `npm ci`, `npm run dev` (proxy de `/api` para `localhost:8080`), `npm run build`, `npm test`.

Ajustes de build em `.env.example` (`VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID`, `VITE_API_BASE_URL`); os padrões locais valem só no `dev` e nos testes, e um build de produção exige os três. Como entrar localmente: `docker/README.md`.
