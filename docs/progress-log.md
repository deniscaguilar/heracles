# Diário de progresso

Registra onde paramos e o próximo passo. Entrada mais recente no topo; atualizar ao fim de cada tarefa.

## 2026-10-29 (Etapa 0, preparação)

**Feito**
- Spec, plano e tarefas da Etapa 0 aprovados e na `main` (PR #3); regra do board de tarefas no Notion no `CLAUDE.md` (PR #4).
- Board "Heracles · Tarefas" criado no Notion com as 32 tarefas da Etapa 0.
- T0.1: `prototypes/README.md` e `docs/stage-0/README.md` criados.
- T0.2: `local/` já estava no `.gitignore`; README local criado e confirmado como ignorado.

- T1.1 (P10): decidido um **único app web React** (PWA do cliente + backoffice em `/admin`), como no EndoAcademy; dois clientes (mobile e web). `CLAUDE.md`, `architecture.md`, ADR-0001, README e roadmap ajustados.
- T1.2 e T1.3 (P8): `docs/stage-0/p8-pendencias.md` com 12 temas jurídicos (J1 a J12) e 12 itens científicos (C1 a C12), todos como pendência aberta, e as travas vigentes (produto só do Dênis).
- T1.4 (P9): comparativo de gateways no Notion (números não confirmados em fonte oficial) e registro `docs/stage-0/p9-gateway.md`; decisão adiada para a Etapa 6. Ordem 1 da Etapa 0 concluída.
- T2.1 a T2.3 (P4): ElasticMQ 1.7.1 e LocalStack 4.14.0 passam nos mesmos cenários; proposta de ElasticMQ para SQS local (ADR-0004 atualizada, provisória). Registro em `docs/stage-0/p4-filas.md`.
- T2.5: ADR-0004 aprovada e mergeada (PR #9); T2.4 (fumaça no SQS real) adiada pelo Dênis.
- T2.6 a T2.9 (P2): protótipo Kotlin da troca em `prototypes/swap/` (Maven, 9 testes verdes), 20 casos em `data/cases.tsv` (não validados), regras de arredondamento e latência (p99 de 0,0015 ms por troca; 1,2 ms para gerar opções sobre 1.358 candidatos). Registro em `docs/stage-0/p2-troca.md`.

**Próximo passo**
- Ordem 3: P1 (sincronização offline, T3.1 a T3.4) e P3 (exames, T3.5 a T3.10; T3.7 pede autorização).

## 2026-10-29

**Feito**
- Roadmap v5, `docs/architecture.md`, ADRs 0001 a 0007 e `CLAUDE.md` aprovados no Plannotator e mergeados na `main` (PR #1).
- Repositório público `heracles` criado e conectado.
- Fluxo de revisão de PR definido: diff local contra a base no Plannotator; aprovação no GitHub não é exigida.

**Próximo passo**
- Etapa 0 do roadmap (decisões e provas): sincronização offline, emulador de SQS, acurácia da extração de exames, integração de wearables e bioimpedância, orçamento de latência, escopo do PWA, revisão jurídica e pesquisa de gateway de pagamento.
- Depois, a spec da Etapa 1 (fundação).

**Pendências**
- `user.email` do git local está como `denis@example.com`; trocar para o e-mail da conta do GitHub (só vale para commits novos, sem reescrever o histórico).
- Ativar secret scanning e push protection no GitHub e, quando o plano permitir, revisar a visibilidade do repositório.
