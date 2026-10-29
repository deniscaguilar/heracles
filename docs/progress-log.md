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
- P3: candidatos de OCR e IA levantados (AWS, Google, Anthropic, Azure, OpenAI e local) no Notion e em `docs/stage-0/p3-provedores.md`. Exames do Dênis em `local/exams/` (5 arquivos).
- P3, T3.5: Dênis autorizou a leitura dos 5 exames; gabaritos criados em `local/gabaritos/` (fora do git, têm valores reais). Os exames são 2 laudos de sangue, 2 de imagem (tomografia e ressonância, não ultrassom) e 1 foto InBody (JPEG).
- P3, candidato F (local): `prototypes/exam-extraction/local_baseline.py` (Tesseract e poppler instalados via Homebrew). Resultados em `docs/stage-0/p3-extracao-exames.md`.
- P1, T3.1: ambiente instalado (Node 22 pelo nvm sem mudar o padrão, Android SDK e emulador Android 15 pelo Homebrew; sem Xcode). Protótipo `prototypes/sync/` com a opção A (expo-sqlite + sincronização própria): 6 cenários verdes, 10 mil registros no emulador (envio 0,75 s, pull 0,22 s). B e C não executadas. Registro em `docs/stage-0/p1-sincronizacao.md`.
- O template do Expo criou um `.claude/settings.json` que ativava um plugin do Claude; removido.
- P1 decidida pelo Dênis: opção A provisória; ADR-0005 atualizada (T3.2 a T3.4). Pendências: SQLCipher, B e C, backend Kotlin, iOS.
- T3.6 cancelada: o Dênis não tem calorimetria nem SIBO; segue só com os exames que tem.
- T3.7: procedimento, checklist e modelo de autorização em `docs/stage-0/p3-provedores.md`; falta o Dênis escolher o provedor e preparar a conta.
- T3.7 adiada pelo Dênis; sigo com as tarefas que não dependem dela. T3.8 a T3.10 (provedores externos) ficam bloqueadas junto.
- T5.2 (P7): `docs/api-conventions.md` (rascunho): versão no caminho, camelCase, UUID, decimais como string, erros Problem Details, cursor, idempotência, tarefas assíncronas.
- T6.1 (P5): orçamento de latência na seção 7.1 de `docs/architecture.md`, com os números medidos nas provas P1, P2 e P4.
- T5.3 (P7): ADR-0008 aprovada: Amazon Cognito provisório, login por conta própria, Google e Apple; `ADMIN` e `MODERATOR` só com conta própria e MFA. Comparativo no Notion. Pendências: região de São Paulo, preço oficial, pt-BR, contas de desenvolvedor da Apple e do Google, prova real de login PKCE.
- T4.1 e T4.2 (P6): matriz de Health Connect e HealthKit e teste com dados simulados no emulador Android (5 registros gravados e lidos). iOS e bioimpedância real pendentes. Build de desenvolvimento do Android funcionando (destrava SQLCipher da P1). Registro em `docs/stage-0/p6-wearables.md`.

**Próximo passo**
- T5.1 (telas em baixa fidelidade) e T6.2 (fechamento).
- P3: Dênis escolhe o provedor e prepara a conta (T3.7); depois T3.8 a T3.10.
- Depois da P3: ordem 4 (P6 wearables), ordem 5 (P7 telas, API e autenticação) e o fechamento (P5 latência e T6.2).

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
