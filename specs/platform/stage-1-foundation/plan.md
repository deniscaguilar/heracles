# Plano: Etapa 1, fundação técnica

- **Spec:** `spec.md` (Aprovada em 29/10/2026)
- **Status:** Aprovada (29/10/2026)
- **Módulo:** `platform` (mais infraestrutura, CI e casca dos clientes)
- **Aprovado o plano, está aprovada também a lista de dependências da seção 9** (CLAUDE.md, seção 10). Dependência fora da lista pede nova aprovação.

## 1. Visão da solução

A fundação se apoia em quatro ideias:

1. **O banco é a última barreira.** A aplicação conecta com um papel sem privilégio de dono (`heracles_app`) e sem `BYPASSRLS`; toda tabela de dado de usuário tem RLS **forçada**; o usuário e o escopo chegam **por transação** (`set_config('app.user_id', ..., true)` e `set_config('app.scope', ..., true)`). Sem contexto, a consulta devolve zero linhas (ADR-0003). Um bug de consulta não expõe dado de outro usuário.
2. **O token só identifica.** O JWT traz o `sub`; perfis (`USER`, `ADMIN`, `MODERATOR`) e permissões ficam no banco do `platform` (ADR-0008). O provedor (Cognito) fica atrás do port `IdentityProvider`; na fundação roda um servidor OIDC de mentira.
3. **Módulos conversam por `api` e por eventos com identificadores**, nunca com dado de saúde. Reação dentro do processo usa eventos de aplicação do Spring; trabalho que pode esperar vira mensagem na fila pelo **outbox** (ADR-0004).
4. **Tudo o que é externo fica atrás de um port**, com adaptador real e adaptador local ou falso. Os testes usam só os locais.

**Decisão deste plano sobre o mecanismo de eventos:** usar uma **tabela de outbox própria** (`platform.outbox_message`) com publicador por consulta (`FOR UPDATE SKIP LOCKED`), e **não** o registro de publicação de eventos do Spring Modulith. Motivos: controle explícito de nova tentativa, DLQ e idempotência; a mensagem leva **só identificadores** (o registro do Modulith serializa o objeto do evento, que poderia carregar mais do que deve); e o desenho fica igual para fila SQS e para o que roda dentro do processo. O Modulith segue para verificação de limites (`Modulith.verify()`) e documentação dos módulos.

## 2. Fatias de entrega

Cada fatia termina com `./mvnw verify` e o pipeline verdes, e vira um grupo no `tasks.md`.

| Fatia | Conteúdo | RF e CA |
|---|---|---|
| S1 | Esqueleto: estrutura de pastas, Maven e wrapper, perfis `api` e `worker`, ArchUnit e Modulith, **pipeline completo** (testes, cobertura, scan, SBOM, Dependabot, proteção da `main`), `docker compose` local | RF1, RF2; CA1, CA2, CA3, CA19 |
| S2 | Banco: papéis, Flyway por módulo, contexto de transação, RLS, tabelas só de teste, teste automático "tabela nova exige RLS e teste de isolamento" | RF4; CA6, CA7 |
| S3 | Identidade: validação de JWT, provisionamento do usuário, perfis no banco, `GET /api/v1/me`, adaptador de teste do `IdentityProvider` | RF3; CA4, CA5 |
| S4 | Auditoria append-only e observabilidade sem dado de saúde | RF5, RF6; CA8, CA9 |
| S5 | Erros padronizados, OpenAPI versionado, cliente TypeScript gerado em `packages/` | RF7; CA10, CA11 |
| S6 | Assíncrono: outbox, publicador, adaptador SQS, worker, consumidor idempotente, nova tentativa, DLQ, tarefas, métricas e reprocessamento; demais ports com adaptador local | RF8, RF9; CA12 a CA17 |
| S7 | Casca dos clientes: app web (login OIDC, shell por perfil, `/admin`) e app mobile (login e shell), com o cliente gerado | RF11; CA20 |
| S8 | Infraestrutura OpenTofu e ambiente de fumaça efêmero (com autorização e custo combinados) | RF10; CA18 |

A ordem é a da tabela, exceto que a S7 e a S8 podem andar em paralelo depois da S5.

## 3. Casos de uso (camada `application`)

Todo caso de uso recebe o `CurrentUser` (identificador, perfil) e confere o perfil antes de agir; sem permissão, `403` e auditoria com resultado `DENIED`.

| Caso de uso | Entrada | Saída | Regras |
|---|---|---|---|
| `ProvisionUser` | `sub` do token | usuário `platform` | Cria no primeiro acesso com perfil `USER`; repetir devolve o existente; audita `USER_CREATED` na criação |
| `GetMe` | `CurrentUser` | `id` e perfil | Só identificador e perfil, sem e-mail nem nome |
| `RecordAudit` (`api`) | ação, tipo e id da entidade, resultado, **nomes de campo** | | Recusa valores no lugar de nomes; usado por todos os módulos pelo port `AuditTrail` |
| `QueryAuditTrail` (`ADMIN`) | filtros, página | eventos | Só nomes e identificadores; paginação por cursor |
| `PublishOutbox` (worker) | | | Pega lote com `FOR UPDATE SKIP LOCKED`, envia pelo `TaskQueue`, marca `published_at`; falha incrementa tentativas com espera crescente |
| `ConsumeTask` (worker) | mensagem | | Checa `processed_message`; se já tratada, só confirma; senão trata e grava na mesma transação |
| `GetTaskStatus` | id da tarefa | status e `result_ref` | Só o dono vê a sua tarefa (RLS); sem dado de saúde |
| `ReprocessDeadLetter` (`ADMIN`) | id da mensagem na DLQ | | Devolve à fila e reinicia a tarefa; efeito ocorre uma vez (idempotência) |

## 4. Modelo de dados (schema `platform`)

| Tabela | Colunas principais | Isolamento |
|---|---|---|
| `app_user` | `id` (uuid), `sub` (único), `role`, `status`, `created_at` | O usuário lê só a própria linha; escopo `admin` lê a lista (não é dado de saúde) |
| `audit_event` | `id`, `occurred_at`, `actor_user_id`, `actor_scope`, `action`, `entity_type`, `entity_id`, `result`, `fields` (texto[], só nomes), `trace_id` | **Append-only por trigger** (`UPDATE` e `DELETE` recusados); leitura só no escopo `admin`; inserção por função do banco |
| `outbox_message` | `id`, `type`, `aggregate_type`, `aggregate_id`, `payload` (jsonb, só identificadores), `created_at`, `published_at`, `attempts`, `next_attempt_at` | Só escopo `system` |
| `task` | `id`, `user_id` (nulo para tarefa de sistema), `type`, `status`, `attempts`, `last_error_code`, `created_at`, `updated_at`, `result_ref` | O dono lê a sua; `system` lê e escreve |
| `processed_message` | `consumer`, `message_id` (chave composta), `processed_at` | Só `system` |

- **Tabelas só de teste** (migrações no escopo de teste, nunca em produção): `test_user_note` (dado de usuário) e `test_health_sample` (dado de saúde), para provar CA6 e CA7.
- **Papéis do banco:** `heracles_owner` (dono dos objetos, roda as migrações), `heracles_app` (usado pela aplicação, sem `BYPASSRLS`, sem poder de DDL). Há um teste que falha se `heracles_app` for superusuário ou tiver `BYPASSRLS`.
- **Migrações Flyway** por módulo (`db/migration/platform`, tabela de histórico própria). Migração já aplicada é imutável (CLAUDE.md, seção 9).
- **Teste que protege o futuro (CA7):** varre o catálogo do banco e falha se existir tabela com coluna `user_id` sem RLS ativa e forçada, ou sem teste de isolamento registrado numa lista.

## 5. Contexto de transação e RLS

- Um interceptor de transação (adaptador de saída, fora do domínio) executa, no início de cada transação, `set_config('app.user_id', :id, true)` e `set_config('app.scope', :scope, true)`. Com `true` (equivalente a `SET LOCAL`), o valor **morre no fim da transação**, então uma conexão reutilizada pelo pool **não vaza** contexto para a próxima requisição.
- **Escopos:** `user` (requisição de usuário), `admin` (rotas `/admin`, nunca abre tabela de saúde), `system` (publicador, consumidor e tarefas de manutenção). **O worker que processa algo de um usuário roda com escopo `user` e o `user_id` vindo da mensagem**, e usa `system` só nas tabelas do `platform` (outbox, tarefa, processadas).
- **Falha fechada:** as políticas comparam com `current_setting('app.user_id', true)`; sem valor, a comparação é nula e nenhuma linha passa.
- Política padrão de uma tabela de dado de usuário: `USING (user_id = current_setting('app.user_id', true)::uuid)` e `WITH CHECK` igual; tabela de saúde não tem política para `admin`.

## 6. Ports e adaptadores

| Port | Adaptador real | Adaptador local ou falso |
|---|---|---|
| `IdentityProvider` | Cognito (adaptador esboçado; sem uso real na etapa) | Servidor OIDC de mentira (`mock-oauth2-server`) |
| `FileStorage` | S3 com URL pré-assinada | LocalStack (S3) |
| `Clock` | Relógio do sistema, fuso `America/Sao_Paulo` | Relógio fixo para teste |
| `TaskQueue` | SQS | ElasticMQ |
| `LlmClient`, `OcrClient` | (fora da etapa) | Falso que devolve resposta fixa |
| `PaymentGateway` | (fora da etapa) | Mock |
| `NotificationChannel` | (fora da etapa) | Falso que só registra |
| `AuditTrail` | Banco (`audit_event`) | Em memória nos testes unitários |

Cada port vive em `application/port/out`; adaptadores em `adapter/out/<tecnologia>`. O domínio não importa Spring, JPA, Jackson nem AWS (regra do ArchUnit).

## 7. API desta etapa (contrato em `/api/v1`)

| Método e rota | Perfil | Observação |
|---|---|---|
| `GET /api/v1/me` | autenticado | Cria o usuário no primeiro acesso |
| `GET /api/v1/tasks/{id}` | dono | Status da tarefa assíncrona |
| `GET /api/v1/admin/audit-events` | `ADMIN` | Paginação por cursor; só nomes e identificadores |
| `POST /api/v1/admin/dead-letters/{id}/reprocess` | `ADMIN` | Reprocessa mensagem da DLQ |
| `GET /actuator/health/liveness` e `/readiness` | público | Sem detalhes; o resto do actuator exige token |

Convenções em `docs/api-conventions.md`. O OpenAPI é gerado do código e versionado; o pipeline compara os dois (CA11).

## 8. Observabilidade, CI e infraestrutura

**Observabilidade (CA9).** OpenTelemetry com exportação OTLP; formato de log JSON com `traceId`, `userId` (UUID) e rota; **lista de atributos de span permitida** (rota em modelo, método, status, `user_id`); corpo de requisição **nunca** é logado; mensagens de erro não repetem valores. Um teste de integração envia valores sentinela e varre a saída de log e os spans exportados (exportador de teste próprio, sem rede).

**Pipeline de CI (GitHub Actions, CA1, CA2, CA19).**
- Build com JDK 25; `./mvnw verify` (unitários, integração com Testcontainers, ArchUnit, Modulith); **portão JaCoCo ≥ 85%** em `domain` e `application`.
- **Scan de dependências** com falha em Alta ou Crítica (suppressions só com justificativa e data de expiração), **scan de imagem** (Trivy) nas imagens que vão a produção, **SBOM CycloneDX** por build como artefato.
- **Dependabot** para atualização contínua (Maven, npm, GitHub Actions); `npm audit` para os pacotes TypeScript.
- **Proteção da `main`:** exige CI verde; secret scanning e push protection ativos.
- Imagem base com digest fixado e `maven-enforcer-plugin` (JDK e convergência de dependências).

**Infraestrutura (CA18, ADR-0002).** OpenTofu em `infra/` com módulos `network` (VPC sem NAT), `compute` (EC2 ARM, Docker Compose e Caddy), `database` (RDS PostgreSQL de zona única), `storage` (S3), `queues` (SQS, DLQ e alarmes de idade da fila e de DLQ não vazia), `secrets` (SSM). O ambiente de fumaça usa estado próprio e é criado e destruído por script. **Todo `apply` e todo recurso com custo pedem autorização antes** (CLAUDE.md, seção 10). O estado remoto (bucket S3 para o estado) é definido nesta etapa e exige a mesma autorização.

## 9. Dependências propostas (aprovadas ao aprovar este plano)

Versões vêm do BOM do Spring Boot; só releases GA estáveis, fixadas; versões conferidas na hora de gerar o `pom.xml` e o `package.json`.

**Backend, execução:** `spring-boot-starter-web`, `-validation`, `-actuator`, `-oauth2-resource-server`, `-jdbc` (SQL explícito nos adaptadores, sem JPA, para controlar RLS e outbox), `-flyway` com `flyway-database-postgresql`, driver `postgresql`, `spring-modulith-starter-core` e `-observability`, `spring-boot-starter-opentelemetry` (traz o ponte do Micrometer e o exportador OTLP), `springdoc-openapi`, `jackson-module-kotlin`, `kotlin-reflect`, AWS SDK v2 (`s3`, `sqs`).

**Backend, teste e build:** JUnit 5, MockK, AssertJ, Testcontainers (`postgresql`), ArchUnit, `mock-oauth2-server`, JaCoCo, `cyclonedx-maven-plugin`, `maven-enforcer-plugin`, scanner de dependências (OWASP dependency-check ou OSV-Scanner, a decidir na S1 com a medição de tempo e falsos positivos) e Trivy (na pipeline, fora do `pom`).

**App web:** React, Vite, TypeScript, React Router, `oidc-client-ts`, `openapi-typescript` e `openapi-fetch`, `react-i18next`, `vite-plugin-pwa`, Vitest, Testing Library e Playwright (um fluxo crítico).

**App mobile:** Expo e React Native (SDK 58, já provado na Etapa 0), `expo-auth-session` (login OIDC com PKCE), **`expo-secure-store`** (guardar a chave de criptografia local e os tokens no Keychain e no Keystore, que **resolve a pendência 3 do fechamento da Etapa 0**), `expo-sqlite` e `expo-build-properties` (já usados nos protótipos), Jest e Testing Library.

**Infra e ambiente local:** OpenTofu, imagens do `docker compose` fixadas por digest (PostgreSQL, ElasticMQ 1.7.1, LocalStack 4.14.0 só com S3, servidor OIDC de mentira, `grafana/otel-lgtm`).

## 10. Estratégia de testes

- **Unitários** (`*Test`): domínio e aplicação, com fakes dos ports; sem Spring nem banco.
- **Integração** (`*IT`): Testcontainers (PostgreSQL real, Flyway real) para CA4 a CA9, CA12 a CA16; o SQS usa o ElasticMQ em contêiner; JWT de teste pelo `mock-oauth2-server`.
- **Arquitetura:** ArchUnit (camadas, sem Spring no domínio, sem módulo `core` ou `shared`) e `Modulith.verify()`.
- **Isolamento:** o teste do catálogo do CA7 e os testes de CA6 com dois usuários.
- **Contrato:** comparação do OpenAPI gerado com o versionado (CA11).
- **Clientes:** Vitest e Jest nas casquinhas; um fluxo ponta a ponta no app web (login, `/admin` conforme o perfil).
- **Infraestrutura:** `tofu validate` e `plan` na pipeline; fumaça efêmero só com autorização.

## 11. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| JDK 25, Kotlin e Boot 4.1 incompatíveis ou instáveis | Conferir versões e compatibilidade na S1; recuar para o JDK 21 só com ADR |
| RLS vazar contexto entre requisições pelo pool de conexões | `set_config` local à transação; teste que roda duas requisições seguidas na mesma conexão |
| Esquecer RLS numa tabela nova | Teste do catálogo (CA7) quebra o pipeline |
| Outbox próprio mais trabalho que o registro do Modulith | Escopo pequeno (uma tabela, um publicador); teste de CA12 e CA13 cobre os erros clássicos |
| Dado de saúde escapando em log ou trace | Teste com valores sentinela (CA9) e lista permitida de atributos |
| Custo e conta AWS no ambiente de fumaça | Autorização e custo combinados antes; `destroy` obrigatório; alarme de orçamento |
| Fundação grande para um desenvolvedor só | Fatias pequenas, cada uma verde; S7 e S8 podem andar em paralelo |

## 12. Fora deste plano

Tudo o que a spec lista como fora de escopo. As specs das etapas seguintes (consentimento, anamnese, exames etc.) começam depois do gate da Etapa 1.

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026 (inclui a lista de dependências da seção 9, com o `expo-secure-store`)
