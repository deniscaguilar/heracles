# Diário de progresso

Registra onde paramos e o próximo passo. Entrada mais recente no topo; atualizar ao fim de cada tarefa.

## 2026-10-30 (tarefas, RLS)

- **E1-S2.3** (tabelas de teste e CA6): `V1000__test_tables.sql` em `src/test/resources/db/test-migration/platform` (só no teste; nunca vai ao pacote), criada pelo `heracles_owner`: `test_user_note` (dado de usuário) e `test_health_sample` (dado de saúde), ambas com RLS `ENABLE` e `FORCE` e política `user_id = platform.current_app_user_id()` em `USING` e `WITH CHECK`. O `PostgresInitializer` soma essa pasta às `locations` do Flyway. `RowLevelSecurityIT` (6 testes): cada usuário vê só as suas linhas mesmo com consulta sem filtro; sem usuário volta zero linha (leitura) e a escrita é recusada; `admin` e `system` não leem nem a tabela de saúde nem a de usuário (as linhas existem: conferido com a conexão administrativa); não dá para gravar linha de outro usuário nem alterar ou apagar a dele; o `heracles_owner` também fica sob a política (prova do `FORCE`; provado que o teste falha sem `FORCE`). Próxima: E1-S2.4 (teste do catálogo, CA7).

## 2026-10-30 (tarefas, contexto de transação)

- **E1-S2.2** (contexto de transação): domínio `AccessContext` e `AccessScope` (`user`, `admin`, `system`; usuário obrigatório só no escopo `user`), porta `TransactionContext` (`application/port/out`) e adaptador `JdbcTransactionContext`, que abre a transação e executa `set_config('app.user_id', ..., true)` e `set_config('app.scope', ..., true)` (equivale a `SET LOCAL`). Aninhar contextos é recusado. `V2__access_context_functions.sql` cria `platform.current_app_user_id()` (nulo sem contexto, então a política não casa nenhuma linha) e `platform.current_app_scope()` (padrão `user`). Termos `accessContext` e `accessScope` entraram no glossário do `CLAUDE.md`. Testes: 5 unitários e 7 de integração com pool de uma conexão (mesmo `pg_backend_pid` dentro e fora da transação, sem vazamento; falha limpa o contexto); provado que quebra se o `set_config` virar de sessão. O portão do JaCoCo passa a medir `domain` de verdade. Próxima: E1-S2.3 (tabelas de teste com RLS forçada).

## 2026-10-30 (tarefas, banco)

- **E1-S2.1** (Flyway por módulo e papéis): `V1__roles_and_schema_privileges.sql` em `db/migration/platform` (schema `platform`, histórico `platform.flyway_schema_history`). Cria `heracles_owner` (dono dos objetos) e `heracles_app` (sem superusuário, sem `BYPASSRLS`, sem DDL), revoga tudo de `PUBLIC` no banco e no `public`, e deixa privilégios padrão de DML para o `heracles_app` nas tabelas do `heracles_owner`. Senhas por *placeholder* do Flyway (variáveis de ambiente; nunca no repositório). O Flyway roda como administrador do banco; a aplicação conecta como `heracles_app`. Dependências (previstas no plano): `spring-boot-starter-jdbc`, `-flyway`, `flyway-database-postgresql`, `postgresql`; teste: `spring-boot-testcontainers` e `testcontainers-postgresql` (todas pelo BOM do Boot). Todo contexto Spring de teste agora sobe um PostgreSQL 18 real (mesmo digest do compose) por `spring.factories`. `DatabaseRolesIT` prova os papéis e que DDL dá `42501`; provado que falha quando o papel vira superusuário. Compose do backend recebeu as variáveis do banco; `docker/check.sh` verde. **Convenção para as próximas migrações (V2 em diante):** começar com `SET LOCAL ROLE heracles_owner;`, para os objetos nascerem do dono (S2.4 vai checar). Próxima: E1-S2.2 (contexto de transação).

## 2026-10-30 (tarefas, proteção da main)

- **E1-S1.11** (com o ok do Dênis): proteção da `main` por API: exige PR, os 4 checks do CI (`backend`, `dependency-scan`, `image-scan`, `npm-audit`) com branch atualizada, histórico linear, sem force push nem exclusão, valendo também para administrador; 0 aprovações exigidas (desenvolvimento solo). Secret scanning e push protection já estavam ativos. Provado: `git push origin HEAD:main` rejeitado ("protected branch hook declined"); `gh pr merge` com CI pendente recusado ("4 of 4 required status checks are expected", PR #38 descartável fechado); push de um segredo falso (chave no formato Stripe) em branch descartável rejeitado por push protection, sem nada publicado. **Fim da fatia S1.** Próximo: S2 (banco e RLS), E1-S2.1.

## 2026-10-30 (tarefas, pipeline de CI)

- **E1-S1.7 a S1.10** (concluídas e provadas no GitHub em 30/10/2026: PR #34 reprovou no JaCoCo, PR #35 reprovou no `dependency-scan` e no Trivy com `log4j-core 2.14.1`, ambos fechados sem merge; o Dependabot abriu sozinho o PR #33): `.github/workflows/ci.yml` com `backend` (`./mvnw verify` no JDK 25; JaCoCo 0.8.16 com portão de 85% de linhas e branches só em `domain` e `application`; SBOM CycloneDX 2.9.3 como artefato), `dependency-scan` (OSV-Scanner sobre o SBOM, ADR-0009), `image-scan` (Trivy, HIGH e CRITICAL) e `npm-audit`. Dependabot para Maven, Actions, Docker e npm. Ações fixadas por SHA. Provado **localmente**: o portão do JaCoCo reprova com uma classe de `domain` sem teste (e fica verde sem ela; um `mvnw clean` é necessário para tirar a classe velha); o `osv-gate.sh` reprova `log4j-core 2.14.1` (CVSS 9,0 e 10,0) e passa o SBOM real; o Trivy não acha nada na imagem do backend. O PR #33 do Dependabot (Maven 3.9.14 para 3.10.0 no wrapper, CI verde) segue aberto, esperando decisão do Dênis. O `npm audit` passa vazio até existir `package-lock.json`. O gate do JaCoCo hoje passa por não haver classes em `domain` e `application`; ele passa a valer com o primeiro código. ADR-0009 aprovada em 30/10/2026.

## 2026-10-30 (encerramento da sessão: onde paramos)

**Estado do projeto**
- **Etapa 1 em andamento: 6 de 45 tarefas** (E1-S1.1 a E1-S1.6, PRs #26 a #30 na `main`). Cartões E1-S1.4 a S1.6 em `DONE` no Notion.
- Sem alterações locais pendentes; nenhuma branch aberta além desta de diário.

**Como retomar**
1. Ler o `CLAUDE.md`, `git checkout main` e `git pull`.
2. JDK 25: `sdk env` em `backend/`; `./mvnw verify` para conferir.
3. **Próximo: E1-S1.7 a S1.10 (pipeline de CI)**, que pretendo entregar numa revisão só: workflow do GitHub Actions com `./mvnw verify` e portão JaCoCo ≥ 85% em `domain` e `application` (JaCoCo 0.8.16), scan de dependências (decidir OWASP ou OSV-Scanner), Trivy na imagem, SBOM CycloneDX (plugin 2.9.3), Dependabot e `npm audit`. Hoje **não há `.github/`** e nenhum CI roda nos PRs; só dá para provar o reprovar-de-propósito depois de empurrar. Depois: S1.11 (pede ok), S2 (banco e RLS) em diante, conforme `specs/platform/stage-1-foundation/tasks.md`.
4. Fluxo: uma revisão no Plannotator antes do commit; aprovada, commit, push, PR, merge e limpeza sem perguntar; mover o cartão no Notion e atualizar `tasks.md` e este diário.

**Ambiente local** (para não estranhar)
- O compose usa o projeto `heracles-dev` e as portas 5433 (Postgres), 9324 (SQS), 4567 (S3), 8082 (OIDC), 3001 (Grafana), 4327/4328 (OTLP) e 8080 (API). O `clinic-system` ocupa as portas padrão. Subir: `docker compose -f docker/compose.yml up -d --build --wait` e `docker/check.sh`.
- Existe um projeto Docker antigo `heracles` (`~/Documents/projetos/heracles`): o contêiner `postgres_db` dele foi recriado por engano no primeiro `up` (o volume `heracles_postgres_data` está intacto) e `heracles-redis-1` continua lá.
- Permissões do Claude Code: `git push`, `git merge` e `gh pr merge` agora estão em `allow` no `settings.json` global (feito pelo Dênis); force push segue em `deny`.

**Pendências que dependem do Dênis** (as mesmas de antes)
- E1-S1.11 (proteção da `main` e secret scanning), E1-S8.1 (estado remoto e conta AWS), E1-S8.3 e E1-S8.4 (ambiente de fumaça, geram custo): pedem ok.
- Provedor de IA e OCR (Etapa 3), teste de fumaça no SQS real (T2.4), validação científica e pareceres jurídicos, contas Apple e Google, Cognito em São Paulo.

## 2026-10-30 (tarefas)

- **E1-S1.5 e E1-S1.6** (`docker compose` e Dockerfile): `docker/compose.yml` com PostgreSQL 18, ElasticMQ 1.7.1, LocalStack 4.14.0 (só S3), `mock-oauth2-server` 3.0.1, `grafana/otel-lgtm` e o backend, tudo por digest, com healthchecks. `docker/check.sh` confirma cada serviço. Projeto `heracles-dev` e portas deslocadas (5433, 4567, 8082, 3001, 4327/4328) porque o `clinic-system` ocupa as padrão e já existia um projeto antigo `heracles` (o primeiro `up` recriou o contêiner `postgres_db` dele; o volume `heracles_postgres_data` ficou intacto). Dockerfile multi-stage com usuário 10001; a mesma imagem subiu nos perfis `all` e `worker`. Próxima: E1-S1.7 (CI).
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
