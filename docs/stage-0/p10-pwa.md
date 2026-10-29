# P10: Escopo do PWA e do cliente web

- **Prova:** P10 da Etapa 0 (`specs/platform/stage-0-proofs/spec.md`)
- **Decisão em:** 29/10/2026, por Dênis, no chat

## 1. Pergunta

Qual é o papel do PWA, e como o cliente web se relaciona com o app mobile e o backoffice?

## 2. Método

Leitura do que o EndoAcademy fez (`projects/clinic-system`: `app/` único em React + Vite, PWA instalável e backoffice em `/admin`, service worker só para o shell, sem cache de resposta da API) e confirmação com o Dênis de que o Heracles segue o mesmo desenho.

## 3. Decisão

**Dois clientes, não três:**
1. **App mobile** (React Native, iOS e Android): onde vive o offline com sincronização (ADR-0005), o dado de saúde guardado no aparelho (criptografado) e os recursos nativos (sensores de saúde, notificações push).
2. **App web** (React + TypeScript, Vite, uma única base em `web/`): o **PWA instalável do cliente** e o **backoffice** em `/admin`. A navegação mostra áreas conforme o perfil da sessão (`USER`, `ADMIN`, `MODERATOR`).

Regras do app web:
- **Service worker só para o shell** (arquivos estáticos). **Sem cache de resposta da API**, porque ela carrega dado de saúde.
- **Sem offline**: sem conexão, o app web mostra o estado "sem conexão"; offline é do app mobile.
- Cliente de API gerado do OpenAPI, i18n com chaves em inglês e textos pt-BR, OIDC com Authorization Code + PKCE.
- A troca de alimento e as demais regras rodam só no backend (ADR-0006); o app web usa o mesmo serviço.
- O backoffice nunca abre tabela de dado de saúde (CLAUDE.md, seção 4); isso vale no backend, não na tela.

## 4. O que muda

- `CLAUDE.md`: "dois clientes"; pasta `backoffice/` deixa de existir, tudo em `web/`.
- `docs/architecture.md`: diagrama, tabela de clientes e item em aberto resolvido.
- ADR-0001: nota de alteração na decisão sobre clientes.
- `README.md`: descrição do projeto.
- O offline da Etapa 5 fica só no app mobile; a Etapa 8 (backoffice) entrega as telas `/admin` dentro do app web.

## 5. Limites

- O recorte de quais telas do cliente existem no app web (por exemplo, se a comunidade e os exames estão lá no início) é decidido na spec de cada etapa.
- Notificação push no navegador não é requisito; o canal de push do app mobile fica no port `NotificationChannel`.

## 6. Pendências

Nenhuma.
