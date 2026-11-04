# Provas de mutação da Etapa 2 (S2 a S8)

- **Data:** 04/11/2026
- **O que é:** para cada regra de segurança e de negócio da S2 à S8, **quebrei a regra de propósito** (no código ou na migração), rodei os testes do módulo e conferi que **algum teste reprova**. Depois restaurei o arquivo e conferi que o código voltou idêntico ao instantâneo anterior. Um mutante que sobrevive é uma regra sem teste que a proteja.
- **Como foi feito:** 24 mutantes aplicados um de cada vez por script (`mutate.py`, fora do repositório: aplica a troca, roda `./mvnw verify` filtrado pelo módulo, registra e restaura). Sem ferramenta nova (não há PIT no projeto, e dependência nova exige aprovação). **Não é cobertura de mutação completa:** são os mutantes que escolhi, um por regra.

## Resultado: 24 de 24 mortos

Dois deles precisaram de correção antes de morrer (seção seguinte).

| # | Tarefa | Mutante (a regra quebrada) | Reprovou |
|---|---|---|---|
| M01 | S2.1 | gatilho da versão publicada invertido (deixa de ser imutável) | `ConsentApiIT`, `ConsentDataRightsIT` |
| M02 | S2.2 | exceção do dono no `consent_event` aceita qualquer coluna | `ConsentSchemaIT` (append-only, CA5) |
| M03 | S3.1 | aceite da versão antiga vale para a nova (sem reaceite) | `ConsentApiIT` (CA1, CA3) |
| M04 | S3.2 | aceite aceita ids que não são as 3 versões vigentes | `ConsentApiIT` (409), `ConsentUseCasesTest` |
| M05 | S3.4 | barreira deixa passar quem não consentiu | `AnamnesisApiIT` (CA1) |
| M06 | S3.4 | `ConsentChecker` responde **sim** quando falha | `DatabaseConsentCheckerTest` (novo, ver abaixo) |
| M07 | S4.1 | link `javascript:` aceito no texto da política | `PolicyBlocksTest`, `PolicyAdminApiIT` (CA7) |
| M08 | S4.2 | backoffice de políticas aberto a qualquer autenticado | `PolicyAdminApiIT` (só `ADMIN`) |
| M09 | S5.1 | mensagem do aceite não vai ao outbox | `ConsentEventsIT` |
| M10 | S5.2 | confirmação enviada duas vezes por entrega | `ConsentEventsIT` (CA10) |
| M11 | S5.3 | aceite e revogação deixam de ser auditados | `ConsentEventsIT` (CA8) |
| M12 | S6.1 | RLS da anamnese deixa de filtrar por usuário | `ProfileSchemaIT` (CA6), `ProfileDataRightsIT` |
| M13 | S6.1 | anamnese deixa de ser append-only | `ProfileSchemaIT`, `ProfileDataRightsIT` |
| M14 | S6.2 | data de nascimento no futuro deixa de ser recusada | `AnamnesisApiIT`, `AnamnesisUseCasesTest` (CA11) |
| M15 | S6.3 | numeração das revisões sempre 1 | `AnamnesisApiIT` (CA11, CA12) |
| M16 | S6.3 | rotas da anamnese sem `@RequiresConsent` | `AnamnesisApiIT` (CA1) |
| M17 | S6.4 | valor da anamnese vaza para o log | `AnamnesisSentinelIT` (CA9) |
| M18 | S7.1 | menor de idade deixa de ser inelegível | `AnamnesisApiIT`, `AnamnesisUseCasesTest` (CA12) |
| M19 | S7.2 | mudança de elegibilidade deixa de ser auditada | `AnamnesisApiIT`, `AnamnesisUseCasesTest` |
| M20 | S8.1 | auditoria deixa de ser append-only para o dono | `AuditEventIT`, `PseudonymizationIT` |
| M21 | S8.2 | passos da exclusão na ordem inversa | `DataRightsErasureTest` |
| M22 | S8.3 | link da exportação vale 25 h em vez de 15 min | `DataRightsExportTest` |
| M23 | S8.3 | exportação consultada fora do escopo do titular | `ExportIT` (CA13) |
| M24 | S8.4 | exclusão sem os 7 dias de espera | `DataRightsErasureTest` |

## O que a rodada achou

1. **M06 sobreviveu na primeira rodada: lacuna real.** Nenhum teste provava que o `ConsentChecker` responde "não" quando a consulta falha (falha fechada, E2-S3.4). Escrevi `DatabaseConsentCheckerTest` (falha ao ler as políticas e ao abrir a transação respondem "não"; um controle mostra que o "sim" existe quando há consentimento). Com o teste, o M06 morre; sem o mutante, o teste passa.
2. **M13 falhou como prova na primeira tentativa por erro meu:** mutei o gatilho da `profile.V3`, mas a `profile.V4` o redefine (`CREATE OR REPLACE`), então o mutante era equivalente e "sobreviveu". Refiz sobre a V4 e ele morre. O mesmo cuidado já valia para o M02 (`consent.V4`), que foi escrito direto na V4.
3. O resto morreu na primeira tentativa, e os testes que reprovaram são os que o `tasks.md` indica para cada regra.

## Para repetir

O script e os logs de cada mutante estão no scratchpad da sessão (fora do repositório). Se esta prova virar rotina, a ferramenta certa é o PIT, que pede aprovação por ser dependência nova.
