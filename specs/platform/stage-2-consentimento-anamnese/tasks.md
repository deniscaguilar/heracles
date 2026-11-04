# Tarefas: Etapa 2, cadastro, consentimento e anamnese

- **Spec:** `spec.md` (Aprovada em 03/11/2026). **Plano:** `plan.md` (Aprovado em 03/11/2026).
- Cada tarefa é pequena e testável, **test-first**; o contrato OpenAPI é atualizado junto. O board "Heracles · Tarefas" (Notion) espelha este arquivo; em conflito, este vence.

## S1: Fachada e base (base do plano)

- [x] E2-S1.1 `platform.api`: `CurrentUser` (com *argument resolver*), `Transactions`, `AuditLog` e `ClockProvider`. *Pronto quando:* Teste de arquitetura verde e um controlador de teste de outro módulo recebe `CurrentUser` sem importar a autenticação do `platform`.
- [x] E2-S1.2 Mover `TaskHandlerRegistration` para `platform.api` e criar `TaskGateway` (tarefa `PENDING` e mensagem no outbox na mesma transação) e `Notifier`. *Pronto quando:* Os testes de fila da fundação seguem verdes e o `TaskGateway` tem teste de commit e de rollback.
- [x] E2-S1.3 Flyway por módulo (três instâncias, histórico no schema de cada um, ordem `platform`, `consent`, `profile`) e a migração inicial dos schemas `consent` e `profile` (dono `heracles_owner`, privilégios padrão). *Segurança aprovada no plano, seção 11.* *Pronto quando:* O banco sobe do zero e a partir da versão anterior; o teste de catálogo confirma dono e privilégios dos dois schemas.
- [x] E2-S1.4 (classes de exemplo em `fixture/modules`: `consent` e `profile` usando `platform.application` reprovam, `platform.api` passa) Pacotes `consent` e `profile` com README e as regras de ArchUnit e Modulith valendo para os três módulos. *Pronto quando:* Verde, com classe de exemplo que prova que `consent` não pode usar `platform.application`.

## S2: `consent`: banco (RF1, RF2; CA2, CA5, CA6)

- [x] E2-S2.1 `consent.policy_version`: tabela, um rascunho por tipo, gatilho de imutabilidade da versão publicada e funções `create_draft`, `update_draft`, `discard_draft`, `publish_version`. *Pronto quando:* Teste de integração: versão publicada recusa `UPDATE` e `DELETE` pela aplicação e pelo dono; rascunho edita e descarta; a numeração é por tipo (CA2).
- [x] E2-S2.2 `consent.consent_event` com RLS forçada, gatilho append-only e a visão `current_consent` (`security_invoker`). *Pronto quando:* Testes de integração de CA5 (`UPDATE` e `DELETE` recusados até para o dono e o superusuário) e CA6 (isolamento entre dois usuários, sem contexto nada volta); tabela registrada no teste de catálogo.
- [x] E2-S2.3 Função `version_counts` com as contagens agregadas por tipo e versão. *Pronto quando:* Teste de integração: contagens corretas e nenhuma coluna de identidade na saída.

## S3: `consent`: casos de uso, API do cliente e barreira (RF2 a RF5; CA1, CA3, CA4)

- [x] E2-S3.1 Domínio: `ConsentType` (obrigatório ou opcional), estado derivado do último evento e pendência por versão. *Pronto quando:* Testes unitários, sem Spring e sem banco.
- [x] E2-S3.2 Casos de uso `ListMyConsents`, `GetCurrentPolicy`, `AcceptRequiredConsents` (`409 policy_version_outdated`) e `SetOptionalConsent`. *Pronto quando:* Testes unitários com fakes, incluindo versão desatualizada e revogação.
- [x] E2-S3.3 Adaptadores JDBC e rotas `GET /me/consents`, `GET /policies/{type}/current`, `POST /me/required-consents` e `PUT /me/consents/{type}`; OpenAPI atualizado. *Pronto quando:* Testes de integração de CA3 (reaceite por versão nova) e CA4 (opcionais desligados, ligar e revogar com efeito imediato).
- [x] E2-S3.4 `ConsentChecker` em `consent.api` (falha fechada), `@RequiresConsent` e o interceptador que responde `403 consent_required` antes do controlador. *Pronto quando:* Teste de integração de CA1; teste que enumera as rotas de saúde e falha se uma não tiver a anotação; provado que sem a barreira o teste reprova.

## S4: `consent`: backoffice (RF1, RF11; CA7)

- [x] E2-S4.1 Conversor do Markdown restrito em blocos estruturados, com `org.commonmark:commonmark` (versão conferida e fixada no `pom.xml`). *Pronto quando:* Testes unitários: HTML bruto, imagem, script e link `javascript:` são recusados; o resultado traz só parágrafo, título, lista, texto, ênfase e link `https` ou `mailto`.
- [x] E2-S4.2 Casos de uso e rotas de rascunho, pré-visualização e publicação (`/admin/policies/...`), só `ADMIN`. *Pronto quando:* Testes de integração de CA7: `ADMIN` publica e a nova versão vira a vigente; `USER` e `MODERATOR` recebem `403`; um rascunho por tipo (`409 draft_already_exists`).
- [x] E2-S4.3 `GET /admin/policies` com tipos, versões e contagens agregadas. *Pronto quando:* Teste de integração; a resposta não traz identidade de ninguém.

## S5: `consent`: eventos, confirmação e auditoria (RF6, RF7; CA8, CA10)

- [x] E2-S5.1 `ConsentAccepted` e `ConsentRevoked` no outbox, na mesma transação do evento de consentimento, com identificadores apenas. *Pronto quando:* Teste de integração: transação desfeita não deixa mensagem; confirmada, a mensagem chega à fila.
- [x] E2-S5.2 Consumidor idempotente `ConfirmConsentChange` que envia a confirmação pelo `Notifier` (adaptador falso). *Pronto quando:* Teste de CA10: a mesma mensagem duas vezes gera uma confirmação; falha na entrega não perde o evento e a nova tentativa funciona.
- [x] E2-S5.3 Auditoria `consent.accepted`, `consent.revoked`, `policy.draft_created` e `policy.published`, só com nomes de campo e identificadores. *Pronto quando:* Teste de CA8: o registro existe com quem, quando, o quê e resultado, e não contém o texto da política.

## S6: `profile`: catálogos e anamnese (RF8; CA6, CA9, CA11)

- [x] E2-S6.1 Migrações do `profile`: `catalog_item` com os dados de partida, `anamnesis_revision` e `anamnesis_item`, com RLS forçada e append-only. *Pronto quando:* Testes de integração: isolamento entre dois usuários, o escopo `admin` e o `system` não leem a anamnese (CA6), tabelas registradas no teste de catálogo.
- [x] E2-S6.2 Domínio e validação da anamnese: valores do catálogo, tamanhos curtos de texto e data de nascimento. *Pronto quando:* Testes unitários de CA11: valor fora do catálogo, texto acima do limite e data no futuro são recusados sem repetir o valor.
- [x] E2-S6.3 `SaveAnamnesis`, `GetMyAnamnesis`, `ListRevisions`, `GetRevision`, `GetCatalog` e as rotas (`/me/anamnesis`, `/me/anamnesis/revisions`, `/catalogs/{kind}`) com `@RequiresConsent`. *Pronto quando:* Testes de integração: salvar duas vezes cria duas revisões e a anterior continua legível; sem `HEALTH_DATA` a resposta é `403 consent_required` (CA1, CA11).
- [x] E2-S6.4 Estender o teste de valores sentinela às rotas da anamnese, com sucesso e com erro. *Pronto quando:* Teste de CA9: o sentinela não aparece em log, trace, métrica, mensagem de fila nem no corpo de erro; provado que reprova se o valor vazar.

## S7: `profile`: triagem (RF10; CA12)

- [x] E2-S7.1 `triage-rules.json` versionado (menor de 18, gestante ou nutriz, condição que exija acompanhamento, medicação que afete dieta ou treino, histórico de transtorno alimentar) e `EvaluateEligibility`. *Pronto quando:* Testes unitários: cada motivo sozinho e combinado, regra conservadora na dúvida, versão das regras devolvida.
- [x] E2-S7.2 Integrar a triagem ao `SaveAnamnesis` (marca `NOT_ELIGIBLE_FOR_AUTOMATIC_PLAN`, auditoria `eligibility.changed`, reversão ao refazer) e `EligibilityQuery` em `profile.api`. *Pronto quando:* Teste de integração de CA12: menor de idade fica inelegível, a marca é auditada, refazer a anamnese a remove.

## S8: Direitos do titular (RF9; CA13, CA14)

- [x] E2-S8.1 Migração do `platform` e do `consent`: função de gatilho de `audit_event` e `consent_event` com a exceção estreita (só `UPDATE` do identificador, só por `heracles_owner`), `pseudonymize_user` e `app_user.deleted_at`. *Segurança aprovada no plano, seção 11.* *Pronto quando:* Testes de integração: a aplicação e o superusuário continuam recusados; a função só troca o identificador; nenhuma outra coluna muda; o roteiro local de CA8 segue passando (este último ponto só se confirma contra a pilha viva, na E2-S9.2).
- [x] E2-S8.2 `ExportContributor` e `ErasureStep` em `platform.api`, e as contribuições de `consent` e `profile`. *Pronto quando:* Testes unitários da ordem e da idempotência; exportação de cada módulo só com o dado do próprio usuário.
- [x] E2-S8.3 Exportação: `POST /me/exports`, tarefa `RunExport` que grava o JSON no `FileStorage`, `GET /me/exports/{taskId}` com URL de 15 minutos e limpeza do arquivo em 24 horas. *Pronto quando:* Teste de CA13 com dois usuários: só os dados dela, a URL expira, outro usuário não consegue o arquivo, o arquivo é apagado no prazo.
- [x] E2-S8.4 Exclusão: `account_erasure`, pedido, cancelamento, agendador de vencidos (7 dias, relógio do port), `RunErasure`, `IdentityProvider.deleteUser` e pseudonimização. *Pronto quando:* Testes de CA14: a tarefa executada duas vezes tem o mesmo resultado; o `sub` antigo cria uma conta nova e vazia; a trilha e a prova do aceite continuam sem ligação com a pessoa.

## S9: Contrato e validação local (CA16 e evidência)

- [x] E2-S9.1 (cliente regenerado e compilando; "as telas usam os tipos gerados" se prova nas S10 e S11) Fechar o contrato OpenAPI e regenerar o cliente TypeScript com todas as rotas da etapa. *Pronto quando:* O job `api-client` do CI passa e as telas usam os tipos gerados (CA16).
- [x] E2-S9.2 Estender `docker/validate.sh` com barreira, aceite, revogação, anamnese, triagem, exportação e exclusão contra a pilha viva. *Pronto quando:* O roteiro passa e reprova quando uma regra é quebrada (provado).

## S10: Web (RF11; CA15)

- [x] E2-S10.1 Gate de aceite no primeiro acesso e quando há versão nova, e componente que desenha os blocos do texto. *Pronto quando:* Testes Vitest: sem aceite não chega à anamnese; blocos com link e lista desenhados; nada de HTML injetado.
- [x] E2-S10.2 Anamnese em passos, com triagem e aviso "procure um profissional". *Pronto quando:* Testes Vitest do preenchimento, das validações e da tela de inelegível.
- [x] E2-S10.3 "Meus consentimentos" (ligar e revogar opcionais) e "Privacidade" (exportar e excluir minha conta, com os 7 dias). *Pronto quando:* Testes Vitest dos estados e da confirmação de exclusão.
- [x] E2-S10.4 `/admin/policies`: listar versões e contagens, rascunho, pré-visualização e publicação. *Pronto quando:* Testes Vitest; a rota continua só para `ADMIN`.
- [x] E2-S10.5 Teste Playwright: usuário novo aceita, preenche a anamnese, vê seus consentimentos; `ADMIN` publica versão nova e o usuário é obrigado a aceitar de novo. *Pronto quando:* O fluxo passa no pipeline (CA15).

## S11: Mobile (RF11; CA15)

- [x] E2-S11.1 Troca de telas por estado (sem biblioteca de navegação), gate de aceite e componente dos blocos. *Pronto quando:* Testes Jest: sem aceite não chega à anamnese; blocos desenhados.
- [x] E2-S11.2 Telas de anamnese, "Meus consentimentos" e "Privacidade". *Pronto quando:* Testes Jest dos fluxos, das validações e da exclusão com os 7 dias.
- [x] E2-S11.3 (Android verificado por mim em 04/11/2026; iOS verificado pelo Dênis no simulador, relato dele em 04/11/2026) Verificação no emulador Android e no simulador iOS contra a pilha local. *Pronto quando:* Fluxo completo visto nos dois aparelhos e registrado no diário (CA15).

## S12: Infraestrutura e fechamento (RF9; fechamento)

- [x] E2-S12.1 Regra de ciclo de vida do prefixo `exports/` (expira em 1 dia) no módulo `storage` do OpenTofu. *Pronto quando:* `check-plan.sh` e o script do LocalStack passam e conferem a regra.
- [x] E2-S12.2 (fechamento escrito; gate decidido pelo Dênis ainda pendente) Conferir CA1 a CA16 da Etapa 2, escrever `docs/stage-2/fechamento.md` e atualizar o diário. *Pronto quando:* Cada critério marcado com a evidência; pendências listadas; gate da Etapa 2 decidido pelo Dênis.
