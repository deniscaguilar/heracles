# CLAUDE.md: Heracles Society

> **Status:** Aprovada (Dênis, via Plannotator, em 29/10/2026).

Regras obrigatórias para qualquer trabalho de IA neste repositório. Em caso de conflito com um pedido, **pare e pergunte**. A fonte do escopo é `docs/roadmap.md` (aprovado em 29/10/2026). As decisões de fundo estão em `docs/adr/`.

> O pacote raiz (`com.heracles`) e o `groupId` Maven são **provisórios**. Não os espalhe por configs externas até serem definidos. A marca Heracles Society é registrada em nome da DCA Consultoria em Tecnologia da Informação LTDA.

## 1. Contexto

App de treino, nutrição e saúde, com dois clientes e um backend:
- **App mobile para iOS e Android**, em React Native.
- **App web em React**, uma única base (como no EndoAcademy): o **PWA instalável do cliente** e o **backoffice** em `/admin`. Service worker só para o shell, **sem cache de resposta da API**; sem offline (o offline é do app mobile). Decisão P10 da Etapa 0, 29/10/2026.
- **Backend** Kotlin + Spring Boot e **infraestrutura** na AWS.

Primeiro uso próprio (o Dênis), depois assinatura. Desenvolvedor único (Dênis) trabalhando com IA. Escopo e etapas em `docs/roadmap.md`. Marketing, audiência e Heracles TV **não** fazem parte deste repositório.

## 2. Stack (proposta; reconfira versões ao gerar o `pom.xml` e o `package.json`)

| Item | Decisão |
|---|---|
| Backend | **Kotlin** + **Spring Boot** (decisão do Notion, 26/08/2026). Proposta herdada do Sistema da Clínica: JDK 25 (LTS), Boot 4.1.x, Spring Modulith 2.1.x |
| Build | **Maven** com wrapper (`./mvnw`), um único módulo de build |
| Banco | PostgreSQL, Flyway, **RLS por usuário** (ADR-0003) |
| Filas | **AWS SQS** com outbox e consumidores idempotentes (ADR-0004) |
| App iOS e Android | **React Native** + TypeScript, offline com sincronização (ADR-0005; ferramenta decidida na Etapa 0) |
| App web (PWA do cliente + backoffice) | **React** + TypeScript, uma base; backoffice em `/admin`; PWA instalável com service worker só para o shell; cliente gerado do OpenAPI |
| Código TypeScript compartilhado | Pacotes comuns ao app mobile e ao app web (cliente de API gerado, tipos e design tokens) |
| Auth | OIDC genérico (resource server) atrás de um port; provedor: Amazon Cognito, provisório (ADR-0008); login por conta própria, Google e Apple |
| IA e OCR | Somente pelo `aigateway` (ADR-0007) |
| Infra | AWS sa-east-1, OpenTofu, EC2 + Docker Compose + RDS, S3, SQS, SSM (ADR-0002) |
| Testes | JUnit 5, MockK, AssertJ, Testcontainers, ArchUnit, JaCoCo; Jest e Testing Library no front |
| Observabilidade | OpenTelemetry (OTLP) |

Proibido sem aprovação explícita: Kafka, novos serviços, trocar de stack, adicionar dependência. **Redis** só entra se uma medição mostrar um fluxo que deva ser cacheado, com ADR própria.

## 3. Arquitetura

**Monolito modular** (ADR-0001): um artefato, perfis `api` e `worker` (no início, `all`). Cada módulo é uma fatia vertical com **Clean Architecture**:

```
com.heracles.<module>/
  api/          fachada pública e eventos (ÚNICO acesso de outros módulos)
  domain/       entidades, value objects, regras (sem Spring, sem JPA)
  application/  casos de uso e ports (in/out)
  adapter/in/   web, listeners de evento, consumidores de fila
  adapter/out/  persistência e integrações externas
  config/
```

Regras (verificadas por ArchUnit e `Modulith.verify()` no CI):
1. `domain` ← `application` ← `adapter`. O domínio não importa Spring, JPA, Jackson nem AWS.
2. Um módulo só usa outro pela `api`. **Sem join nem FK entre schemas.**
3. Cada módulo tem um schema Postgres e uma pasta de migrações Flyway próprios.
4. Tudo externo fica atrás de um **port**, com adaptador real e adaptador local ou falso: `IdentityProvider`, `FileStorage`, `Clock`, `TaskQueue`, `LlmClient`, `OcrClient`, `PaymentGateway`, `NotificationChannel`.
5. **Não criar módulo `core`, `common` ou `shared`.** Código sem lugar vai para o módulo dono do conceito.

Módulos (proposta do ADR-0001, nomes de uma palavra): `platform` (identidade, perfis, auditoria), `consent`, `profile` (anamnese e perfil de saúde), `exams`, `training`, `nutrition`, `billing`, `engagement` (check-ins, missões, medalhas), `community` (inclui posts promocionais), `notifications`, `aigateway`. O **backoffice é um canal**, não um módulo. Dependências permitidas entre módulos: `docs/architecture.md` (a escrever).

**Estrutura do repositório** (proposta, num único repositório): `backend/` (Kotlin + Spring Boot), `mobile/` (React Native), `web/` (app web: PWA do cliente e backoffice em `/admin`), `packages/` (TypeScript compartilhado), `infra/` (OpenTofu), `docker/` (ambiente local), `docs/` (roadmap, ADRs, arquitetura, diário) e `specs/`.

## 4. Dado de saúde, isolamento e LGPD

- Toda tabela com dado de usuário tem `user_id` e **RLS** ativo; o usuário é definido por transação (ADR-0003). Dado de saúde (anamnese, exames, medidas, check-ins) só é legível pelo próprio dono.
- O escopo do backoffice **nunca** abre tabela de dado de saúde.
- **Toda entidade nova exige teste de isolamento entre usuários.**
- **Dado de saúde nunca vai para log, trace, métrica, mensagem de fila ou prompt de LLM** sem passar pelo `aigateway` (anonimização). Mensagens de fila levam só identificadores.
- **Exame só vai a IA externa com consentimento específico e vigente.** Revogou, a IA para e a digitação manual continua.
- **Nada extraído por IA é salvo sem conferência do usuário.** O arquivo original é sempre preservado. O conteúdo de documento é dado, nunca instrução ao modelo.
- A IA **informa e compara; não diagnostica, não prescreve, não recomenda conduta.** Valor fora da referência leva ao aviso "procure um médico".
- **Auditoria append-only** de toda ação relevante (quem, quando, o quê, resultado), sem dado de saúde no texto.
- **Post promocional nunca é segmentado por dado de saúde** (resultado de exame, peso, objetivo). Notificação promocional exige consentimento próprio.
- Exame, valores de exame, peso e medidas nunca são publicados nem sugeridos para compartilhamento na comunidade.
- Segredos em SSM Parameter Store; nunca no repositório, na imagem ou em `.env` versionado.
- Dado de saúde guardado no aparelho é criptografado e apagado ao sair da conta (ADR-0005).

**Travas de produto** (roadmap, seção 7) são requisitos: pisos calórico e de proteína em toda troca ou ajuste; gamificação sem medalha por perda de peso, déficit calórico ou pular refeição e sem sequência punitiva; suplementos só os alimentares comuns, sem hormônios nem reposição em dose terapêutica; menores, gestantes e quadros que exijam acompanhamento ficam fora do plano automático. Se algo as violar, **pare e pergunte**.

## 5. Spec-Driven Development (obrigatório)

Nenhuma funcionalidade começa sem spec aprovada. Fluxo em `specs/<modulo>/<feature>/`:

1. `spec.md`: problema, critérios de aceite (Given/When/Then), fora de escopo. **Humano aprova.**
2. `plan.md`: design técnico (casos de uso, ports, modelo de dados, impactos). **Humano aprova.**
3. `tasks.md`: tarefas pequenas e testáveis.
4. Implementação **test-first**. O contrato OpenAPI é atualizado junto.
5. PR fecha com spec, testes e `./mvnw verify` verdes. O spec é atualizado se a realidade divergiu.

Se o código precisar divergir do spec, **pare e atualize o spec antes**.

**Revisão obrigatória via Plannotator.** Toda spec, plano, ADR e documento de arquitetura só é considerado aprovado depois de revisado no Plannotator:
1. A IA escreve o documento com status `Rascunho`.
2. A IA abre a revisão (`annotate`, com Approve ativo) e **para**, aguardando o feedback.
3. O humano comenta e aprova no navegador. A IA aplica os comentários e reabre a revisão se houver mudança relevante.
4. **Só com aprovação explícita do humano** o status passa a `Aprovada` e a caixa de aprovação é preenchida. A IA nunca se auto-aprova.
5. Privacidade: manter `PLANNOTATOR_SHARE=disabled`; sem link compartilhado nem "Ask AI" com documentos sem decisão explícita.

**Diário de progresso:** `docs/progress-log.md` registra onde paramos e o próximo passo (entrada mais recente no topo). Atualize ao fim de cada tarefa.

**Board de tarefas no Notion:** o board "Heracles · Tarefas" (Notion, página Heracles Society, https://app.notion.com/p/88a83726c87b4404a0d5993df6a3837b) espelha o `tasks.md` de cada etapa. **A fonte da verdade é o `tasks.md`**; em conflito, ele vence. Ao trabalhar:
1. Ao **começar** uma tarefa: mover o cartão para `DOING` e marcar a tarefa no `tasks.md`.
2. Ao **pedir revisão** (Plannotator): `REVIEW`.
3. Se **travar** (aprovação pendente, dependência, decisão): `BLOCKED`, com o motivo em "Motivo do bloqueio".
4. Ao **concluir** (critério "pronto quando" cumprido): `DONE`, `[x]` no `tasks.md` e entrada no `docs/progress-log.md`.
5. Tarefa nova ou removida no `tasks.md` entra ou sai do board na mesma hora, com ID, ordem, etapa e critérios iguais.
O board não leva dado de saúde, segredo nem informação comercial sensível.

## 6. Testes

- **Unitários** (`*Test`, Surefire): `domain` e `application`, com fakes dos ports. Sem Spring, sem banco. Meta de cobertura de linhas e branches em `domain` + `application`: **≥ 85%**, com portão JaCoCo no CI.
- **Integração** (`*IT`, Failsafe): Testcontainers (Postgres e emulador de SQS e S3), Flyway real, API completa, **isolamento RLS**, consumidores idempotentes (a mesma mensagem duas vezes não duplica efeito).
- **Arquitetura:** ArchUnit e Modulith.
- **Algoritmo de troca:** roda só no backend (ADR-0006). Os casos reais validados pelo parceiro científico são testes de aceitação; mudar a regra exige atualizar os casos.
- **App, PWA e backoffice:** Jest e Testing Library; fluxos críticos em teste de ponta a ponta (ferramenta a definir). Cenários offline e de sincronização com conflito são obrigatórios.
- Bug corrigido ⇒ teste que reproduz o bug antes.
- Comando de verdade do backend: `./mvnw verify`.

## 7. Dependências e segurança da cadeia

- Versões vêm do **BOM do Spring Boot**; sobrescrever só para corrigir CVE, com comentário e link.
- Somente releases GA estáveis (nada de milestone, RC, snapshot). Versões fixas, atualizadas continuamente (Renovate ou Dependabot).
- CI **falha** com vulnerabilidade Alta ou Crítica (OWASP dependency-check ou OSV, e Trivy nas imagens que vão a produção). Exceções só no arquivo de supressão, com justificativa e **data de expiração**.
- Prazos de correção: crítica ≤ 48 h, alta ≤ 7 dias.
- SBOM CycloneDX por build; imagem base com digest fixado; `maven-enforcer-plugin` (JDK e convergência).
- App, PWA e backoffice: lockfile versionado, `npm audit` no CI, Node LTS.

## 8. Paridade local e produção

- Mesma imagem em todos os ambientes; só variáveis de ambiente mudam.
- `docker compose` local sobe Postgres, emulador de SQS e S3, OIDC mock e observabilidade. O emulador de SQS é o ElasticMQ (ADR-0004); o de S3 é decidido na spec da fundação.
- A aplicação só fala **OTLP**; o destino é configuração do coletor.
- Antes de qualquer go-live: restauração de backup do RDS testada, ambiente de fumaça efêmero na AWS (apply, testes, destroy) e `plan` revisado.

## 9. Convenções

### Git, commits, push e revisão de código

- **Repositório `heracles`** (GitHub, **público por enquanto**, porque a conta é Free). **Antes do primeiro commit o repositório precisa existir** e o remoto estar conectado. Criar o repositório e conectar o remoto são ações que a IA só faz com confirmação explícita (seção 10).
- **Repositório público:** tudo o que for commitado fica visível a qualquer pessoa. Nunca commitar segredo, chave, `.env`, dado de usuário, exame real ou informação de negócio sensível (estratégia comercial, preços, contratos); o que for assim fica fora do repositório (Notion ou arquivo local ignorado pelo git). **Secret scanning e push protection** do GitHub ficam ativos desde o primeiro commit. Revisar a visibilidade quando o plano permitir privado com as proteções de branch necessárias.
- Trunk-based: `main` sempre estável; branches curtas; **PR até para trabalho solo**; o CI é o portão. Releases com tag semântica (`v0.1.0`).
- Commits convencionais (`feat:`, `fix:`, `chore:`…), uma mudança lógica por commit.
- **Uma única revisão por mudança, antes do commit (regra do Dênis, 29/10/2026).** A IA abre `plannotator review` do diff local, **com Approve ativo, e para**. A **aprovação** dessa revisão autoriza tudo o que segue, **sem nova pergunta e sem segunda revisão**: commit, `git push`, abertura do PR, **merge (squash) na `main`**, **remoção da branch** (remota e local) e atualização da `main` local. A IA não pergunta "posso mergear?" nem "posso remover as branches?". Comentários são aplicados e a revisão é reaberta se a mudança for relevante. Se depois da aprovação o conteúdo mudar (novo commit, conflito de rebase), a revisão é refeita. O PR não é revisado pela URL: o GitHub não deixa o autor aprovar o próprio PR, e o desenvolvimento é solo; aprovação no GitHub **não** é exigida e a proteção da `main` exige só o CI verde.
- **Limites que continuam valendo:** a IA **nunca faz force push**, nunca faz commit nem push direto na `main` (só por PR) e **só segue com o que passou pela revisão aprovada no Plannotator** (sem aprovação, não faz commit, push, PR nem merge). Quando houver CI, o merge só acontece com o CI verde. Mudança que caia na lista da seção 10 continua exigindo a decisão específica do Dênis sobre o assunto (não sobre o merge). Ao final, a IA relata o que fez (commit, PR, merge).
- **Ao iniciar o trabalho, depois de ler este arquivo:** a IA **atualiza a `main`** (`git checkout main` e `git pull`) e confere `git status`, para começar com o projeto atualizado. Se houver alteração local não commitada, **não descarta**: avisa o Dênis.
- Revisão de documento (spec, plano, ADR) segue o fluxo da seção 5; revisão de código segue este fluxo. As duas mantêm `PLANNOTATOR_SHARE=disabled`.
- Dinheiro em `BigDecimal` ou centavos (`Long`); fuso `America/Sao_Paulo` explícito; nunca `LocalDateTime` sem fuso para instantes.
- Quantidades do algoritmo de troca em `BigDecimal` ou inteiros em unidade fixa, com regras de arredondamento escritas na spec (ADR-0006), nunca `Double`.
- **Migração Flyway já aplicada é imutável**; correção vem em nova migração.
- Decisões novas de arquitetura viram **ADR** em `docs/adr/`.
- **Idioma (regra firme):**
  - **Português:** specs, ADRs, roadmap, README e demais documentação, **mensagens de commit** (a descrição; o prefixo do tipo fica em inglês, ex.: `feat: cria cadastro de usuário`) e **nomes de branch** (sem acentos, ex.: `feat/e1-isolamento-por-usuario`).
  - **Inglês:** todo o código e a estrutura: pastas e arquivos, pacotes, classes, funções, variáveis, testes, comentários, tabelas e colunas (`snake_case`), migrações, endpoints e OpenAPI, enums, logs, códigos de erro, recursos de infra.
  - O que o usuário vê (textos da UI e mensagens de erro exibidas) é **pt-BR via i18n**, com chaves em inglês.
- Termos de domínio seguem o glossário da seção 12; termo novo entra no glossário antes de ser usado.

## 10. A IA deve parar e perguntar antes de

- Alterar migração já aplicada, configuração de segurança, RLS ou autenticação.
- Adicionar ou trocar dependência, imagem base, provider de infra ou serviço AWS.
- Fazer deploy, `apply` de infra ou qualquer ação que gere custo.
- Criar o repositório, conectar o remoto, fazer commit, push ou merge sem a revisão aprovada no Plannotator (com a revisão única aprovada, a IA segue direto até o merge, sem nova pergunta; ver seção 9).
- Enviar dado a serviço externo (LLM, OCR, e-mail, push, WhatsApp, gateway de pagamento).
- Criar ou alterar uma fila, ou colocar dado de saúde numa mensagem.
- Segmentar comunicação por qualquer dado de saúde.
- Violar uma regra deste arquivo ("só desta vez") ou uma trava de produto.

## 11. Definição de pronto

Spec aprovada e atualizada • testes unitários e de integração verdes • cobertura ≥ meta • ArchUnit e Modulith verdes • scan sem Alta ou Crítica • teste de isolamento entre usuários (se houver dado novo) • consumidor idempotente testado (se houver fila) • casos de aceitação do algoritmo de troca verdes (se mexeu nele) • OpenAPI atualizado • sem dado de saúde em logs, traces ou mensagens • ADR criada se houve decisão de arquitetura • nenhum identificador em português no código.

## 12. Glossário de domínio (português → inglês)

Use sempre o termo em inglês no código. Termos novos entram aqui antes de serem usados.

| Português | Código (inglês) |
|---|---|
| usuário / cliente | `user` |
| anamnese | `anamnesis` |
| alergia, intolerância, restrição | `allergy`, `intolerance`, `restriction` |
| treino | `workout` |
| exercício | `exercise` |
| grupo de equivalência (exercício) | `exerciseGroup` |
| substituição de exercício | `exerciseSubstitution` |
| equipamento | `equipment` |
| grupo muscular | `muscleGroup` |
| padrão de movimento | `movementPattern` |
| cardápio | `mealPlan` |
| refeição | `meal` |
| alimento | `food` |
| porção | `portion` |
| troca (de alimento) | `substitution` |
| "comi fora do plano" | `offPlanMeal` |
| lista de compras | `shoppingList` |
| tolerância calórica (±10%) | `calorieTolerance` |
| piso de proteína | `proteinFloor` |
| suplemento | `supplement` |
| exame | `exam` |
| tipo de exame | `examType` |
| arquivo do exame | `examDocument` |
| marcador | `marker` |
| faixa de referência | `referenceRange` |
| consentimento | `consent` |
| política / termo | `policy` |
| check-in | `checkIn` |
| desafio | `challenge` |
| missão | `mission` |
| medalha | `badge` |
| comunidade | `community` |
| amigo | `friend` |
| post | `post` |
| post promocional | `promotionalPost` |
| assinatura | `subscription` |
| plano (de assinatura) | `plan` |
| aluno grátis | `complimentaryAccess` |
| vitalício | `lifetimeAccess` |
| tarefa assíncrona | `task` |
| auditoria | `auditTrail` |
| dia livre / pausa | `restDay` |
| contexto de acesso (quem e com qual escopo a transação roda) | `accessContext` |
| escopo de acesso (`user`, `admin`, `system`) | `accessScope` |
| identidade (quem chama, vinda do token) | `identity` |
