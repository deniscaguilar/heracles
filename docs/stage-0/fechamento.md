# Fechamento da Etapa 0 (decisões e provas técnicas)

- **Tarefa:** T6.2 (`specs/platform/stage-0-proofs/tasks.md`)
- **Data:** 29/10/2026
- **Estado proposto:** **concluída com pendências.** A Etapa 1 (fundação) pode começar. As pendências abaixo **não bloqueiam a fundação**; as que bloqueiam estão marcadas e ficam para etapas posteriores.
- **Decisão do Dênis (29/10/2026):** fechamento aceito via Plannotator.

## 1. Critério de conclusão (spec, seção 5)

| Critério | Estado | Onde |
|---|---|---|
| Todas as provas têm registro em `docs/stage-0/` | **Sim**, com a P3 parcial (só o candidato local foi medido) | `README.md` desta pasta |
| Ferramenta de sincronização escolhida em ADR | **Sim, provisória**: `expo-sqlite` com sincronização própria; criptografia local provada | ADR-0005 |
| Emulador de SQS escolhido em ADR | **Sim, provisório**: ElasticMQ; falta o teste no SQS real (T2.4) | ADR-0004 |
| Provedor de identidade escolhido em ADR | **Sim, provisório**: Amazon Cognito; login por conta própria, Google e Apple | ADR-0008 |
| **Provedor de IA e OCR escolhido em ADR** | **Não.** Candidatos levantados e linha de base local medida; falta escolher, preparar conta e autorizar o envio (T3.7 a T3.10) | `p3-provedores.md`, `p3-extracao-exames.md`; ADR-0007 sem mudança |
| Casos de aceitação da troca validados | **Não validados** por profissional: 20 casos prontos e testados, marcados "propostos pela IA" | `p2-troca.md`, pendência C3 |
| Orçamento de latência fixado | **Sim, como proposta** a validar com Postgres real na Etapa 1 | `docs/architecture.md`, seção 7.1 |
| Jurídico e parceiro científico com pendência registrada | **Sim**: 12 temas jurídicos e 12 científicos, todos abertos | `p8-pendencias.md` |

## 2. O que a Etapa 0 decidiu

| Decisão | Resultado | Registro |
|---|---|---|
| Clientes | App mobile (React Native) e **um único app web** (PWA do cliente e backoffice em `/admin`) | `p10-pwa.md`, ADR-0001 |
| Sincronização offline | `expo-sqlite` + sincronização própria, com SQLCipher; opções WatermelonDB e PowerSync **não executadas** | ADR-0005, `p1-sincronizacao.md` |
| Troca de alimento | Regra em Kotlin, `BigDecimal`, 20 casos; cálculo de 0,0015 ms por troca, 1,2 ms para gerar opções | `p2-troca.md`, ADR-0006 |
| Filas | ElasticMQ no ambiente local; mesmos resultados do LocalStack, com menos memória | `p4-filas.md`, ADR-0004 |
| Extração de exame | Tipo reconhecido por regras em 5 de 5; extração de sangue por regra tem cerca de metade de acerto: **precisa de modelo de IA** | `p3-extracao-exames.md` |
| Wearables e bioimpedância | Viável no Android e no iOS (simulador), só no app mobile; bioimpedância pela plataforma de saúde; entra na Etapa 7 | `p6-wearables.md` |
| Identidade | Cognito, provisório | ADR-0008, `p7-identidade.md` |
| API | Convenções de API | `docs/api-conventions.md` |
| Telas | Telas em baixa fidelidade do app e do backoffice | `docs/design/telas-baixa-fidelidade.md` |
| Gateway de pagamento | Pesquisa; decisão adiada para a Etapa 6 | `p9-gateway.md` |
| Substituição de exercício | Registrada para a Etapa 4 | `docs/roadmap.md` |

## 3. Pendências e o que cada uma bloqueia

| # | Pendência | Dono | Bloqueia |
|---|---|---|---|
| 1 | **Provedor de IA e OCR** (T3.7 a T3.10): escolher, preparar conta, autorizar envio, medir, atualizar ADR-0007 | Dênis | **Etapa 3** (exames com IA). Não bloqueia a Etapa 1 |
| 2 | **Teste de fumaça no SQS real** (T2.4) e confirmar o ElasticMQ | Dênis (custo e conta AWS) | Nada na Etapa 1; fecha a ADR-0004 |
| 3 | **Chave de criptografia no Keychain e no Keystore** (`expo-secure-store`, dependência a aprovar) | Dênis (aprovação) | **Qualquer dado de saúde real no aparelho de terceiros** (Etapa 5 em diante) |
| 4 | **Validação científica** dos 20 casos da troca, dos pisos e das equivalências (C1 a C12) | Parceiro científico (a apresentar o produto quando estiver redondo) | **Abrir o produto a terceiros** (Etapa 6 em diante); a Etapa 4 pode construir |
| 5 | **Pareceres jurídicos** (J1 a J12) | Advogado (a definir) | **Abrir o produto a terceiros**, IA com dado de terceiros e cobrança real |
| 6 | **Cognito:** região de São Paulo, preço oficial, pt-BR, contas de desenvolvedor da Apple e do Google, prova real de login com PKCE | Dênis | Etapa 2 em produção; a Etapa 1 usa o servidor OIDC de mentira |
| 7 | **WatermelonDB e PowerSync** não executados; backend Kotlin do protocolo de sincronização | IA, na spec da Etapa 5 | Nada agora; gatilhos no ADR-0005 |
| 8 | **Wearables:** repetir o iOS com todos os tipos, aparelho real, dado vindo da InBody | Dênis (aparelho) | Etapa 7 |
| 9 | **Gateway de pagamento:** conferir taxas e regras das lojas | IA e Dênis | Etapa 6 |
| 10 | **Latência:** validar o orçamento com Postgres real | IA | Etapa 1 mede |
| 11 | **Calorimetria, SIBO e ultrassom:** sem exemplo (T3.6 cancelada) | Dênis | Medição dessas famílias na Etapa 3 |

## 4. Travas que seguem valendo

Produto **só do Dênis** até haver parecer jurídico e validação científica; **IA externa só com dado do próprio Dênis**, com autorização escrita a cada envio; nenhuma regra de troca ou de plano vai a outro usuário antes da validação (`p8-pendencias.md`, seção 1).

## 5. Próximo passo: Etapa 1 (fundação)

A Etapa 1 pode começar pela **spec** (`specs/platform/...`), seguindo o fluxo do CLAUDE.md. Entradas já decididas para ela:
- Monólito modular, módulo `platform`, RLS por usuário, auditoria append-only (ADR-0001 e 0003).
- Filas SQS com outbox e ElasticMQ local (ADR-0004).
- Identidade por port, com servidor OIDC de mentira no `docker compose` (ADR-0008).
- Convenções de API (`docs/api-conventions.md`) e orçamento de latência (`docs/architecture.md`, 7.1).
- Estrutura do repositório: `backend/`, `mobile/`, `web/` (app web único), `packages/`, `infra/`, `docker/`.
- **A decidir na spec da fundação:** emulador de S3 local, mecanismo de eventos (registro do Spring Modulith ou tabela própria), JDK e versões, e nome do pacote raiz.

## 6. Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
