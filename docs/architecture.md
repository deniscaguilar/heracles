# Arquitetura

**Status:** Aprovada
**Base:** `docs/roadmap.md` (v5) e ADRs 0001 a 0007. As regras operacionais estão em `../CLAUDE.md`.

> O mapa de dependências da seção 4 é uma **proposta** a validar. Depois de aprovado, vira regra de ArchUnit e de `Modulith.verify()` no CI: o build falha se um módulo usar outro fora do mapa.

## 1. Visão geral

Monolito modular em Kotlin + Spring Boot, com Clean Architecture dentro de cada módulo (ADR-0001). Um artefato com perfis `api` e `worker` (no início, `all`). Um PostgreSQL com um schema por módulo e isolamento por usuário por Row-Level Security (ADR-0003). Filas SQS para o que é assíncrono (ADR-0004).

```
 App iOS/Android (React Native)  ┐
 App web (React: PWA + /admin)   ┘──HTTPS──►  Caddy  ──►  backend (api + worker)  ──►  RDS PostgreSQL
                                                            │   │   │   │
                                                            │   │   │   └──► SQS (filas + DLQ)
   App ──URL pré-assinada──► S3 (exames, mídia) ◄───────────┘   │   └──────► provedor OIDC
                                                                └──────────► aigateway ──► OCR e LLM (externos)
                                                            OTLP ──► coletor ──► observabilidade
```

Local: o mesmo desenho com Postgres, emulador de SQS e S3, OIDC mock e observabilidade no Docker Compose.

## 2. Clientes e canais

| Canal | Onde vive | Quem usa |
|---|---|---|
| App iOS e Android | `mobile/` (React Native) | Usuário |
| App web, área do cliente (PWA) | `web/` (React), service worker só para o shell, sem offline | Usuário |
| App web, backoffice | `web/` (mesma base React), rotas `/admin` | `ADMIN` e `MODERATOR` |

Os dois clientes consomem o mesmo contrato OpenAPI versionado, com cliente TypeScript gerado em `packages/`. O backoffice **não é um módulo**: cada módulo expõe os seus endpoints administrativos em `adapter/in/web`, sob `/admin/<módulo>`, protegidos pelo `Authorizer` do `platform`.

## 3. Módulos

Nome de uma palavra: é o nome do pacote em `com.heracles.<module>`, da pasta da spec em `specs/<module>/` e do schema do Postgres.

| Módulo | Responsabilidade | Etapa |
|---|---|---|
| `platform` | Identidade e sessão, perfis e permissões, auditoria, infraestrutura de tarefas assíncronas (outbox, registro de tarefas, idempotência), armazenamento de arquivos, relógio | 1 |
| `consent` | Políticas e termos versionados, aceite, revogação, consulta "o usuário consentiu em X?" | 2 |
| `profile` | Anamnese, objetivo, nível, restrições, alergias e intolerâncias, medidas corporais e dados de aparelhos e wearables | 2 |
| `exams` | Tipos de exame, upload, reconhecimento e extração, marcadores, réguas e comparativos, interpretação, relatório em PDF, lembrete de refazer | 3 |
| `nutrition` | Banco de alimentos, receitas, cardápio, troca de alimento e "comi fora do plano" (ADR-0006), lista de compras, sugestão de suplemento | 4 |
| `training` | Banco de exercícios, geração de treino, **substituição de exercício**, registro de treino | 4 |
| `billing` | Planos, assinaturas, periodicidades, concessões (aluno grátis e vitalício), gateway de pagamento atrás de um port, webhooks | 6 |
| `engagement` | Check-ins, desafios, missões, medalhas, regras de gamificação | 7 |
| `community` | Perfil público, amizades, posts, comunidades, posts promocionais, feed, moderação | 9 |
| `notifications` | Canais (push, WhatsApp e e-mail atrás de um port), modelos, preferências, entrega respeitando consentimento | 2 em diante |
| `aigateway` | Acesso único a OCR e LLM: consentimento, minimização, esquema de saída, custo e registro de uso (ADR-0007) | 3 |

O `platform` guarda o que é transversal; **não existe módulo `core`, `common` ou `shared`**. Código sem dono vai para o módulo dono do conceito, e a arquitetura é revista se não houver dono.

## 4. Dependências permitidas

Uma seta `A → B` significa que `A` pode usar a `api` pública de `B`. Qualquer outra dependência é proibida e quebra o build. **Não pode haver ciclos.**

| Módulo | Pode depender de |
|---|---|
| `platform` | (nenhum) |
| `consent` | `platform` |
| `profile` | `platform` |
| `billing` | `platform` |
| `aigateway` | `platform`, `consent` |
| `notifications` | `platform`, `consent` |
| `exams` | `platform`, `consent`, `profile`, `aigateway` |
| `training` | `platform`, `profile`, `billing` |
| `nutrition` | `platform`, `profile`, `billing` |
| `engagement` | `platform`, `profile` |
| `community` | `platform`, `consent` |

**Regras de comunicação**
- **Consulta síncrona** (preciso de uma resposta para continuar): pela `api` do módulo dono, dentro do mapa acima.
- **Reação a mudança** (aconteceu algo, quem se importa reage): por **eventos de domínio** publicados pelo módulo dono. Quem consome não conhece quem publica, então **eventos não criam dependência** no mapa. Por isso `engagement`, `notifications`, `training` e `nutrition` reagem a eventos de outros módulos sem depender deles.
- Eventos de domínio passam pelo outbox do `platform` e, quando o consumidor é assíncrono, por SQS (ADR-0004). A mensagem leva só identificadores.
- Módulos de base (`platform`, `consent`, `profile`, `billing`) não conhecem seus consumidores.
- Cada módulo só lê as **tabelas do próprio schema**. Sem join nem FK entre schemas; referências a outros módulos guardam só o identificador.

**Por que estas setas**
- `training` e `nutrition` dependem de `billing` para saber se o usuário tem o plano de conteúdo correspondente (Treino, Nutrição ou ambos) e de `profile` para restrições, objetivo e nível.
- `exams` depende de `profile` (sexo e idade para faixas de referência), de `consent` (consentimento de IA) e de `aigateway` (extração e interpretação).
- `aigateway` depende de `consent` porque é ele quem recusa o envio sem consentimento específico vigente.
- `engagement` não depende de treino, nutrição nem exames: reage aos eventos deles e publica `CheckInSubmitted`, que `training` e `nutrition` consomem para ajustar o plano. O plano continua sendo propriedade de quem o gera, e os pisos de segurança ficam onde o cálculo acontece.
- `community` depende de `consent` (compartilhar conteúdo e receber comunicação promocional) e nada de módulos de saúde.

## 5. Catálogo inicial de eventos

Proposta; cada spec detalha o conteúdo (sempre identificadores, nunca dado de saúde).

| Evento | Publicado por | Consumido por |
|---|---|---|
| `ConsentAccepted`, `ConsentRevoked` | `consent` | `notifications` (confirmação), `exams` e `aigateway` (interromper IA), `community` |
| `ExamUploaded` | `exams` | worker do próprio `exams` (reconhecimento e extração) |
| `ExamProcessed` | `exams` | `notifications`, `engagement` |
| `ExamReminderDue` | `exams` | `notifications` |
| `WorkoutCompleted` | `training` | `engagement` |
| `MealLogged`, `OffPlanMealRecorded` | `nutrition` | `engagement` |
| `CheckInSubmitted` | `engagement` | `training`, `nutrition` (ajustar plano), `notifications` |
| `BadgeEarned`, `MissionCompleted` | `engagement` | `notifications`, `community` (se o usuário optou por mostrar) |
| `SubscriptionChanged`, `AccessGranted`, `PaymentFailed` | `billing` | `notifications`, `training`, `nutrition` |
| `PromotionalPostPublished` | `community` | `notifications` (push só a quem consentiu) |
| `PostReported` | `community` | fila de moderação do backoffice |

## 6. Dono dos dados e classe de isolamento

Cada tabela cai em uma classe de isolamento (ADR-0003):

| Classe | Regra | Exemplos |
|---|---|---|
| **Saúde** | Só o próprio usuário lê; nenhum escopo, inclusive `admin` e `system`, abre fora do contexto do dono | anamnese, exames, marcadores, medidas, check-ins, refeições registradas |
| **Compartilhada** | Políticas de visibilidade por amizade, comunidade e bloqueio | posts, comunidades, amizades, posts promocionais |
| **Catálogo** | Leitura por qualquer usuário autenticado; escrita só `admin` | exercícios, alimentos, receitas, tipos de exame, missões e medalhas |
| **Operacional** | Do próprio usuário ou de operação, conforme a spec | assinaturas, tarefas assíncronas, preferências de notificação |

| Módulo | Principais dados | Classe |
|---|---|---|
| `platform` | usuário, vínculo com o provedor de identidade, perfis, auditoria, tarefas e mensagens processadas | operacional |
| `consent` | políticas, versões, aceites, revogações | operacional |
| `profile` | anamnese, restrições, alergias, medidas | saúde |
| `exams` | exames, arquivos, marcadores, interpretações; catálogo de tipos | saúde; catálogo |
| `nutrition` | cardápios, refeições, trocas, listas; catálogo de alimentos e receitas | saúde; catálogo |
| `training` | treinos, execuções; catálogo de exercícios | saúde; catálogo |
| `engagement` | check-ins, progresso de missões, medalhas; catálogo de missões | saúde; catálogo |
| `billing` | planos, assinaturas, cobranças, concessões | operacional |
| `community` | perfis públicos, posts, comunidades, amizades, denúncias | compartilhada |
| `notifications` | preferências, entregas | operacional |
| `aigateway` | registro de uso (sem conteúdo) | operacional |

## 7. Fluxos principais

**Envio de exame com IA** (assíncrono)
1. O app pede uma URL pré-assinada à API e envia o arquivo direto ao S3; a API registra o exame como `PENDING` e publica `ExamUploaded` (outbox).
2. O worker de `exams` consome, pergunta ao `aigateway` e este confere o consentimento em `consent`, minimiza o conteúdo, chama OCR e LLM e valida a saída contra o esquema do tipo.
3. `exams` grava o resultado como "aguardando conferência" e publica `ExamProcessed`; o app mostra o estado e, ao abrir, pede a conferência ao usuário.
4. Só depois da conferência os marcadores entram no histórico, na régua e no comparativo.

**Troca de alimento** (síncrono, com conexão)
1. O app envia o alimento a trocar e o substituto; `nutrition` calcula quantidade e ajustes do dia, respeitando tolerância de ±10%, pisos e restrições (de `profile`), e devolve as opções com o motivo.
2. Sem conexão, o app guarda uma ação pendente; ao reconectar, o servidor calcula e devolve uma proposta a confirmar.

**Check-in que ajusta o plano** (assíncrono)
1. `engagement` registra o check-in e publica `CheckInSubmitted`.
2. `training` e `nutrition` reagem e propõem ajustes dentro dos pisos de segurança; o app mostra "plano atualizado" quando pronto.

**Post promocional** (assíncrono)
1. O backoffice cria o post em `community`, com audiência (todos, um usuário ou uma comunidade) escolhida sem usar dado de saúde.
2. A entrega ao feed roda em fila; `PromotionalPostPublished` leva a `notifications`, que só envia push a quem aceitou comunicações promocionais.

### 7.1 Orçamento de latência

Proposta inicial do roadmap (p95 de 300 ms para leituras do dia e trocas) conferida com as provas P1, P2 e P4. Os números **medidos** abaixo são de protótipo (sem banco real nem rede móvel); as metas são **orçamentos a validar** na Etapa 1 (com Postgres real) e na Etapa 4.

| Tipo de operação | Meta (p95, no servidor) | Se passar da meta |
|---|---|---|
| Leitura do dia (cardápio, treino) e perfil | 300 ms | investigar; leitura offline no app já cobre o uso sem rede |
| Troca de alimento (resposta com opções) | 300 ms no total; **10 ms** para o cálculo da regra | otimizar a consulta; a troca não vira assíncrona, vira "pendente" só sem conexão (ADR-0006) |
| Escrita de registro de uso (treino, refeição, check-in) | invisível ao usuário: grava local na hora e envia depois | n/a (offline-first) |
| Envio da fila de mutações (`/sync/push`) | 1 s por lote de 500 | reduzir o tamanho do lote |
| Pull incremental (`/sync/pull`) | 500 ms | paginar por cursor |
| Pedir URL pré-assinada de upload de exame | 300 ms | n/a (o arquivo vai direto ao S3) |
| Extração de exame (assíncrona) | a medir na P3 (depende do provedor); estado visível ao usuário | tarefa com status e nova tentativa (ADR-0004) |
| Login e renovação de sessão | 500 ms | depende do provedor de identidade |
| Listas do backoffice | 500 ms | paginação por cursor |

**Regra:** uma operação cujo p95 esperado passa de **2 s** vira assíncrona (`202` e tarefa com status, `docs/api-conventions.md`, seção 6); entre 300 ms e 2 s é otimizada antes de virar assíncrona, exceto a extração de exame, que nasce assíncrona.

**Medido nas provas (protótipos):**

| Medida | Resultado | Fonte |
|---|---|---|
| Cálculo de uma troca | p99 de 0,0015 ms | P2 |
| Gerar opções sobre 1.358 candidatos do mesmo grupo | p99 de 1,2 ms | P2 |
| Enviar 10 mil mutações em lotes de 500 (emulador Android, rede local) | 0,75 s | P1 |
| Baixar 10 mil registros num aparelho novo | 0,22 s | P1 |
| Fila SQS (emuladores): entrega, nova tentativa com espera de 2 s e 4 s, DLQ | funcional; latência fim a fim **não medida** | P4 |

O cálculo da regra ocupa uma fração mínima do orçamento; o tempo real será de banco, rede e serialização, a medir com Postgres real na Etapa 1.

## 8. Segurança e privacidade

- Isolamento por usuário no banco, auditoria append-only e ausência de dado de saúde em log, trace, métrica e mensagem de fila: ADR-0003 e CLAUDE.md, seção 4.
- IA só pelo `aigateway`, com consentimento, minimização e conferência humana: ADR-0007.
- Upload de exame direto ao S3 por URL pré-assinada de curta duração; a criação da URL e o registro do arquivo são auditados (ADR-0002).
- Dado de saúde no aparelho criptografado e apagado ao sair da conta (ADR-0005).

## 9. Estrutura interna de cada módulo

```
com.heracles.<module>/
  api/          fachada pública e eventos (ÚNICO acesso de outros módulos)
  domain/       entidades, value objects, regras (sem Spring, sem JPA)
  application/  casos de uso e ports (in/out)
  adapter/in/   web (inclui /admin/<módulo>), listeners de evento, consumidores de fila
  adapter/out/  persistência e integrações externas
  config/
```

Dependência: `domain` ← `application` ← `adapter` (ADR-0001). Os ports de `platform` e dos módulos de integração: `IdentityProvider`, `FileStorage`, `Clock`, `TaskQueue`, `LlmClient`, `OcrClient`, `PaymentGateway`, `NotificationChannel`.

## 10. Pontos de extração futura

O worker de extração de exame e IA é o candidato mais natural a virar serviço próprio (carga assíncrona, integração externa, escala distinta), seguido de `community` se o volume de feed crescer. Os schemas separados e as `api`s já permitem isso. Critérios em ADR-0001.

## 11. Em aberto

- **Permissões fixas por perfil** (`USER`, `ADMIN`, `MODERATOR`): definidas nas specs de `platform`, `consent` e `community`.
- **Onde fica a lista de planos de conteúdo** consultada por `training` e `nutrition`: a proposta é `billing` expor uma consulta de direito de acesso; a spec decide se há cópia em cache local de leitura.
- ~~Escopo do PWA e seu papel no offline~~: decidido na P10 (`docs/stage-0/p10-pwa.md`).
- **Mecanismo de eventos** (registro de eventos do Spring Modulith como outbox, ou tabela própria): decidido na spec da fundação.
- **Wearables e bioimpedância integrada:** se viáveis, os dados entram em `profile`; a prova está na Etapa 0.

## 12. Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
