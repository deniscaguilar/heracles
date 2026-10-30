# Diário de progresso

Registra onde paramos e o próximo passo. Entrada mais recente no topo; atualizar ao fim de cada tarefa.

## 2026-10-30

- **E1-S1.4** (perfis `api`, `worker` e `all`): padrão `all` em `application.yml`; anotações `@ApiOnly` e `@WorkerOnly` em `platform/config` para marcar beans por papel. `RuntimeRoleIT` prova que `api` não sobe o worker e `worker` não sobe a API. `./mvnw verify` verde (9 unitários, 7 de integração). Próxima: E1-S1.5 (`docker compose`).

## 2026-10-29 (encerramento da sessão: onde paramos)

**Estado do projeto**
- **Etapa 0 concluída com pendências** (`docs/stage-0/fechamento.md`).
- **Etapa 1 (fundação técnica) em andamento**: spec, plano e tarefas aprovados e na `main`. Concluídas **E1-S1.1** (estrutura de pastas), **E1-S1.2** (`pom.xml`, wrapper e aplicação mínima, JDK 25, Spring Boot 4.1.2) e **E1-S1.3** (pacotes do módulo `platform`, regras do ArchUnit com prova de violação e `Modulith.verify()`; 9 testes unitários e 3 de integração verdes com `./mvnw verify`). 3 de 45 tarefas da Etapa 1.
- **Board no Notion** ("Heracles · Tarefas", Etapas E0 e E1): cartões atualizados até a E1-S1.3.

**Como retomar (regras do `CLAUDE.md`)**
1. Ler o `CLAUDE.md` e **atualizar a `main`** (`git checkout main` e `git pull`).
2. Usar o JDK 25: `sdk env` em `backend/` (`.sdkmanrc`) e `./mvnw verify` para conferir que está verde.
3. **Próxima tarefa: E1-S1.4** (perfis `api`, `worker` e `all`), depois S1.5 (`docker compose` local) e o resto da fatia S1 (CI, scans, SBOM, Dependabot). Ordem completa em `specs/platform/stage-1-foundation/tasks.md`.
4. Fluxo: uma revisão no Plannotator antes do commit; aprovada, a IA faz commit, push, PR, merge e limpeza sem perguntar; mover o cartão no Notion e atualizar `tasks.md` e este diário.

**Pendências que dependem do Dênis** (nenhuma bloqueia a S1)
- **Provedor de IA e OCR** (T3.7 a T3.10 da Etapa 0): escolher, preparar conta e autorizar o envio; bloqueia a Etapa 3. Candidatos e checklist em `docs/stage-0/p3-provedores.md` e no Notion.
- **Teste de fumaça no SQS real** (T2.4): adiado.
- **Tarefas da Etapa 1 que pedem ok**: E1-S1.11 (proteção da `main` e secret scanning), E1-S8.1 (estado remoto e conta AWS), E1-S8.3 e E1-S8.4 (ambiente de fumaça, geram custo).
- **Validação científica** e **pareceres jurídicos** (`docs/stage-0/p8-pendencias.md`): bloqueiam abrir o produto a terceiros (Etapa 6 em diante). O produto segue só do Dênis.
- **Contas de desenvolvedor da Apple e do Google** e confirmação do Cognito em São Paulo (ADR-0008).

**Ambiente da máquina** (para não estranhar): Xcode 26.6 e simulador do iOS 26.5, Android SDK e emulador `heracles-p1`, Node 22 (padrão do nvm continua 18), Tesseract e poppler, CocoaPods, `mas`. Exames reais só em `local/` (fora do git).

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
- T5.1 (P7): telas em baixa fidelidade aprovadas pelo Dênis (`docs/design/telas-baixa-fidelidade.md`): navegação, telas do app, estados compartilhados e backoffice. Sem Figma por ora.
- Xcode instalado e licença aceita; dependências do teste de iOS (`@kingstinct/react-native-healthkit` e `react-native-nitro-modules`) aprovadas pelo Dênis.
- P6 iOS: Xcode 26.6 e simulador do iOS 26.5 instalados; app compilado e rodando no simulador; HealthKit gravou e leu 3 de 5 tipos (peso e gordura sem permissão). Achados: permissão por tipo e leitura negada vem como lista vazia. Registro em `docs/stage-0/p6-wearables.md`.
- P1, SQLCipher: criptografia local provada no Android (emulador) e no iOS (simulador) com `expo-sqlite` e `useSQLCipher` (SQLCipher 4.17.0): arquivo ilegível sem chave, chave errada falha, custo pequeno. Chave de teste fixa; falta o armazenamento seguro da chave. ADR-0005 e `p1-sincronizacao.md` atualizados.
- Requisito novo do Dênis: **substituição de exercício** (por equipamento equivalente, como supino reto com halter por barra, ou por outro do mesmo grupo muscular). Decisão: **fica na Etapa 4**, não na Etapa 0 (consulta ao catálogo, sem o risco técnico da troca de alimento; regras dependem do educador físico). Registrado no roadmap (Etapa 4, anamnese da Etapa 2 e ideias aprovadas), no glossário do `CLAUDE.md`, na pendência C10, nas telas e na arquitetura.
- T6.2: fechamento da Etapa 0 em `docs/stage-0/fechamento.md`, **aprovado pelo Dênis**: concluída com pendências; a Etapa 1 pode começar. Pendência principal que não bloqueia a fundação: provedor de IA e OCR (T3.7 a T3.10).
- Spec da Etapa 1 (fundação) aprovada pelo Dênis (`specs/platform/stage-1-foundation/spec.md`): 11 requisitos e 20 critérios de aceite; JDK 25, Boot 4.1, Modulith 2.1, `com.heracles`, LocalStack só para S3; casca dos clientes e ambiente de fumaça na AWS entram na etapa.
- Regras de fluxo git do Dênis no `CLAUDE.md`: uma revisão por mudança, depois commit, push, PR, merge e limpeza sem perguntar; atualizar a `main` ao iniciar.
- `plan.md` da Etapa 1 aprovado (`specs/platform/stage-1-foundation/plan.md`): outbox próprio, RLS por transação, 8 fatias (S1 a S8) e lista de dependências aprovada, incluindo `expo-secure-store` (resolve a pendência da chave de criptografia local).
- `tasks.md` da Etapa 1 (`specs/platform/stage-1-foundation/tasks.md`): 45 tarefas em 8 fatias mais o fechamento, test-first, com as que pedem ok marcadas (S1.11, S8.1, S8.3, S8.4). As 45 estão no board "Heracles · Tarefas" do Notion (Etapa E1; a coluna "Prova" guarda a fatia S1 a S8 e F).
- E1-S1.1: estrutura de pastas (`backend/`, `mobile/`, `web/`, `packages/`, `infra/`, `docker/`) com README em cada uma; README raiz com a tabela de pastas.
- E1-S1.2: `backend/pom.xml` e o wrapper (Maven 3.9.14); `./mvnw verify` verde com JDK 25 (3 testes de integração: liveness 200, health sem detalhes, outros endpoints do actuator não expostos); o enforcer recusa o JDK 21. **Versões (conferidas no Maven Central em 29/10/2026):** Spring Boot 4.1.2 (a 4.2.0-RC1 não é GA), Kotlin 2.3.21 (pelo BOM do Boot), Jackson 3.1.7, Tomcat 11.0.27, JDK 25.0.2 (SDKMAN, `backend/.sdkmanrc`). Modulith 2.1.2, MockK 1.14.11, AWS SDK 2.57.7, springdoc 3.2.0, JaCoCo 0.8.16 e ArchUnit 1.5.1 são as últimas GA e entram nas tarefas em que forem usados.

- E1-S1.3: pacotes do módulo `platform` (`api`, `domain`, `application`, `adapter`, `config`) e regras do ArchUnit em `ArchitectureRules` (camadas, domínio sem Spring/JPA/Jackson/AWS, sem pacote `core`, `common` ou `shared`, módulo só usa outro pela `api`), com **classes de exemplo com violação proposital** que provam que cada regra falha; `Modulith.verify()` com as classes de exemplo fora. Dependências: `spring-modulith-starter-core` e `-test` (2.1.2) e `archunit-junit5` (1.5.1), da lista aprovada. `./mvnw verify` verde: 9 testes unitários e 3 de integração. **Limite:** ainda não há classe de produção nas camadas do `platform`, então as regras passam sem exercitar código real; a prova de que funcionam são as classes de exemplo, e elas passam a valer para o código real a partir da S2.

**Próximo passo**
- E1-S1.4: perfis `api`, `worker` e `all`.

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
