# Heracles Society

App de treino, nutrição e saúde: app mobile (iOS e Android), app web (PWA do cliente e backoffice), backend e infraestrutura.

## Estado atual

Etapa 0 concluída com pendências (`docs/stage-0/fechamento.md`); Etapa 1 (fundação técnica) em andamento. Ainda não há código de produto.

| Documento | Conteúdo |
|---|---|
| `CLAUDE.md` | Regras obrigatórias para a IA e para o desenvolvimento |
| `docs/roadmap.md` | Escopo e etapas do produto |
| `docs/architecture.md` | Módulos, dependências permitidas, eventos e fluxos |
| `docs/adr/` | Decisões de arquitetura (ADRs) |

## Estrutura

| Pasta | Conteúdo |
|---|---|
| `backend/` | Backend Kotlin e Spring Boot |
| `mobile/` | App iOS e Android (React Native) |
| `web/` | App web: PWA do cliente e backoffice em `/admin` (React) |
| `packages/` | Código TypeScript compartilhado |
| `infra/` | Infraestrutura (OpenTofu) |
| `docker/` | Ambiente local (`docker compose`) |
| `docs/` | Roadmap, arquitetura, ADRs, convenções e registros |
| `specs/` | Specs, planos e tarefas por etapa |
| `prototypes/` | Protótipos descartáveis da Etapa 0 (não são produção) |

## Como trabalhamos

Spec-driven: nenhuma funcionalidade começa sem spec aprovada. Documentos e código passam por revisão no Plannotator antes de commit e push. Detalhes no `CLAUDE.md`.

## Repositório público

Nada de segredo, chave, `.env`, dado de usuário ou exame real neste repositório.
