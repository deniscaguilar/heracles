# web

App web em React, uma única base: PWA instalável do cliente e backoffice em `/admin` (`docs/stage-0/p10-pwa.md`). Service worker só para o shell, sem cache de resposta da API.

Estado: shell com i18n (pt-BR), aviso de "sem conexão" e PWA instalável (service worker só para o shell). Login e `/admin` entram na E1-S7.2.

Comandos (Node 22): `npm ci`, `npm run dev` (proxy de `/api` para `localhost:8080`), `npm run build`, `npm test`.
