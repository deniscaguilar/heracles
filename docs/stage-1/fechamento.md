# Fechamento da Etapa 1 (fundação)

- **Tarefa:** E1-F.1 (`specs/platform/stage-1-foundation/tasks.md`)
- **Data:** 03/11/2026
- **Estado proposto:** **concluída com uma pendência: o ambiente de fumaça na AWS (E1-S8.4).** Desde a primeira versão deste documento, a pilha local viva (E1-V.1) e os módulos de filas, armazenamento e segredos num LocalStack (E1-V.2) também foram validados; ver a seção 2. Os 20 critérios têm evidência; só a metade do CA18 que cria recursos na AWS depende de autorização de custo e de uma imagem do backend.
- **Decisão do Dênis:** _aguardando._ A primeira revisão deste documento foi só da evidência; o gate (seguir para a Etapa 2 já ou esperar a S8.4) é decisão do Dênis.

## 1. Critérios de aceite

| CA | Estado | Evidência |
|---|---|---|
| **CA1** pipeline barra o que deve | **Atendido** | Job `backend` roda `./mvnw verify`: testes, ArchUnit (`ArchitectureTest`, com classes de exemplo que provam que cada regra reprova), Modulith (`ModulithStructureTest`) e portão JaCoCo de 85% de linhas e branches em `domain` e `application` ("All coverage checks have been met" no CI). OSV-Scanner sobre o SBOM (ADR-0009) e Trivy da imagem reprovam Alta e Crítica; exceções só em `.trivyignore.yaml`, com justificativa e validade. A `main` exige 9 checks. |
| **CA2** SBOM | **Atendido** | Artefato `sbom-cyclonedx` de cada build (`cyclonedx-maven-plugin`). |
| **CA3** ambiente local sobe e fica saudável | **Atendido** | `docker compose -f docker/compose.yml up -d --build --wait` e `docker/check.sh` (PostgreSQL, ElasticMQ, LocalStack S3, OIDC de mentira, Grafana LGTM, backend); instruções em `docker/README.md`. O job `e2e` do CI repete isso do zero numa máquina limpa a cada PR. |
| **CA4** 401 padronizado | **Atendido** | `AuthenticationIT`: sem token, expirado, outra chave, outro emissor, outra audiência, token malformado e outros esquemas. `ProblemDetailsIT`: os 401 e 403 também trazem `code` e `traceId`. |
| **CA5** `/me` cria o usuário como `USER` | **Atendido** | `MeEndpointIT`: sub novo vira `USER`, resposta só com id e perfil, repetir não duplica, usuário não muda o próprio perfil. |
| **CA6** isolamento por usuário (RLS) | **Atendido** | `RowLevelSecurityIT`: cada usuário só vê as suas linhas mesmo sem filtro, sem usuário nada volta, `admin` e `system` não leem dado de saúde, RLS forçada vale até para o dono. |
| **CA7** tabela nova exige RLS e teste de isolamento | **Atendido** | `DatabaseCatalogIT` (com `CatalogRules`): falha se uma tabela com `user_id` não tem RLS ativa e forçada, teste de isolamento registrado, ou dono correto; os casos de falha são exercitados. |
| **CA8** auditoria append-only | **Atendido** | `AuditEventIT`: `UPDATE` e `DELETE` recusados pelo banco para todos os papéis, inclusive dono e superusuário; `AuditTrailIT`: valor no lugar de nome de campo é recusado antes do banco. |
| **CA9** nada de dado de saúde na saída | **Atendido** | `ObservabilityIT`: valores sentinela em consulta, caminho, corpo e cabeçalhos nunca chegam a log, span ou erro; spans só com atributos permitidos. `ProblemDetailsIT`: erro sem eco do valor. |
| **CA10** Problem Details com `traceId` | **Atendido** | `ProblemDetailsIT`: `code` estável, nomes dos campos e `traceId` igual ao do trace. |
| **CA11** contrato OpenAPI e cliente | **Atendido** | `OpenApiContractIT` (contrato gerado igual ao versionado, só endpoints reais, nenhuma rota de teste); job `api-client` regenera o cliente, reprova se o arquivo versionado difere e compila. |
| **CA12** outbox | **Atendido** | `OutboxIT` (commit deixa estado e mensagem, rollback deixa nenhum dos dois, o banco recusa payload que não seja identificadores) e `PublishOutboxIT` (mensagens gravadas com o publicador parado chegam quando ele volta; dois publicadores nunca enviam a mesma mensagem). |
| **CA13** consumidor idempotente | **Atendido** | `ConsumeTaskIT`: a mesma mensagem duas vezes tem um efeito só; falha não deixa efeito nem marca; dois consumidores ao mesmo tempo, um efeito. |
| **CA14** falha, espera crescente, DLQ e reprocesso | **Atendido** | `RetryDeadLetterIT` (sempre falha: novas tentativas com espera, DLQ e tarefa `FAILED`; falha duas vezes e depois funciona: `DONE`), `ReprocessDeadLetterIT` e `ReprocessDeadLetterEndpointIT` (volta à fila, efeito uma vez), `BackoffTest`. |
| **CA15** status da tarefa | **Atendido** | `TaskStatusEndpointIT`: o dono vê só o status; outro usuário, tarefa de sistema e id desconhecido dão o mesmo 404; sem token 401. |
| **CA16** métricas e alarmes de fila | **Parcial** | Métrica: `QueueMetricsIT` (fila, DLQ, idade do outbox e da tarefa, com ElasticMQ e Postgres reais). Alarmes: definidos no OpenTofu (idade da mensagem mais antiga e DLQ não vazia) e exigidos por `check_plan.py`. **Não provado:** o alarme disparando de verdade (só existe na AWS; a S8.4 confere que ele existe, não que dispara). |
| **CA17** adaptador local ou falso por porta | **Atendido** | `PortsAndFakesTest`, `LocalAdaptersIT` (com a chave há um falso por porta, sem ela nenhum), `S3FileStorageIT` (LocalStack), `SqsTaskQueueIT` (ElasticMQ); regra ArchUnit de que `application` só depende de portas. |
| **CA18** infraestrutura | **Parcial** | `plan`: **atendido**. 47 recursos, todos dentro de RF10, conferido offline no CI (`check-plan.sh`, que reprova NAT, porta 22 etc., provado) e com o `plan` real na conta (47 a criar, 0 a mudar, 0 a destruir). Ambiente de fumaça: script `smoke.sh` escrito e com as travas testadas; **não executado** (E1-S8.4). |
| **CA19** segredo não entra | **Atendido** | Push protection e secret scanning ativos; provado na S1 (push de uma chave falsa recusado). Segredos só em SSM no OpenTofu, nada em imagem nem em `.env` versionado. |
| **CA20** casca dos clientes | **Atendido** | Web: `App.test` e testes de autenticação (Vitest) e `critical-flow.spec.ts` (Playwright contra a pilha real, 3 cenários, no CI); mobile: 37 testes Jest e login verificado no emulador Android e no simulador iOS (contra a pilha local), com o banco local cifrado (S7.5). |

## 2. Validação local antes da AWS (E1-V.1 e E1-V.2, pedido do Dênis)

- **Pilha viva** (`docker/validate.sh`): 30 verificações passam e reprovam quando a regra é quebrada (gatilho da auditoria desligado: CA8 falha). Dão evidência, na pilha de verdade e não só em teste de integração, de CA3 a CA6, CA8 a CA10 e CA12 a CA16: login com token real, `/me`, 400 com `traceId` que existe no Tempo, 403 em `/admin`, auditoria que o banco não deixa alterar, valor sentinela fora da resposta, do trace e do log, outbox com rollback e commit, 5 tentativas com espera crescente até a DLQ, tarefa `FAILED`, métricas no Prometheus e reprocessamento administrativo.
- **Módulos no LocalStack** (`infra/scripts/localstack.sh`): 20 verificações dos módulos `queues`, `storage` e `secrets` (24 com a aplicação real consumindo as filas criadas pelo OpenTofu). Reforça CA14 e CA16 (redrive e alarmes definidos nos módulos) e o desenho de upload da ADR-0002; reprova quando o módulo é alterado (provado).
- **Não coberto por nenhum dos dois, por falta de entrada na pilha ou de serviço no emulador gratuito:** efeito idempotente do consumidor (CA13, segue nos testes de integração), upload ao S3 pela API (não há endpoint), disparo real dos alarmes, e rede, banco e instância (só o ambiente de fumaça, E1-S8.4).

## 3. Números

- Backend: 74 testes unitários e 108 de integração, 0 falhas (CI da `main`, 03/11/2026).
- Web: 23 testes Vitest e 3 cenários Playwright. Mobile: 37 testes Jest. `api-client`: testes e geração conferida no CI.
- `main`: 9 verificações obrigatórias (`backend`, OSV, Trivy, `npm audit`, `api-client`, `web`, `e2e`, `mobile`, `infra`).
- Infra: 47 recursos, provedores `aws` 6.71.0 e `random` 3.8.1 fixos, `trivy config` com 2 supressões com validade em 03/05/2027.

## 4. Pendências

**Bloqueiam o fechamento total (CA18):**
1. **E1-S8.4**: rodar o ambiente de fumaça. Precisa do OK de custo do Dênis (estimativa: menos de US$ 0,30 por rodada de 2 horas), da conta autorizada e de uma **imagem do backend arm64 por digest** que a instância consiga baixar (o CI não publica imagem: GHCR pelo CI ou ECR).

**Não bloqueiam a Etapa 2, mas ficam registradas:**
2. Alarmes: o disparo real só se vê na AWS; `alarm_email` ainda não definido (sem ele o tópico SNS existe, mas ninguém recebe). O SNS foi aprovado na revisão da S8.2.
3. Credenciais da máquina são de um usuário administrador de longa duração; para o `apply`, preferir um papel de menor poder.
4. Provedor de identidade real (Cognito, ADR-0008): ainda só o servidor de mentira. O token de acesso do Cognito traz `client_id` e não `aud`, então o adaptador real precisará ajustar o validador de audiência.
5. Clientes: sair só apaga os tokens locais (não chama `end_session`); sem renovação de token; identificador do app mobile (`com.heracles.mobile`) e ícones são provisórios; o banco local só tem a tabela `meta` (o esquema e a sincronização vêm nas etapas de dado).
6. Dependências de ferramenta com achados baixos e moderados no `npm audit` (Expo CLI, Jest, workbox-build, openapi-typescript): acompanhar pelo Dependabot; 0 Alta ou Crítica.
7. O pacote raiz (`com.heracles`) e o `groupId` Maven continuam provisórios (CLAUDE.md).
8. Pendências da Etapa 0 que não eram desta fatia continuam em `docs/stage-0/fechamento.md` (provedor de IA e OCR, validação científica dos casos de troca, jurídico).

## 5. Próximo passo

A S8.4 (ambiente de fumaça na AWS), quando o Dênis autorizar o custo e escolher de onde sai a imagem do backend, e a decisão do gate. A validação local (V.1 e V.2) já está feita.
