# Plano: Etapa 2, cadastro, consentimento e anamnese

- **Spec:** `spec.md` (Aprovada em 03/11/2026)
- **Status:** Aprovada (03/11/2026)
- **Módulos:** `consent` e `profile` (novos); `platform` (fachada `api`, direitos do titular); `web/` e `mobile/`
- **Aprovado o plano, estão aprovados também a lista de dependências da seção 9 e os dois pontos de segurança da seção 11 que alteram a fundação** (CLAUDE.md, seção 10). Dependência ou mudança de segurança fora da lista pede nova aprovação.

## 1. Visão da solução

Cinco ideias sustentam a etapa:

1. **Consentimento é evento, não estado.** Aceite e revogação são linhas append-only em `consent.consent_event`; o estado vigente é derivado do último evento de cada tipo. Isso dá a prova ("quem aceitou qual versão, quando") sem esforço extra e torna a revogação trivial e imediata.
2. **A barreira está antes do banco.** Um interceptador do `consent` recusa com `403 consent_required` qualquer rota marcada `@RequiresConsent(HEALTH_DATA)` **antes** do controlador. Um teste enumera todas as rotas de saúde e falha se alguma não estiver marcada, então uma rota nova esquecida não passa no CI.
3. **Cada módulo tem seu schema, sua pasta de migrações e seu histórico do Flyway.** Os módulos novos só falam com o `platform` pela `platform.api` (que passa a existir) e entre si por `consent.api` e `profile.api`. Sem FK entre schemas: só identificadores.
4. **Direitos do titular são tarefas assíncronas que cada módulo ajuda a cumprir.** O `platform` orquestra (fila, status, arquivo, identidade); `consent` e `profile` registram o que sabem exportar e como apagar. Módulos futuros fazem o mesmo, sem mexer no orquestrador.
5. **Apagar é desvincular, não destruir a prova.** A anamnese e o perfil são apagados; a prova do aceite e a trilha de auditoria ficam, **sem ligação com a pessoa** (o identificador é trocado por um código aleatório que não volta). A exceção ao "append-only" é estreita: uma função do dono do banco que só troca o `user_id` (seção 11, ponto 1).

## 2. Fatias de entrega

Cada fatia fecha com testes verdes, contrato OpenAPI atualizado e entrada no diário. As fatias 1 a 9 são backend; 10 e 11, clientes; 12, infraestrutura e fechamento.

| # | Fatia | Cobre |
|---|---|---|
| S1 | Fachada `platform.api`; Flyway por módulo; esqueletos de `consent` e `profile` com ArchUnit e Modulith verdes | base de tudo |
| S2 | `consent`: banco (`policy_version` imutável, `consent_event` append-only, visão do estado, agregados) e testes de banco | RF1, RF2; CA2, CA5, CA6 |
| S3 | `consent`: casos de uso, API do cliente, `ConsentChecker` e a barreira `@RequiresConsent` | RF2 a RF5; CA1, CA3, CA4 |
| S4 | `consent`: backoffice (rascunho, pré-visualização, publicação, contagens) e conversão do texto em blocos | RF1, RF11; CA7 |
| S5 | `ConsentAccepted`/`Revoked` pelo outbox, confirmação idempotente e auditoria | RF6, RF7; CA8, CA10 |
| S6 | `profile`: catálogos, anamnese com revisões, validação | RF8; CA6, CA9, CA11 |
| S7 | `profile`: triagem e `profile.api` de elegibilidade | RF10; CA12 |
| S8 | Direitos do titular: exportação e exclusão (7 dias), `ExportContributor` e `ErasureStep`, pseudonimização | RF9; CA13, CA14 |
| S9 | Roteiro de validação local e contrato OpenAPI fechados (`docker/validate.sh` ganha os fluxos novos) | CA16 e evidência |
| S10 | Web: gate de aceite, anamnese, "Meus consentimentos", privacidade (exportar e excluir), `/admin/policies`, teste Playwright | RF11; CA15 |
| S11 | Mobile: as mesmas telas (sem biblioteca de navegação), verificação no emulador e no simulador | RF11; CA15 |
| S12 | Regra de ciclo de vida do prefixo `exports/` no OpenTofu e fechamento da etapa (`docs/stage-2/fechamento.md`) | RF9; fechamento |

## 3. Casos de uso (camada `application`)

**`platform` (acréscimos):** `RequestExport`, `GetExportLink`, `RunExport` (tarefa), `RequestErasure`, `CancelErasure`, `RunDueErasures` (agendado), `RunErasure` (tarefa).

**`consent`:**

| Caso de uso | Resumo |
|---|---|
| `ListMyConsents` | Para cada um dos 7 tipos: obrigatório ou não, versão vigente, versão aceita, estado e se está pendente |
| `GetCurrentPolicy` | Versão vigente de um tipo, em blocos para o cliente desenhar |
| `AcceptRequiredConsents` | Aceita os três obrigatórios de uma vez; recusa `409 policy_version_outdated` se alguma versão já mudou |
| `SetOptionalConsent` | Liga ou revoga um opcional na versão vigente |
| `CheckConsent` (`ConsentChecker`) | "O usuário consentiu em X na versão vigente?"; falha fechada |
| `CreateDraft`, `UpdateDraft`, `DiscardDraft`, `PreviewVersion`, `PublishVersion` | Backoffice; só `ADMIN` |
| `ListPolicies` | Tipos, versões e **contagens agregadas** de quem está em cada versão |
| `ConfirmConsentChange` | Consumidor de `ConsentAccepted`/`Revoked`: confirmação pelo `NotificationChannel` |
| `ExportConsentHistory`, `EraseConsentData` | Contribuição do `consent` aos direitos do titular |

**`profile`:**

| Caso de uso | Resumo |
|---|---|
| `GetCatalog` | Itens de um catálogo (alergia, equipamento etc.) |
| `SaveAnamnesis` | Valida, cria uma nova revisão, roda a triagem e devolve a elegibilidade |
| `GetMyAnamnesis`, `ListRevisions`, `GetRevision` | Leitura do próprio dado |
| `EvaluateEligibility` | Aplica as regras provisórias de triagem a uma anamnese |
| `GetEligibility` (`profile.api`) | Para `training` e `nutrition` perguntarem se o usuário pode ter plano automático |
| `ExportAnamnesis`, `EraseAnamnesis` | Contribuição do `profile` aos direitos do titular |

Os casos de uso falam só com ports; sem Spring, JPA, Jackson nem AWS no `domain` e no `application` (regra do ArchUnit da fundação).

## 4. Modelo de dados

Cada módulo cria o seu schema, de dono `heracles_owner`, com os mesmos privilégios padrão do `platform` (a aplicação só faz DML nas tabelas, nunca DDL). Nomes em inglês, `snake_case`. Sem FK entre schemas.

### 4.1 Schema `consent`

| Tabela | Colunas principais | Isolamento e regras |
|---|---|---|
| `policy_version` | `id`, `type` (7 valores), `version` (inteiro, único por tipo), `title`, `markdown` (até 60.000 caracteres), `created_by`, `created_at`, `published_by`, `published_at` (nulo = rascunho) | Sem `user_id` (não é dado de usuário). Índice parcial: **um rascunho por tipo**. Gatilho: versão com `published_at` preenchido **não aceita `UPDATE` nem `DELETE`** (CA2); rascunho pode ser editado e descartado. Leitura por qualquer autenticado; escrita só pelo escopo `admin`, por função |
| `consent_event` | `id`, `user_id`, `type`, `action` (`GRANT`/`REVOKE`), `policy_version_id`, `client` (`WEB`/`MOBILE`), `occurred_at` (preenchido pelo banco) | **`user_id` com RLS forçada**, política padrão `user_id = app.user_id` com `USING` e `WITH CHECK`. Gatilho append-only para todos os papéis, com **uma única exceção**: a função `consent.pseudonymize_user` (seção 11). Registrada no teste de catálogo (CA7 da fundação) |
| `current_consent` (visão) | último evento de cada `(user_id, type)` | `security_invoker`, então a RLS vale na leitura |

Funções `SECURITY DEFINER` do dono: `create_draft`, `update_draft`, `discard_draft`, `publish_version` (promove o rascunho, número seguinte da sequência do tipo), `version_counts` (para cada tipo e versão, **quantas pessoas** têm aquela versão como último aceite; nenhuma identidade sai), `pseudonymize_user`. O papel da aplicação só chama funções; não escreve em `policy_version`.

### 4.2 Schema `profile`

| Tabela | Colunas principais | Isolamento e regras |
|---|---|---|
| `anamnesis_revision` | `id`, `user_id`, `revision` (inteiro, único por usuário), `created_at`, `objective`, `training_level`, `birth_date`, `biological_sex` (nulo = prefiro não informar), `screening` (jsonb: respostas da triagem), `eligible` (boolean), `ineligibility_reasons` (códigos), `triage_rules_version` | **Dado de saúde**: RLS forçada, só o dono lê; o escopo `admin` e o `system` não leem. Append-only (gatilho recusa `UPDATE`); `DELETE` só pela função de exclusão |
| `anamnesis_item` | `id`, `revision_id`, `user_id`, `kind` (`ALLERGY`, `INTOLERANCE`, `RESTRICTION`, `FOOD_PREFERENCE`, `DISLIKED_FOOD`, `EQUIPMENT`, `INJURY`), `catalog_code` ou `other_text` (curto), `body_region`, `note` (curta) | Mesma regra de saúde (tem `user_id`) |
| `catalog_item` | `kind`, `code`, `sort_order` | Dados de partida em migração; sem `user_id`; leitura por qualquer autenticado. O rótulo em português vem do i18n dos clientes pela chave `code` |

A triagem lê as regras de `triage-rules.json` (versionado no repositório, com número de versão gravado na revisão, para o resultado ser reprodutível quando o nutricionista corrigir a lista).

### 4.3 Schema `platform` (acréscimos)

| Objeto | Para quê |
|---|---|
| `account_erasure` (`user_id`, `requested_at`, `due_at`, `status` `PENDING`/`CANCELED`/`DONE`) | Pedido de exclusão com os 7 dias de arrependimento. Isolada por usuário; o agendador (escopo `system`) lê os vencidos |
| `data_export` (`task_id`, `user_id`, `storage_key`, `created_at`, `expires_at`) | Referência do arquivo de exportação (24 h). Isolada por usuário |
| `platform.pseudonymize_user(old, new)` | Troca o `user_id` em `audit_event` (seção 11) |
| `app_user.deleted_at` | O usuário apagado vira linha sem `sub` e sem perfil, para o `sub` antigo poder criar uma conta nova e vazia (CA14) |

## 5. Migrações, papéis e RLS

- **Flyway por módulo:** três instâncias em `config` (uma por módulo), cada uma com `schemas`, `locations` (`db/migration/<módulo>`) e **tabela de histórico no próprio schema**. Ordem de execução fixa: `platform`, `consent`, `profile`. As versões numéricas são independentes por módulo.
- **Cada módulo novo** começa com uma migração que cria o schema (dono `heracles_owner`), concede `USAGE` ao `heracles_app` e repete os `DEFAULT PRIVILEGES` do `platform` (V1), sem tocar nos papéis, que já existem.
- **Contexto de transação:** continua `AccessContext` por transação. Casos de uso do cliente rodam em escopo `user`; as funções do backoffice, em `admin`; o agendador de exclusões e os consumidores sem usuário, em `system`. O `profile` e o `consent` pedem o contexto pela `platform.api` (seção 6).
- **Teste de catálogo (CA7 da fundação):** `consent_event`, `anamnesis_revision`, `anamnesis_item`, `account_erasure` e `data_export` entram na lista de tabelas com teste de isolamento registrado; o teste falha se faltar RLS forçada.

## 6. Portas, fachada e adaptadores

**Fachada `platform.api`** (nova; é o **único** acesso dos outros módulos ao `platform`, regra 2 do CLAUDE.md). Só interfaces e tipos simples:

| Item | Função |
|---|---|
| `CurrentUser` | Identificador e perfil de quem chama; resolvido por um *argument resolver* do `platform`, então controladores de outros módulos recebem `CurrentUser` sem conhecer a autenticação |
| `Transactions` | Roda um bloco em escopo `user`, `admin` ou `system` |
| `AuditLog` | Registra auditoria (só nomes de campo e identificadores) |
| `TaskGateway` | Cria a tarefa (status `PENDING`) e anexa a mensagem ao outbox na mesma transação |
| `TaskHandlerRegistration` | Passa de `application` para `api`: o módulo registra o tratador do seu tipo de mensagem |
| `Notifier` | Envia um modelo pelo `NotificationChannel` (adaptador falso/local) |
| `ExportContributor` e `ErasureStep` | Cada módulo registra o que exporta e como apaga; o `platform` os chama em ordem |
| `ClockProvider` | O `Clock` do port (fuso `America/Sao_Paulo`) |

**Fachadas dos novos módulos:** `consent.api` (`ConsentChecker`, `RequiresConsent`, `ConsentType`, eventos `ConsentAccepted` e `ConsentRevoked` só com identificadores) e `profile.api` (`EligibilityQuery`).

**Ports que mudam:** `IdentityProvider` ganha `deleteUser(subject)` (adaptador do JWT: desvincula localmente; adaptador falso registra a chamada; o Cognito real fica para a tarefa própria, spec pergunta 1). `FileStorage` já cobre gravar, URL pré-assinada e apagar.

**Adaptadores:** `Jdbc*` de cada módulo no `adapter/out` do próprio módulo; interceptador `@RequiresConsent` e controladores em `adapter/in/web`; agendadores no `adapter/in/scheduler`.

> **Ajustes feitos na implementação (03/11/2026), sem mudar o que foi aprovado:** a `platform.api` ganhou `CurrentUserProvider` (o interceptador da barreira precisa saber quem chama) e `EventPublisher` (publicar no outbox sem criar tarefa); o `TaskHandlerRegistration` **não foi movido**: a `api` tem o seu e o `platform` o adapta ao tipo interno; o `Notifier` falha na chamada, e não na subida, quando não há canal (só existe com adaptador real ou os falsos ligados); a configuração web do `consent` ficou separada da dos casos de uso para evitar ciclo de beans; versão fixada do `commonmark`: 0.32.0.

> **Ajustes feitos na implementação da S8 (04/11/2026), sem mudar o que foi aprovado:** (1) `profile` ganhou a migração `V4__erasure.sql` (a anamnese continua append-only; o `DELETE` passa só para `heracles_owner` com um sinalizador local à transação que a função `profile.erase_user` levanta), do mesmo tipo da exceção aprovada para a auditoria; (2) `ErasureStep.erase` roda no escopo `system` e cada módulo gera o seu próprio código aleatório na pseudonimização (sem tabela de correspondência, e não precisa ser o mesmo entre módulos); (3) `account_erasure` ganhou o status `RUNNING` (tarefa já enfileirada, não se cancela mais) e a tarefa de exclusão é de sistema (`subjectId` na mensagem), para a RLS do usuário não esconder as linhas no meio; (4) a identidade é removida no provedor **antes** de esvaziar o usuário, para uma nova tentativa ainda saber o `sub`; (5) códigos novos: `export_expired` (410) e `task_not_found` (404) na leitura da exportação; (6) o arquivo de exportação também é apagado na exclusão (ordem 30), e `platform.my_audit_events()` devolve ao titular só as linhas de auditoria que o citam.

## 7. API desta etapa (contrato em `/api/v1`)

Erros no formato Problem Details com `code` estável e `traceId` (`docs/api-conventions.md`). Códigos novos: `consent_required` (403), `policy_version_outdated` (409), `policy_not_found` (404), `draft_already_exists` (409), `anamnesis_not_found` (404), `anamnesis_invalid` (400, com os **nomes** dos campos, nunca os valores), `erasure_not_found` (404), `export_not_ready` (409).

| Rota | Quem | Efeito |
|---|---|---|
| `GET /me/consents` | usuário | Estado dos 7 consentimentos e pendências |
| `GET /policies/{type}/current` | autenticado | Versão vigente em blocos |
| `POST /me/required-consents` | usuário | Aceita os 3 obrigatórios nas versões vigentes |
| `PUT /me/consents/{type}` | usuário | Liga ou revoga um opcional (`{granted, versionId, client}`) |
| `GET /catalogs/{kind}` | autenticado | Itens do catálogo |
| `GET /me/anamnesis`, `PUT /me/anamnesis` | usuário + `HEALTH_DATA` | Última revisão; nova revisão com elegibilidade |
| `GET /me/anamnesis/revisions`, `GET /me/anamnesis/revisions/{n}` | usuário + `HEALTH_DATA` | Histórico |
| `POST /me/exports`, `GET /me/exports/{taskId}` | usuário | Pede a exportação (202 com `taskId`); devolve URL de 15 minutos enquanto o arquivo existir |
| `POST /me/erasure-requests`, `GET /me/erasure-requests/current`, `DELETE /me/erasure-requests/current` | usuário | Pede, consulta e cancela a exclusão |
| `GET /admin/policies` | `ADMIN` | Tipos, versões e contagens |
| `POST /admin/policies/{type}/draft`, `PUT /admin/policy-drafts/{id}`, `DELETE /admin/policy-drafts/{id}` | `ADMIN` | Rascunho |
| `GET /admin/policy-versions/{id}/preview`, `POST /admin/policy-drafts/{id}/publish` | `ADMIN` | Pré-visualiza e publica |

`GET /me/consents`, `GET /policies/...`, os pedidos de exportação e exclusão e os catálogos **não** exigem `HEALTH_DATA` (a pessoa precisa deles justamente para chegar lá).

**Texto da política:** o administrador escreve **Markdown restrito**. O backend o converte, na gravação e na leitura, em **blocos estruturados** (parágrafo, título, lista, e dentro deles texto, ênfase e link `https` ou `mailto`); HTML bruto, imagem, script e qualquer outro nó são **recusados** na gravação. Os dois clientes desenham os blocos com seus próprios componentes, sem biblioteca de Markdown e sem `innerHTML`.

## 8. Observabilidade, auditoria e CI

- **Auditoria** (nomes de campo e identificadores, nunca valor): `consent.accepted`, `consent.revoked`, `policy.draft_created`, `policy.published`, `anamnesis.saved`, `eligibility.changed`, `export.requested`, `export.completed`, `erasure.requested`, `erasure.canceled`, `erasure.completed`.
- **Sem dado de saúde na saída:** o teste de valores sentinela da fundação é estendido aos endpoints novos (anamnese com alergia, lesão e observação sentinelas, com sucesso e com erro). A mensagem de fila leva só `userId`, `eventId`, `type` e `policyVersionId`.
- **Métricas** de baixa cardinalidade: aceites e revogações por tipo, exportações e exclusões por resultado.
- **CI:** nada novo no desenho; os jobs existentes cobrem. O teste de catálogo, o ArchUnit e o Modulith passam a ver três módulos. `docker/validate.sh` ganha os fluxos novos (S9).

## 9. Dependências propostas (aprovadas ao aprovar este plano)

Uma única dependência nova de produção, escolhida para **não** puxar biblioteca de Markdown para os clientes:

| Onde | Dependência | Por quê | Versão |
|---|---|---|---|
| Backend | `org.commonmark:commonmark` | Ler o Markdown restrito como árvore, para recusar HTML, imagem e nós não permitidos e gerar os blocos. Sem dependências transitivas, mantida, licença BSD-2 | Última GA estável, **conferida e fixada ao gerar o `pom.xml`** (fora do BOM do Spring Boot) |

Nada novo no web e no mobile: formulários com React puro, o roteamento do web já existe (`react-router`) e o mobile usa uma troca de telas por estado, **sem biblioteca de navegação** (escolher uma biblioteca fica para a Etapa 5, junto do banco local). Triagem e catálogos usam o Jackson que já está no Spring Boot.

## 10. Estratégia de testes

Test-first, e cada regra com a prova de que o teste reprova sem ela, como na fundação.

- **Unitários** (`domain` e `application`, sem Spring nem banco): estado derivado do consentimento (último evento), pendência por versão, regras da triagem (cada motivo isolado e combinado), validação da anamnese, conversão do texto em blocos (recusas de HTML, imagem e esquema `javascript:`), prazo de 7 dias com o `Clock` falso. Cobertura ≥ 85% mantida.
- **Integração** (Testcontainers, Flyway real): imutabilidade da versão publicada (CA2), `consent_event` append-only inclusive para o dono e o superusuário (CA5), a exceção estreita da pseudonimização (só `user_id`, só pela função), isolamento entre usuários e escopo `admin` sem acesso à anamnese (CA6), barreira `consent_required` em todas as rotas de saúde por enumeração (CA1), reaceite por versão nova (CA3), opcionais e revogação imediata (CA4), backoffice e papéis (CA7), auditoria sem valores (CA8), sentinelas (CA9), confirmação idempotente com a mesma mensagem duas vezes (CA10), revisões e validações (CA11), triagem e reversão (CA12), exportação com URL que expira (CA13), exclusão idempotente que deixa a pessoa recriável (CA14).
- **Arquitetura:** ArchUnit e Modulith com `platform`, `consent` e `profile`; `consent` e `profile` só usam `platform.api`; `profile` não usa `consent` a não ser por `consent.api`.
- **Contrato:** OpenAPI regenerado e comparado; cliente TypeScript regenerado e compilado (CA16).
- **Clientes:** Vitest/Jest para as telas e o gate; Playwright no web para o fluxo completo (CA15): usuário novo → aceite → anamnese → "Meus consentimentos" → admin publica versão nova → o usuário é obrigado a aceitar de novo. Mobile verificado no emulador Android e no simulador iOS.
- **Local:** `docker/validate.sh` estendido (S9): barreira, aceite, revogação, anamnese, triagem, exportação e exclusão contra a pilha viva.

## 11. Riscos e mitigação

Os dois primeiros itens **alteram a segurança da fundação** e pedem a sua aprovação explícita nesta revisão.

1. **Pseudonimização quebra o "append-only" da auditoria (mudança na ADR-0003).** A exclusão precisa desvincular a pessoa da trilha e da prova do aceite, mas os gatilhos recusam todo `UPDATE`. **Proposta:** uma migração nova (a V4 aplicada não se altera) substitui a função do gatilho de `audit_event` e a de `consent_event` por uma que continua recusando tudo, **exceto** o `UPDATE` que muda apenas `user_id` (e `actor_user_id`/`entity_id` na auditoria) quando executado por `current_user = 'heracles_owner'`, o que só acontece dentro das funções `SECURITY DEFINER` `pseudonymize_user`. A aplicação não consegue trocar de papel; o superusuário continua recusado (o roteiro local e os testes provam). O novo identificador é um UUID aleatório sem tabela de correspondência.
2. **Cada módulo novo ganha um schema e um histórico do Flyway próprios.** O `platform` hoje configura um Flyway só; passar a três é mudança de configuração do banco. **Proposta:** como na seção 5, com a ordem fixa e testes de integração que sobem o banco do zero e de uma versão anterior.
3. **Rota de saúde sem a barreira.** Mitigação: `@RequiresConsent` por anotação e um teste que enumera todos os mapeamentos de `profile` (e, depois, de `exams`, `training`, `nutrition`) e falha se algum não tiver a anotação; fail closed no interceptador se a verificação falhar.
4. **Texto livre vaza dado de saúde.** Mitigação: limites curtos, tratados como saúde em todo lugar, teste de sentinelas, nunca em mensagem de fila nem em auditoria.
5. **Triagem errada deixa passar alguém que não devia.** Mitigação: regra conservadora (na dúvida, inelegível), versionada e gravada na revisão; a lista é configuração, não código, e o nutricionista a corrige depois. Enquanto os textos e a triagem forem provisórios, o produto segue restrito ao Dênis (spec, RF12).
6. **Exportação mistura dados de outra pessoa.** Mitigação: o arquivo é montado em transação de escopo `user` (a RLS decide o que entra), o teste de CA13 usa dois usuários.
7. **Exclusão em andamento perde o `sub` ou cria conta com dados velhos.** Mitigação: passos idempotentes, `deleted_at` no usuário e teste que reexecuta a tarefa.
8. **Prazo de 7 dias com fuso e relógio.** Mitigação: tudo pelo `Clock` do port, em `America/Sao_Paulo`, testado com relógio fixo.
9. **Mobile sem biblioteca de navegação cresce mal.** Aceito: 5 telas; a decisão fica para a Etapa 5.

## 12. Fora deste plano

Cognito real e login social (tarefa própria antes do primeiro usuário externo); envio real de notificação; edição de catálogos e de tipos de exame pelo backoffice; exames, IA e planos; anamnese no banco local do mobile; marca de revisão jurídica e aviso de texto provisório (decisão do Dênis, spec pergunta 8); parecer jurídico, RIPD e DPO.

## Aprovação

- [x] Aprovado por: Dênis, via Plannotator (Approve), em 03/11/2026, incluindo a lista de dependências (seção 9) e os dois pontos de segurança da seção 11
