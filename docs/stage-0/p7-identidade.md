# P7: Provedor de identidade

- **Prova:** P7 da Etapa 0 (`specs/platform/stage-0-proofs/spec.md`); tarefa T5.3
- **Estado:** decisão **provisória** tomada em 29/10/2026: **Amazon Cognito** (ADR-0008, aprovada pelo Dênis). Nada foi testado contra um user pool real.

## 1. Pergunta

Qual provedor de identidade usar atrás do port `IdentityProvider`, com login por conta própria e por Google e Apple, MFA e recuperação de conta, para um produto pequeno e um desenvolvedor só?

## 2. Onde está o comparativo

No **Notion** (página "Provedores de identidade: comparativo (P7)", sob Heracles Society). Preços e propostas ficam lá, e não neste repositório público.

## 3. Candidatos avaliados

Amazon Cognito, Keycloak (autohospedado), Auth0, Clerk, Firebase Identity Platform e as opções open source Supabase Auth, Ory e Zitadel. Critérios: PKCE no mobile, MFA, recuperação de conta, custo para produto pequeno, operação para desenvolvedor solo, região dos dados e serviço novo (que exige aprovação).

## 4. Resultado

- **Cognito** é o melhor encaixe: já estamos na AWS, não cria serviço novo, cobre OIDC com PKCE, MFA por aplicativo e login social (Google e Apple), e o custo para poucos usuários tende a ficar na faixa gratuita.
- **Keycloak** é o plano B (serviço novo para operar).
- Auth0, Clerk e Firebase saem por custo com MFA, região fora do Brasil ou pouco encaixe com o desenho.
- **A pesquisa é quase toda de terceiros, com números que se contradizem.** Nada foi confirmado em página oficial; por isso a decisão é provisória.

## 5. Decisão

Ver o ADR-0008. Resumo: Cognito por OIDC e PKCE; o provedor guarda só identificador e e-mail; perfis e permissões ficam no banco do `platform`; MFA obrigatório para `ADMIN` e `MODERATOR`, que entram só com conta própria; login do cliente por conta própria, Google e Apple (a Apple vem junto com o Google no iOS); sem vínculo automático de contas; consentimento do produto antes de qualquer dado de saúde.

## 6. Limites

- Sem prova prática de login (exige user pool de teste, custo e autorização).
- Disponibilidade em São Paulo e preço oficial não confirmados.
- Textos em pt-BR do login gerenciado e o envio de e-mail e SMS não verificados.
- Regras da App Store e do Google sobre login social conferidas só por fontes de terceiros.

## 7. Pendências

Estão na seção "Pendências" do ADR-0008: contas de desenvolvedor da Apple e do Google, região, preço oficial, idioma, e-mail e SMS, exclusão e exportação por API e a prova real de login com PKCE.
