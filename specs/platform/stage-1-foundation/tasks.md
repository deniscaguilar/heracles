# Tarefas: Etapa 1, fundação técnica

- **Spec:** `spec.md` (Aprovada) • **Plano:** `plan.md` (Aprovada)
- Tarefas pequenas, testáveis e **test-first**. Ao começar uma tarefa, mover o cartão no board (CLAUDE.md, seção 5); ao concluir, marcar `[x]`, mover o cartão e registrar no `docs/progress-log.md`.
- Tarefas marcadas **(pede ok)** exigem aprovação do Dênis antes de executar (custo, configuração do repositório ou conta AWS).
- Cada fatia termina com `./mvnw verify` e o pipeline verdes.

## S1: Esqueleto e pipeline (RF1, RF2; CA1, CA2, CA3, CA19)

- [x] E1-S1.1 Criar a estrutura de pastas do CLAUDE.md (`backend/`, `mobile/`, `web/`, `packages/`, `infra/`, `docker/`) com um README curto em cada uma. *Pronto quando:* As pastas existem, o `README.md` raiz aponta para elas e nada de produto foi criado.
- [x] E1-S1.2 Gerar `backend/pom.xml` e o wrapper (`./mvnw`): Boot, Kotlin, Modulith, `maven-enforcer-plugin` (JDK 25 e convergência); conferir e registrar as versões. *Pronto quando:* `./mvnw verify` verde; o app sobe e `/actuator/health/liveness` responde 200 (teste `*IT`); versões registradas no `progress-log.md`.
- [x] E1-S1.3 Pacote `com.heracles.platform` com `api`, `domain`, `application`, `adapter/in`, `adapter/out` e `config`; regras ArchUnit (camadas, domínio sem Spring, JPA, Jackson e AWS, sem módulo `core`, `common` ou `shared`) e `Modulith.verify()`. *Pronto quando:* Os testes de arquitetura passam e um teste com violação proposital (classe de teste) prova que a regra falha.
- [x] E1-S1.4 Perfis `api`, `worker` e `all` (padrão `all`). *Pronto quando:* Teste de integração: no perfil `api` o worker não sobe e no `worker` a API não sobe.
- [x] E1-S1.5 `docker/compose.yml` com PostgreSQL, ElasticMQ 1.7.1, LocalStack 4.14.0 (só S3), servidor OIDC de mentira, `grafana/otel-lgtm` e o backend; imagens por digest. *Pronto quando:* `docker compose up` deixa tudo saudável; script `docker/check.sh` confirma cada serviço; README diz como subir (CA3).
- [x] E1-S1.6 `Dockerfile` do backend (multi-stage, imagem base com digest, usuário sem privilégio). *Pronto quando:* A imagem constrói e roda no compose; a mesma imagem serve ao perfil `api` e `worker`.
- [x] E1-S1.7 Workflow do GitHub Actions: build com JDK 25 e `./mvnw verify`, portão JaCoCo ≥ 85% em `domain` e `application`. *Pronto quando:* O workflow roda no PR; baixar a cobertura de propósito reprova o PR (CA1).
- [x] E1-S1.8 Scan de dependências (decidir OWASP dependency-check ou OSV-Scanner pela medição de tempo e falsos positivos) com falha em Alta ou Crítica e arquivo de supressão com justificativa e data de expiração. *Pronto quando:* Dependência vulnerável de propósito reprova o pipeline; a decisão fica registrada (CA1).
- [x] E1-S1.9 Trivy na imagem do backend e SBOM CycloneDX por build como artefato. *Pronto quando:* Imagem com vulnerabilidade Alta ou Crítica reprova; o SBOM aparece como artefato do build (CA1, CA2).
- [x] E1-S1.10 Dependabot (Maven, npm e GitHub Actions) e job de `npm audit` para os pacotes TypeScript. *Pronto quando:* O Dependabot abre PR de teste; `npm audit` roda no pipeline.
- [x] E1-S1.11 Proteção da `main` (exige CI verde) e secret scanning com push protection (pede ok: muda configuração do repositório). *Pronto quando:* Push com segredo de teste é bloqueado; merge sem CI verde é bloqueado (CA19).

## S2: Banco e isolamento por usuário (RF4; CA6, CA7)

- [x] E1-S2.1 Flyway por módulo (`db/migration/platform`, histórico próprio) e migração dos papéis `heracles_owner` e `heracles_app`. *Pronto quando:* Teste de integração: `heracles_app` não é superusuário, não tem `BYPASSRLS` e não faz DDL.
- [x] E1-S2.2 Port e adaptador do contexto de transação (`set_config('app.user_id', ..., true)` e `app.scope`). *Pronto quando:* Teste: duas requisições seguidas na mesma conexão não vazam contexto; sem contexto, zero linhas.
- [x] E1-S2.3 Tabelas só de teste `test_user_note` e `test_health_sample` com RLS forçada, e os testes de CA6. *Pronto quando:* Dois usuários só veem os seus; consulta sem filtro continua isolada; sem contexto não volta nada; escopo `admin` não lê a tabela de saúde.
- [x] E1-S2.4 Teste do catálogo do banco: toda tabela com `user_id` exige RLS ativa e forçada e um teste de isolamento registrado. *Pronto quando:* Criar uma tabela de teste sem RLS faz o teste falhar (CA7).

## S3: Identidade (RF3; CA4, CA5)

- [x] E1-S3.1 Port `IdentityProvider` e adaptador de teste; *resource server* que valida assinatura, emissor, público e expiração do JWT (emissor por variável de ambiente). *Pronto quando:* Testes: sem token, token expirado, assinatura inválida e outro emissor retornam 401 padronizado (CA4).
- [x] E1-S3.2 Migração `app_user` com RLS; casos de uso `ProvisionUser` e `GetMe`; `GET /api/v1/me`. *Pronto quando:* `sub` novo cria usuário `USER`; repetir devolve o mesmo; a resposta só tem identificador e perfil (CA5).

## S4: Auditoria e observabilidade (RF5, RF6; CA8, CA9)

- [x] E1-S4.1 Migração `audit_event` com triggers que recusam `UPDATE` e `DELETE`, e inserção por função do banco. *Pronto quando:* Teste de integração: alterar ou apagar um evento é recusado pelo banco (CA8).
- [x] E1-S4.2 Port `AuditTrail`, adaptador e caso de uso `RecordAudit` que recusa valores no lugar de nomes de campo. *Pronto quando:* Testes unitários e de integração; valor no lugar de nome é recusado (CA8).
- [x] E1-S4.3 `Authorizer` por perfil, `403` e auditoria com resultado `DENIED`. *Pronto quando:* `USER` em rota `ADMIN` recebe 403 e deixa evento `DENIED`.
- [x] E1-S4.4 `QueryAuditTrail` e `GET /api/v1/admin/audit-events` (`ADMIN`, cursor). *Pronto quando:* Só `ADMIN` lê; a resposta traz só nomes e identificadores.
- [x] E1-S4.5 OTLP, log em JSON com `traceId`, `userId` e rota, lista permitida de atributos de span; corpo de requisição nunca logado. *Pronto quando:* Teste de integração com valores sentinela (CPF, e-mail, texto de saúde): zero ocorrências em log, spans e erro (CA9).

## S5: Erros e contrato de API (RF7; CA10, CA11)

- [x] E1-S5.1 Tratador de erros no formato Problem Details com `code` estável e `traceId`. *Pronto quando:* Erro de validação devolve `application/problem+json` com `code` e o `traceId` do trace (CA10).
- [x] E1-S5.2 OpenAPI gerado pelo springdoc e versionado em `backend/openapi/`; teste que compara o gerado com o versionado. *Pronto quando:* Mudar um endpoint sem atualizar o arquivo reprova (CA11).
- [x] E1-S5.3 `packages/api-client` gerado com `openapi-typescript` e `openapi-fetch`. *Pronto quando:* O cliente é regenerado no pipeline e compila (CA11).

## S6: Fluxos assíncronos e ports (RF8, RF9; CA12 a CA17)

- [x] E1-S6.1 Migrações `outbox_message`, `task` e `processed_message` com RLS de escopo `system` (e `task` legível pelo dono). *Pronto quando:* Testes de isolamento passam; o teste do catálogo (S2.4) continua verde.
- [x] E1-S6.2 Port `TaskQueue` e adaptador SQS; teste de integração com ElasticMQ em contêiner. *Pronto quando:* Enviar, receber e confirmar mensagem funciona.
- [x] E1-S6.3 Gravar a mensagem no outbox na mesma transação da mudança de estado. *Pronto quando:* Transação desfeita não deixa mensagem (CA12).
- [x] E1-S6.4 `PublishOutbox` (lote com `FOR UPDATE SKIP LOCKED`, espera crescente nas falhas). *Pronto quando:* Com o publicador parado e depois ligado, a mensagem confirmada chega à fila (CA12).
- [x] E1-S6.5 `ConsumeTask` idempotente por `processed_message`. *Pronto quando:* A mesma mensagem duas vezes produz um efeito (CA13).
- [x] E1-S6.6 Nova tentativa com espera crescente, DLQ e tarefa `FAILED`. *Pronto quando:* Mensagem que sempre falha vai à DLQ após as tentativas (CA14).
- [x] E1-S6.7 `GetTaskStatus` e `GET /api/v1/tasks/{id}`. *Pronto quando:* O dono vê o status; outro usuário recebe 404; sem dado de saúde (CA15).
- [x] E1-S6.8 `ReprocessDeadLetter` e `POST /api/v1/admin/dead-letters/{id}/reprocess` (`ADMIN`). *Pronto quando:* Reprocessar devolve à fila e o efeito ocorre uma vez (CA14).
- [ ] E1-S6.9 Métricas de idade da fila e de profundidade da DLQ. *Pronto quando:* As métricas são emitidas e testadas (CA16).
- [ ] E1-S6.10 Ports `FileStorage` (S3 e LocalStack), `Clock`, `LlmClient`, `OcrClient`, `PaymentGateway` e `NotificationChannel` com adaptadores locais ou falsos. *Pronto quando:* Testes usam só os locais, sem rede externa; ArchUnit confirma que domínio e aplicação só dependem de ports (CA17).

## S7: Casca dos clientes (RF11; CA20)

- [ ] E1-S7.1 `web/` com Vite, React, TypeScript, i18n (chaves em inglês, pt-BR) e PWA instalável com service worker só para o shell, sem cache de resposta da API. *Pronto quando:* Testes Vitest; o service worker não guarda resposta da API.
- [ ] E1-S7.2 Login OIDC com PKCE contra o servidor de mentira; shell por perfil; rota `/admin` só para `ADMIN` e `MODERATOR`; chamada a `GET /api/v1/me` pelo cliente gerado. *Pronto quando:* Usuário comum não vê `/admin`; administrador vê (CA20).
- [ ] E1-S7.3 Teste Playwright do fluxo crítico (login, perfil, `/admin`). *Pronto quando:* O fluxo passa no pipeline.
- [ ] E1-S7.4 `mobile/` com Expo e React Native: login com `expo-auth-session`, tokens no `expo-secure-store`, shell e cliente gerado. *Pronto quando:* Teste Jest; login no emulador e no simulador contra o servidor de mentira (CA20).
- [ ] E1-S7.5 Chave de criptografia local por usuário guardada no `expo-secure-store` e abertura do banco com SQLCipher (resolve a pendência 3 do fechamento da Etapa 0). *Pronto quando:* Provado no emulador Android e no simulador iOS; sair da conta apaga a chave e o banco.

## S8: Infraestrutura (RF10; CA18)

- [ ] E1-S8.1 Esqueleto OpenTofu em `infra/`, backend de estado e `tofu validate` na pipeline (pede ok: estado remoto e conta AWS). *Pronto quando:* `tofu validate` verde no pipeline.
- [ ] E1-S8.2 Módulos `network` (sem NAT), `database`, `storage`, `queues` (com DLQ e alarmes), `secrets` e `compute` (EC2 ARM, Compose e Caddy). *Pronto quando:* `plan` mostra só os recursos de RF10 e foi revisado pelo Dênis (CA18).
- [ ] E1-S8.3 Script do ambiente de fumaça efêmero: `apply`, teste de fumaça e `destroy` (pede ok: gera custo). *Pronto quando:* O script existe e foi revisado; custo estimado combinado antes.
- [ ] E1-S8.4 Rodar o ambiente de fumaça e registrar o resultado (pede ok: gera custo). *Pronto quando:* Backend sobe, o teste passa e o `destroy` remove tudo (CA18).

## Fechamento

- [ ] E1-F.1 Conferir CA1 a CA20, escrever `docs/stage-1/fechamento.md` e atualizar o diário. *Pronto quando:* Cada critério marcado com a evidência; pendências listadas; gate da Etapa 1 decidido pelo Dênis.
