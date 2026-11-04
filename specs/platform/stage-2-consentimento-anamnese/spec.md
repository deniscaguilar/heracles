# Spec: Etapa 2, cadastro, consentimento e anamnese

- **Status:** Aprovada (03/11/2026)
- **Origem:** `docs/roadmap.md`, Etapa 2 e seção 7 (travas de segurança); `docs/architecture.md` (módulos `consent` e `profile`); ADR-0003 (isolamento e auditoria), ADR-0004 (filas), ADR-0008 (identidade); `docs/stage-0/p8-pendencias.md` (J1, J4, J10, C7, C12); `docs/stage-1/fechamento.md`
- **Módulos:** `consent` e `profile` (novos), com ajustes em `platform` (porta de consentimento, exclusão de conta) e nos dois clientes. O backoffice é um canal, não um módulo.

## 1. Problema

A fundação está pronta, mas nenhum dado de saúde pode ser coletado sem antes registrar **que a pessoa sabe o que será feito com ele e concordou**. A LGPD dá ao dado de saúde regime próprio (consentimento específico), e o `CLAUDE.md` (seção 4) já impõe: exame só vai a IA externa com consentimento específico e vigente, promoção só para quem aceitou, e dado de saúde nunca é segmento de venda. Se a anamnese vier antes do consentimento, ou se o consentimento for um "aceito" solto sem versão nem prova, o remendo depois custa caro e deixa dado coletado sem base. Esta etapa entrega o **consentimento versionado e auditado**, a **anamnese** (primeiro dado de saúde de verdade), a **triagem de quem fica fora do plano automático** e os **direitos do titular** (exportar e excluir), nas duas telas de cliente e no backoffice mínimo.

## 2. Objetivo

Ao fim da etapa: o cliente só chega à anamnese depois de aceitar os termos, a política de privacidade e o consentimento de dado sensível de saúde, em suas **versões vigentes**; o Dênis publica novas versões pelo backoffice e quem aceitou uma versão antiga é obrigado a aceitar de novo; consentimentos opcionais (IA em exames, comunidade, notificações, comunicação promocional) são separados, começam desligados e podem ser revogados a qualquer momento, com **efeito imediato** nos módulos que dependem deles; a anamnese fica isolada por usuário no banco; quem cai numa condição que o plano automático não atende é identificado e **barrado do plano**, sem diagnóstico; o titular consegue **exportar** e **excluir** seus dados; tudo é auditado sem dado de saúde no texto.

## 3. Requisitos

**RF1. Políticas versionadas (`consent`).** Tipos de política: `TERMS_OF_USE`, `PRIVACY_POLICY`, `HEALTH_DATA_CONSENT`, `AI_EXAMS_CONSENT`, `COMMUNITY_CONSENT`, `NOTIFICATIONS_CONSENT`, `PROMOTIONAL_CONSENT`. Cada política tem **versões**; uma versão publicada é **imutável** (corrigir texto é nova versão). Só pode haver uma versão vigente por tipo. O texto é cadastrado em português, em formato restrito (parágrafos, listas, negrito e links), nunca HTML livre. Política **não é dado de usuário** (sem `user_id`), mas só `ADMIN` escreve.

**RF2. Aceite e revogação.** O aceite grava quem, qual **versão** viu, quando e por qual cliente (`WEB`, `MOBILE`), em registro **append-only** (aceite e revogação são eventos, nunca atualização). Os três consentimentos **obrigatórios** (`TERMS_OF_USE`, `PRIVACY_POLICY`, `HEALTH_DATA_CONSENT`) são pedidos juntos no primeiro acesso; os quatro **opcionais** são pedidos um a um, **desligados por padrão**, sem pré-marcação. O estado vigente de cada consentimento é derivado do último evento.

**RF3. Nova versão exige novo aceite.** Quando uma versão obrigatória nova é publicada, quem aceitou a anterior fica **pendente**: o app mostra o que mudou (o texto novo) e bloqueia o uso de funções de saúde até o novo aceite. O aceite antigo permanece ligado à versão que a pessoa viu. Versão nova de política opcional **não** liga nada: a pessoa continua com o estado que tinha e é convidada a rever.

**RF4. Porta de consentimento (`ConsentChecker`).** O `consent` expõe pela sua `api` a pergunta "o usuário consentiu em X, na versão vigente?" e o evento `ConsentAccepted`/`ConsentRevoked` (identificadores apenas). Outros módulos **nunca** leem as tabelas do `consent`. Falha fechada: se não for possível responder, a resposta é "não". A revogação vale **no mesmo instante** da consulta seguinte.

**RF5. Barreira do dado de saúde.** Todo endpoint que lê ou grava dado de saúde (anamnese nesta etapa; os de exames, plano, medidas e check-ins nas etapas seguintes) exige `HEALTH_DATA_CONSENT` vigente e responde `403` com o código estável `consent_required` quando falta, **antes** de tocar no banco. Revogar `HEALTH_DATA_CONSENT` **não apaga** o dado (isso é a exclusão, RF9): suspende o uso até novo aceite.

**RF6. Auditoria.** Aceite, revogação, publicação de versão, pedido de exportação, pedido de exclusão e resultado de cada um entram na trilha da fundação (`RecordAudit`), com nomes de campo e identificadores, **nunca** o texto da política nem valor da anamnese.

**RF7. Confirmação em segundo plano.** `ConsentAccepted` e `ConsentRevoked` vão pelo outbox e, no consumidor idempotente, disparam uma confirmação à pessoa pelo port `NotificationChannel` (nesta etapa o adaptador falso/local; nenhum envio real). A mesma mensagem entregue duas vezes gera **uma** confirmação.

**RF8. Anamnese (`profile`).** Dado de saúde, isolado por usuário (RLS forçada, ADR-0003). Campos:
- objetivo (`OBJECTIVE`: valores fechados, por exemplo emagrecimento, ganho de massa, saúde geral, desempenho) e nível de treino (`TRAINING_LEVEL`: iniciante, intermediário, avançado);
- **alergias, intolerâncias e restrições** (lista de itens de um catálogo, mais "outro" em texto curto), cada item com seu tipo;
- preferências alimentares (itens de catálogo, por exemplo vegetariano, e alimentos que não gosta, em texto curto);
- **equipamento disponível** (itens de catálogo);
- **limitações físicas e lesões** (região do corpo do catálogo, mais uma observação curta);
- data de nascimento e sexo biológico (ver pergunta 6), necessários para a triagem de idade e, na Etapa 3, para as faixas de referência.

Texto livre é **curto, com limite de tamanho**, e tratado como dado de saúde em todo lugar (nunca em log, trace, métrica ou fila). Os catálogos nascem como dados de partida em migração e são lidos pela API; editá-los pelo backoffice fica para depois (RF12, fora de escopo). Cada gravação cria uma **revisão** (histórico append-only da anamnese), porque o plano da Etapa 4 precisará saber com que anamnese foi gerado.

**RF9. Direitos do titular.**
- **Exportação:** o titular pede; uma tarefa assíncrona (Etapa 1) monta um arquivo JSON com **o que o produto guarda dele** (perfil, anamnese e revisões, histórico de consentimento, trilha de auditoria que o cita) e o grava no `FileStorage`; a pessoa recebe uma **URL pré-assinada de vida curta**. O arquivo é apagado em prazo definido (pergunta 4). Só o próprio titular exporta.
- **Exclusão:** o titular pede, confirma, e uma tarefa assíncrona apaga o dado de saúde e de perfil e desvincula a identidade (ver pergunta 4 para o que fica). Pelo ADR-0008, a exclusão remove o usuário no `platform` e depois no provedor de identidade (adaptador falso nesta etapa).
- Os dois pedidos têm status consultável por `GET /api/v1/tasks/{id}` e geram auditoria.

**RF10. Triagem de quem fica fora do plano automático.** A anamnese inclui perguntas de triagem (menor de idade pela data de nascimento, gestação ou amamentação, condição que exija acompanhamento, uso de medicação que o plano deva considerar, histórico de transtorno alimentar; **lista e regras provisórias**, pergunta 5). Qualquer resposta que a regra provisória trate como impeditiva marca o perfil como `NOT_ELIGIBLE_FOR_AUTOMATIC_PLAN` e mostra o aviso "procure um profissional". A marca é **auditada**, **reversível pela própria pessoa** ao refazer a anamnese, e é lida pelos módulos de treino e cardápio (Etapa 4) por uma consulta da `api` do `profile`. O app **nunca** diagnostica, prescreve nem recomenda conduta.

**RF11. Backoffice mínimo e clientes.**
- **Web `/admin` (perfis `ADMIN`):** listar políticas e versões, criar rascunho, **pré-visualizar** e publicar versão; o texto publicado não se edita. `MODERATOR` não acessa.
- **Web e mobile (cliente):** tela de aceite no primeiro acesso (obrigatórios juntos, opcionais separados), tela "Meus consentimentos" (ver, ligar e revogar opcionais), anamnese em passos, "Exportar meus dados" e "Excluir minha conta". Textos em pt-BR via i18n, chaves em inglês. Sem offline (o offline é a Etapa 5); o banco local cifrado do mobile **não** guarda anamnese ainda.
- Contrato OpenAPI atualizado e cliente TypeScript regenerado, como na fundação.

**RF12. Travas desta etapa.** Os textos jurídicos (termos, privacidade, consentimentos) e a lista de triagem são **provisórios** até o parecer (J1, J4, J10) e a validação do nutricionista (C7, C12). Enquanto forem provisórios, **o produto segue restrito ao Dênis** (`docs/stage-0/p8-pendencias.md`, seção 1): nenhum usuário externo, nem teste fechado. Decisão de 03/11/2026: o sistema **não** tem marca de "revisada por advogado" nem aviso de texto provisório; o texto cadastrado vale como está e a validação jurídica acontece depois, fora do sistema (J1, J4 e J10 continuam abertas em `p8-pendencias.md`).

## 4. Critérios de aceite

**CA1 (barreira).** *Dado* um usuário sem `HEALTH_DATA_CONSENT` vigente, *quando* chama qualquer endpoint de anamnese, *então* recebe `403` com `code = consent_required` e **nenhuma consulta de dado de saúde é feita**; *quando* aceita os três obrigatórios, *então* a mesma chamada passa.

**CA2 (versão imutável).** *Dado* uma versão publicada, *quando* alguém tenta alterar o texto (pela API, por SQL com o papel da aplicação ou pelo dono), *então* o banco recusa; corrigir exige uma versão nova.

**CA3 (reaceite).** *Dado* que o usuário aceitou a versão 1 de `TERMS_OF_USE` e a versão 2 foi publicada, *quando* ele usa o app, *então* fica pendente, vê o texto novo e não acessa função de saúde até aceitar a 2; o registro do aceite da 1 continua ligado à 1.

**CA4 (opcionais).** *Dado* um usuário novo, *quando* abre "Meus consentimentos", *então* os quatro opcionais estão **desligados**; *quando* liga e depois revoga um deles, *então* `ConsentChecker` responde "sim" e depois "não" **na consulta seguinte**, e os dois eventos existem em ordem.

**CA5 (append-only).** *Dado* a tabela de eventos de consentimento, *quando* se tenta `UPDATE` ou `DELETE` (inclusive como dono e superusuário), *então* o banco recusa, como na auditoria.

**CA6 (isolamento, CA7 da fundação).** *Dado* os usuários A e B com consentimentos e anamnese, *quando* A consulta, *então* só vê os seus, **mesmo com uma consulta sem filtro de usuário**; sem usuário no contexto nada volta; o escopo `admin` **não lê** a anamnese nem as revisões; o teste de catálogo da fundação falha se alguma tabela nova com `user_id` não tiver RLS forçada e teste de isolamento.

**CA7 (backoffice).** *Dado* um `ADMIN`, *quando* cria rascunho, pré-visualiza e publica, *então* a nova versão vira a vigente e a anterior deixa de ser; *dado* `USER` ou `MODERATOR`, *então* recebe `403`. O texto com HTML, script ou link `javascript:` é recusado ou neutralizado na gravação e na exibição.

**CA8 (auditoria).** *Dado* aceite, revogação, publicação, pedido de exportação ou de exclusão, *então* há registro de quem, quando, o quê e o resultado, **sem** o texto da política nem valor da anamnese.

**CA9 (sem dado de saúde na saída).** *Dado* uma anamnese com valores sentinela (alergia, lesão, observações), *quando* é gravada com sucesso e com erro, *então* os valores **não** aparecem em log, trace, métrica, mensagem de fila nem no corpo de erro (o teste da fundação é estendido aos novos endpoints).

**CA10 (confirmação idempotente).** *Dado* `ConsentAccepted` entregue **duas vezes** ao consumidor, *então* o `NotificationChannel` recebe **uma** confirmação; uma falha na entrega não perde o evento e a nova tentativa funciona.

**CA11 (anamnese e revisões).** *Dado* uma anamnese salva e depois alterada, *então* existem duas revisões, a atual é a última e a anterior continua legível pelo dono; valores fora do catálogo, texto acima do limite e data de nascimento no futuro são recusados com `400`, `code` estável e `traceId`, sem repetir o valor.

**CA12 (triagem).** *Dado* um perfil com data de nascimento que indica menor de idade (ou outra resposta impeditiva pela regra provisória), *então* o perfil é marcado `NOT_ELIGIBLE_FOR_AUTOMATIC_PLAN`, o app mostra "procure um profissional", a marca é auditada, e a consulta da `api` do `profile` devolve inelegível; refazer a anamnese com respostas elegíveis remove a marca.

**CA13 (exportação).** *Dado* o pedido de exportação, *quando* a tarefa termina, *então* `GET /api/v1/tasks/{id}` mostra `DONE` e a pessoa recebe uma URL pré-assinada que baixa um JSON com **só os dados dela**; a URL expira; outro usuário não consegue o arquivo; o arquivo é apagado no prazo definido.

**CA14 (exclusão).** *Dado* o pedido de exclusão confirmado, *quando* a tarefa termina, *então* a anamnese, as revisões e o perfil do usuário deixam de existir, a identidade é desvinculada no `platform` e no adaptador de identidade, o token antigo não cria outro usuário com os dados antigos, e a trilha guarda apenas o que a pergunta 4 definir; a tarefa executada **duas vezes** tem o mesmo resultado.

**CA15 (clientes).** *Dado* o app web e o app mobile contra a pilha local, *quando* um usuário novo entra, *então* vê a tela de aceite, só chega à anamnese depois dela, preenche e salva, vê "Meus consentimentos", e `ADMIN` publica uma versão em `/admin` e o usuário é obrigado a aceitar de novo; o teste de ponta a ponta do web cobre esse fluxo e o do mobile é verificado no emulador e no simulador.

**CA16 (contrato).** *Dado* o contrato OpenAPI, *então* o pipeline falha se o gerado difere do versionado, e o cliente TypeScript regenerado compila e é usado pelas telas.

## 5. Decisões propostas (a confirmar nesta revisão)

| Tema | Proposta |
|---|---|
| Consentimentos obrigatórios e opcionais | 3 obrigatórios (termos, privacidade, dado de saúde) aceitos juntos; 4 opcionais separados, desligados por padrão (RF2) |
| Eventos, não estado | Aceite e revogação são eventos append-only; o estado vigente é derivado (CA5) |
| Esquemas do Postgres | `consent` e `profile`, cada um com seu schema e sua pasta de migrações Flyway (CLAUDE.md, regra 3); sem FK entre schemas, só identificadores |
| Política de texto | Formato restrito, sanitizado na gravação e na exibição; nada de HTML livre |
| Revisões da anamnese | Cada gravação cria uma revisão append-only; a "atual" é a última |
| Catálogos (alergias, equipamento, regiões do corpo, objetivos) | Dados de partida em migração, lidos pela API; edição pelo backoffice fica para etapa posterior |
| Triagem | Regra provisória e **conservadora**: na dúvida, fora do plano automático; lista e regras em arquivo de configuração versionado, fácil de trocar quando o nutricionista validar |
| Notificação | Só pelo port `NotificationChannel`, adaptador falso/local nesta etapa |
| Exportação | JSON em S3 (SSE-KMS) com URL pré-assinada de vida curta; prazo de apagamento configurável |
| Identidade | Continua o servidor OIDC de mentira (ver pergunta 1) |
| Dependências novas | Previstas e a aprovar no `plan.md`: biblioteca de sanitização de texto (back e front); nenhuma outra por ora |

## 6. Fora de escopo

- Login real contra o Cognito, e-mail/senha/Google/Apple (ver pergunta 1).
- Qualquer envio real de push, WhatsApp ou e-mail.
- Exames, IA, plano de treino, cardápio, medidas corporais, check-ins, assinatura, comunidade e gamificação.
- Edição de catálogos (alergias, equipamento etc.) e dos tipos de exame pelo backoffice.
- Sincronização offline e anamnese no banco local do mobile (Etapa 5).
- Usuário externo de qualquer tipo, enquanto os textos e a triagem forem provisórios (RF12).
- Relatório de impacto (RIPD), nomeação do encarregado (DPO) e parecer jurídico: dependem do advogado (J1 a J4, J10).

## 7. Perguntas e respostas (revisão de 03/11/2026)

O Dênis respondeu às oito perguntas pelo Plannotator; as propostas foram aceitas, com um ajuste na 8. Isto fixa a decisão:

1. **Cadastro com conta própria, Google e Apple:** o servidor OIDC de mentira continua nesta etapa. O Cognito vira uma **tarefa própria**, antes do primeiro usuário externo, e passa por você por criar serviço AWS e custo (CLAUDE.md, seção 10).
2. **Obrigatórios:** termos, privacidade e dado de saúde. Opcionais e desligados por padrão: IA em exames, comunidade, notificações e promocional.
3. **Revogar `HEALTH_DATA_CONSENT`:** suspende o uso das funções de saúde até novo aceite, sem apagar; apagar é o pedido de exclusão.
4. **Retenção, exportação e exclusão (provisório, validação jurídica depois):** arquivo de exportação vive 24 horas; exclusão com 7 dias de arrependimento, depois apaga perfil, anamnese e revisões e desvincula a identidade; a trilha de auditoria e a prova do aceite ficam sem ligação com a pessoa (código que não volta ao usuário).
5. **Triagem (provisória):** impedem o plano automático menor de 18 anos, gestante ou nutriz, condição que exija acompanhamento, medicação que afete dieta ou treino e histórico de transtorno alimentar. Lista inicial e conservadora, em arquivo de configuração, para o nutricionista corrigir. O app só avisa "procure um profissional".
6. **Data de nascimento e sexo biológico:** os dois agora; sexo opcional, com "prefiro não informar".
7. **Visibilidade do admin:** não vê quem consentiu em quê por usuário; só contagens agregadas por versão.
8. **Revisão jurídica no sistema:** **sem** marca de "revisada por advogado" e **sem** aviso de texto provisório. Segue o texto cadastrado, e a validação jurídica vem depois, fora do sistema. (Ajuste em relação à proposta; refletido no RF12.)

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 03/11/2026
