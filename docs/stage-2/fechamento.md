# Fechamento da Etapa 2 (consentimento e anamnese)

- **Tarefa:** E2-S12.2 (`specs/platform/stage-2-consentimento-anamnese/tasks.md`)
- **Data:** 04/11/2026
- **Estado proposto:** **concluída**, com uma pendência que já era assumida como tarefa própria: o adaptador real do Cognito para apagar a identidade no provedor. Os 16 critérios têm evidência, as provas de mutação da S2 à S8 e a E2-S1.4 foram feitas (seção 4).
- **Decisão do Dênis:** _aguardando._ O gate (seguir para a Etapa 3) é decisão do Dênis. Nada foi commitado: falta a revisão única de código no Plannotator.

## 1. Critérios de aceite

| CA | Estado | Evidência |
|---|---|---|
| **CA1** barreira de consentimento | **Atendido** | `HealthRoutesBarrierIT` (toda rota de saúde tem a barreira; falha se uma não tiver) e `ConsentApiIT`: sem `HEALTH_DATA_CONSENT` vigente, `403 consent_required` e nenhuma consulta de dado de saúde; aceitos os três, a rota abre. `E2-CA1` do `docker/validate.sh` repete na pilha viva. Web: `ConsentGate.test` e Playwright (o gate barra inclusive `/anamnesis`); mobile: `ConsentGate.test`. |
| **CA2** versão publicada é imutável | **Atendido** | `ConsentSchemaIT`: o banco recusa alterar o texto para a aplicação e para o dono; `PolicyAdminApiIT` pela API. |
| **CA3** reaceite | **Atendido** | `ConsentApiIT` e `E2-CA3` da pilha viva (versão nova torna o aceite pendente e o registro do aceite antigo continua). Web: Playwright (admin publica Termos novos e o usuário é obrigado a aceitar de novo); `ConsentGate.test` no web e no mobile ("os textos mudaram"). |
| **CA4** opcionais | **Atendido** | `ConsentApiIT`/`ConsentEventsIT` (nascem desligados; ligar e revogar muda o `ConsentChecker` na consulta seguinte). Pilha viva: `E2-CA4`. **No Android:** ativar e revogar gravam `GRANT\|MOBILE` e `REVOKE\|MOBILE`. |
| **CA5** consentimento append-only | **Atendido** | `ConsentSchemaIT`: `UPDATE` e `DELETE` recusados, inclusive dono e superusuário (a exceção estreita da pseudonimização é provada à parte, `PseudonymizationIT`). Pilha viva: `E2-CA5` com controle positivo (sem o gatilho, numa transação revertida, o `UPDATE` passa). |
| **CA6** isolamento por usuário | **Atendido** | `ProfileSchemaIT`, `ConsentSchemaIT` e o registro em `IsolationTests` (`DatabaseCatalogIT` falha se faltar): cada usuário só vê o seu mesmo sem filtro, sem usuário nada volta, `admin` e `system` não leem a anamnese. |
| **CA7** backoffice | **Atendido** | `PolicyAdminApiIT` (rascunho, pré-visualização, publicação, `USER`/`MODERATOR` recebem 403, HTML e `javascript:` recusados). Web: `PoliciesPage.test` (só `ADMIN`) e Playwright. |
| **CA8** auditoria | **Atendido** | `ConsentEventsIT`, `AnamnesisApiIT`, `ExportIT`, `ErasureIT`: registro de quem, quando, o quê e o resultado, só com nomes de campo. Pilha viva: `E2-CA8`. |
| **CA9** sem dado de saúde na saída | **Atendido** | `AnamnesisSentinelIT`: valores sentinela em sucesso e erro não aparecem em log, trace, métrica, mensagem de fila nem corpo de erro. Pilha viva: `E2-CA9` (log da aplicação, auditoria e fila). |
| **CA10** confirmação idempotente | **Atendido** | `ConsentEventsIT`: o mesmo evento duas vezes, uma confirmação no `NotificationChannel`; falha na entrega não perde o evento. |
| **CA11** anamnese e revisões | **Atendido** | `AnamnesisApiIT`, `AnamnesisValidatorTest`, `AnamnesisUseCasesTest`: duas revisões, a anterior legível; catálogo, limite de texto e data futura recusados. Telas: validações por passo no web e no mobile; anamnese salva no Android. |
| **CA12** triagem | **Atendido** | `TriageTest`, `AnamnesisApiIT`: menor fica `NOT_ELIGIBLE_FOR_AUTOMATIC_PLAN`, marca auditada, refazer remove. Pilha viva: `E2-CA12`. Telas: "Procure um profissional" no web (Vitest e Playwright) e no mobile (Jest). |
| **CA13** exportação | **Atendido** | `ExportIT`, `DataRightsExportTest`, `ConsentDataRightsIT`, `ProfileDataRightsIT`: só os dados da pessoa, URL de 15 minutos, expiração em 24 h, outro usuário não alcança. Pilha viva: `E2-CA13` baixa o arquivo pelo link no LocalStack. **No Android:** exportação real até "Baixar meus dados". Infra: regra de ciclo de vida de `exports/` (S12.1). |
| **CA14** exclusão | **Atendido** | `ErasureIT`, `DataRightsErasureTest`, `PseudonymizationIT`: 7 dias pelo relógio do port, tarefa idempotente, anamnese e revisões apagadas, trilha desvinculada, `sub` antigo abre conta nova e vazia. Pilha viva: `E2-CA14` com o agendador de verdade. **No Android:** confirmação com os 7 dias, agendada (`PENDING`, 7 dias) e cancelada (`CANCELED`). **Ressalva:** `IdentityProvider.deleteUser` é no-op no adaptador real (ver pendências). |
| **CA15** clientes | **Atendido** | **Web: atendido.** Playwright 5/5 contra a pilha local (usuário novo aceita, preenche, vê consentimentos; `ADMIN` publica; reaceite obrigatório). **Mobile:** 69 testes Jest; **Android verificado no emulador** contra a pilha local (login, gate, aceite `MOBILE`, anamnese, consentimentos, exportação, exclusão e cancelamento). **iOS: o Dênis fez o fluxo no simulador e relatou que está ok** (evidência é o relato dele; eu não consigo tocar no simulador). |
| **CA16** contrato | **Atendido** | `OpenApiContractIT` (o gerado difere do versionado: reprova) e job `api-client` (regenera e compila). As telas do web e do mobile usam os tipos gerados. Achado e corrigido nesta etapa: o `CurrentUser` vazava como parâmetro `user`; teste novo reprova qualquer operação que o liste. |

## 2. Números

- **Backend:** `./mvnw verify` verde: 340 testes (156 unitários, 184 de integração), 0 falhas, portão de 85% cumprido, ArchUnit e Modulith verdes.
- **Pilha viva (`docker/validate.sh`):** 79 ok e 0 falhas (Etapas 1 e 2); só a Etapa 2: 49 ok e 0 falhas (rodado de novo no fechamento).
- **Web:** 66 testes Vitest, `tsc` e build limpos; Playwright 5/5 contra a pilha local.
- **Mobile:** 69 testes Jest, `tsc` limpo.
- **Infra:** `check-plan.sh` passa para `smoke` e `prod` (reprovava sem a regra de `exports/`); `localstack.sh`: 22 ok, 0 falhas.

## 3. O que a verificação nos aparelhos pegou (e os testes não)

Três defeitos reais, todos corrigidos, com teste que agora reprova:

1. O backend exige os **ids das três versões vigentes** em `POST /me/required-consents`; o gate mandava só as pendentes. O Playwright pegou.
2. O backend exige `versionId` também ao **revogar**; web e mobile omitiam. O Android pegou. Os backends simulados dos testes agora recusam como o real.
3. Consentimento opcional **sem texto publicado** mandava `versionId` nulo e falhava; agora mostra "Texto ainda não publicado". O Android pegou.

E o do contrato (CA16) acima. Lição: o backend simulado dos testes de tela precisa validar como o real.

## 4. Provas de mutação e E2-S1.4 (feitas em 04/11/2026)

- **24 mutantes, um por regra da S2 à S8, 24 mortos** (detalhe em `docs/stage-2/provas-de-mutacao.md`). A rodada achou uma lacuna real, o `ConsentChecker` que responde "sim" quando falha (M06), coberta pelo novo `DatabaseConsentCheckerTest`; e um mutante meu equivalente (M13), refeito. Não é cobertura de mutação completa, são os mutantes escolhidos.
- **E2-S1.4:** `fixture/modules` com `consent` e `profile` usando `platform.application` (a regra de fronteira reprova) e `platform.api` (passa).

## 5. Pendências

1. **Cognito real:** `IdentityProvider.deleteUser` é no-op no adaptador OIDC genérico. A exclusão apaga tudo do nosso lado e desvincula o `sub`, mas **não apaga a conta no provedor**; é tarefa própria (ADR-0008).
2. **Ambiente novo não tem texto publicado** (as migrações não semeiam): um `ADMIN` precisa publicar os 3 textos obrigatórios (e os opcionais que forem usados) antes de qualquer pessoa aceitar. Os textos reais precisam de revisão jurídica; é passo operacional do primeiro go-live, não de migração.
3. **Cobertura que fica nos testes de integração:** o disparo real da exclusão em 7 dias de calendário (a pilha viva move o prazo no banco), e o link de exportação aberto no emulador Android (o link agora aponta para o endereço publicado, `localhost:4567`, e precisa de `adb reverse tcp:4567`; o `validate.sh` baixa o link exatamente como o backend o entrega).
4. **Visual:** telas mínimas, sem identidade visual e sem área segura no topo (o título encosta na barra de status quando a tela é alta). Fora do escopo da etapa.
5. **Ambiente de fumaça na AWS (E1-S8.4):** continua pendente da Etapa 1, depende de autorização de custo.

## 6. Para quem já tem a pilha antiga

O LocalStack precisa do bucket `heracles-files`: `docker compose -f docker/compose.yml up -d --force-recreate localstack`.

## 7. Próximos passos

Com a sua decisão do gate: abrir a revisão única de código no Plannotator (`plannotator review`), e só com a aprovação seguem commit, push, PR, merge e limpeza das branches (CLAUDE.md, seção 9). A revisão leva junto as provas de mutação e a E2-S1.4.
