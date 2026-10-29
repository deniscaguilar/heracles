# ADR-0001: Monolito modular com Clean Architecture

- **Status:** Aprovada
- **Data:** 2026-10-29
- **Origem:** roadmap v5, Etapa 1; decisão do Notion de 26/08/2026 (Kotlin + Spring Boot); padrão validado no Sistema da Clínica

## Contexto

Desenvolvedor único com assistente de IA. O produto tem muitas áreas (cadastro e consentimento, exames com IA, motores de treino e cardápio, assinatura, gamificação, comunidade) e o modelo de dados ainda está em descoberta. Microsserviços trariam custo operacional (rede, contratos versionados, tracing distribuído, vários pipelines) sem resolver um problema que já exista.

## Decisão

Um **monolito modular**: um artefato, módulos por contexto de negócio, fronteiras verificadas por Spring Modulith (`verify()`) e ArchUnit. O mesmo artefato roda com perfis `api` e `worker`; no início, ambos juntos (`all`).

- **Linguagem e framework:** Kotlin + Spring Boot (decisão do Notion). JDK 25 LTS, Spring Boot 4.1.x, Spring Modulith 2.1.x e Maven com wrapper são a proposta herdada do Sistema da Clínica; **confirmar as versões ao gerar o `pom.xml`**, seguindo o BOM do Boot.
- **Módulos** (uma palavra, proposta): `platform`, `consent`, `profile`, `exams`, `training`, `nutrition`, `billing`, `engagement`, `community`, `notifications`, `aigateway`. O backoffice é um canal, não um módulo. Sem `core`, `common` ou `shared`.
- **Clean Architecture por módulo:** `api` / `domain` / `application` / `adapter/in` / `adapter/out` / `config`, com dependência para dentro. O domínio não importa Spring, JPA, Jackson nem AWS. Persistência por JDBC ou JPA, escolhida por módulo.
- **Dados:** um PostgreSQL, **um schema por módulo**, uma pasta Flyway por módulo, sem join nem FK entre schemas.
- **Ports** para tudo externo, cada um com adaptador real e adaptador local ou falso: `IdentityProvider`, `FileStorage`, `Clock`, `TaskQueue`, `LlmClient`, `OcrClient`, `PaymentGateway`, `NotificationChannel`.
- **Comunicação:** consulta síncrona pela `api` do módulo dono; reação a mudança por eventos de domínio (ADR-0004).
- **Clientes:** o app mobile (React Native, iOS e Android), o app web (React, uma base: PWA do cliente e backoffice em `/admin`) são os dois clientes, no mesmo repositório, e consomem o contrato OpenAPI versionado. Pacotes TypeScript compartilhados (cliente de API gerado, tipos, design tokens) evitam duplicação entre eles. Alterado em 29/10/2026 pela decisão P10 da Etapa 0 (`docs/stage-0/p10-pwa.md`).
- O mapa de dependências permitidas entre módulos vai para `docs/architecture.md`, que será escrito e revisado em seguida.

## Alternativas

- **Microsserviços:** descartados pelo custo operacional para um desenvolvedor.
- **Monolito sem fronteiras verificadas:** vira monolito comum; o CI precisa cobrar as fronteiras.
- **ECS e Kubernetes com serviços separados** (esboço original do Notion): complexidade sem benefício no estágio atual (ver ADR-0002).

## Consequências

- Operação simples, um pipeline, depuração local fácil, módulos pequenos que cabem no contexto da IA.
- Disciplina de fronteiras cobrada pelo CI.
- Extrair um módulo em serviço fica possível porque schemas e `api`s já são separados.

## Gatilhos para extrair um serviço

Escala distinta, ciclo de release distinto, tecnologia distinta ou isolamento de falha. Candidatos: o worker de IA e extração de exame, e o `community` se o volume de feed crescer.

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
