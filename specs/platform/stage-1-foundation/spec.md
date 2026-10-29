# Spec: Etapa 1, fundação técnica

- **Status:** Aprovada (29/10/2026)
- **Origem:** `docs/roadmap.md`, Etapa 1; `docs/architecture.md`; ADR-0001 a 0005, 0007 e 0008; `docs/api-conventions.md`; `docs/stage-0/fechamento.md`
- **Módulo:** `platform` (e a infraestrutura transversal); não entrega funcionalidade de produto

## 1. Problema

Nenhuma funcionalidade de produto (cadastro, exames, cardápio, treino, assinatura) pode começar sem o chão que ela usa: um repositório com CI que barra vulnerabilidade, um ambiente local igual ao de produção, identidade, isolamento de dado por usuário imposto pelo banco, auditoria que não pode ser alterada, filas que não perdem nem duplicam efeito e infraestrutura na AWS. Se isso for feito depois, o isolamento e a auditoria viram remendo em cima de dado de saúde. Esta etapa entrega **só a fundação**, provada por testes, e nenhum comportamento visível ao cliente além do login de teste.

## 2. Objetivo

Ao fim da etapa, uma mudança nova passa por um pipeline que a valida (testes, arquitetura, cobertura, vulnerabilidades); o ambiente local sobe com um comando e se parece com a produção; um usuário autenticado só enxerga o que é dele, **mesmo que uma consulta esteja errada**; toda ação relevante deixa rastro imutável sem dado de saúde; um evento gravado no banco chega ao consumidor **uma vez só**, mesmo com falha e reentrega; e existe infraestrutura na AWS definida como código e provada em ambiente de fumaça.

## 3. Requisitos

**RF1. Repositório e CI.** Estrutura de pastas do `CLAUDE.md` (seção 3), build Maven com wrapper, pipeline no GitHub Actions: testes unitários e de integração, ArchUnit e `Modulith.verify()`, portão de cobertura ≥ 85% em `domain` e `application`, scan de dependências (falha com Alta ou Crítica), scan de imagem, SBOM CycloneDX por build, atualização contínua de dependências, proteção da `main` (exige CI verde) e secret scanning com push protection.

**RF2. Ambiente local reproduzível.** `docker compose` sobe PostgreSQL, ElasticMQ (SQS), LocalStack só para S3, servidor OIDC de mentira, coletor e observabilidade, e o backend com a mesma imagem de produção (só variáveis mudam).

**RF3. Identidade e sessão atrás do port `IdentityProvider`.** O backend é *resource server* OIDC: valida o JWT (assinatura, emissor, público, expiração). O usuário é criado no `platform` no primeiro acesso, a partir do `sub`. **Perfis** `USER`, `ADMIN` e `MODERATOR` ficam no banco; o token só traz o `sub` (ADR-0008). Adaptador real (Cognito) e adaptador de teste.

**RF4. Isolamento por usuário no banco (ADR-0003).** Toda tabela com dado de usuário tem `user_id` e RLS ativa (`FORCE`). O usuário e o escopo (`user`, `admin`, `system`) são definidos **por transação** (`app.user_id` e `app.scope`). O papel do banco usado pela aplicação não é superusuário e não ignora RLS. Sem usuário definido, a consulta **não devolve nada** (falha fechada). Tabelas de saúde são legíveis só pelo dono; o escopo `admin` **nunca** as abre.

**RF5. Auditoria append-only.** Registro de quem, quando, o quê e o resultado, imposto por *triggers* que recusam alteração e exclusão. O evento de auditoria aceita **só nomes de campo**, nunca valores; um teste garante que dado de saúde não entra.

**RF6. Observabilidade sem dado de saúde.** A aplicação só fala **OTLP**. Logs, traces e métricas levam só identificadores e atributos de baixa cardinalidade. Um teste procura valores sentinela (CPF, e-mail, texto de saúde) na saída e não os encontra.

**RF7. Erros padronizados e contrato de API versionado.** Erros no formato Problem Details com `code` estável (`docs/api-conventions.md`, seção 3); OpenAPI gerado e versionado; cliente TypeScript gerado em `packages/`; rotas em `/api/v1`.

**RF8. Fluxos assíncronos (ADR-0004).** Publicação de eventos por **outbox transacional**; publicador e *workers* (perfis `api` e `worker`, no início `all`); consumidores **idempotentes** por identificador da tarefa; nova tentativa com espera crescente; DLQ; tabela de tarefas com status (`PENDING`, `PROCESSING`, `DONE`, `FAILED`) exposta por `GET /api/v1/tasks/{id}`; métricas de idade da fila e de profundidade da DLQ; reprocessamento de DLQ para o backoffice (a tela fica para a Etapa 8; aqui só o caso de uso e o endpoint administrativo).

**RF9. Ports com adaptador local ou falso** (`CLAUDE.md`, seção 3, regra 4): `IdentityProvider`, `FileStorage` (S3 e LocalStack), `Clock`, `TaskQueue` (SQS e ElasticMQ), `LlmClient`, `OcrClient`, `PaymentGateway` (mock), `NotificationChannel`. Nenhum adaptador real de IA, pagamento ou notificação nesta etapa.

**RF10. Infraestrutura inicial como código (ADR-0002).** OpenTofu em `sa-east-1`: rede sem NAT, uma instância EC2 ARM com Docker Compose e Caddy, RDS PostgreSQL de zona única, bucket S3 (upload direto por URL pré-assinada), filas SQS com DLQ e alarmes, segredos no SSM. Sem `apply` fora do ambiente de fumaça e sem custo sem autorização.

**RF11. Casca dos clientes (proposta, ver pergunta 2).** App web (`web/`, React) com login por OIDC (PKCE) contra o servidor de mentira, shell com navegação por perfil e a rota `/admin`; app mobile (`mobile/`, React Native) com login e shell; ambos usam o cliente de API gerado. Sem telas de produto.

## 4. Critérios de aceite

**CA1.** *Dado* um PR, *quando* o pipeline roda, *então* falha se: um teste falha, uma regra do ArchUnit ou do Modulith é violada, a cobertura de `domain` e `application` fica abaixo de 85%, existe vulnerabilidade Alta ou Crítica sem exceção válida (com justificativa e data de expiração), ou a imagem tem vulnerabilidade Alta ou Crítica.

**CA2.** *Dado* um build, *quando* termina, *então* gera o SBOM CycloneDX como artefato.

**CA3.** *Dado* um computador novo com Docker, *quando* se roda o comando de subir o ambiente, *então* PostgreSQL, ElasticMQ, S3 local, OIDC de mentira, observabilidade e backend ficam saudáveis, e a documentação diz como.

**CA4.** *Dado* uma requisição sem token, com token expirado, com assinatura inválida ou de outro emissor, *quando* chama uma rota protegida, *então* recebe `401` com o erro padronizado.

**CA5.** *Dado* um token válido de um `sub` novo, *quando* chama `GET /api/v1/me`, *então* o usuário é criado no `platform` com o perfil `USER` e a resposta traz só o identificador e o perfil.

**CA6 (isolamento).** *Dado* dois usuários A e B com registros numa tabela de teste de dado de usuário, *quando* A consulta, *então* só vê os seus; *quando* a consulta tem um erro proposital (sem filtro de usuário), *então* ainda só devolve os de A. *Dado* uma transação sem usuário definido, *então* nenhuma linha volta. *Dado* o escopo `admin`, *então* uma tabela de teste marcada como de saúde continua ilegível.

**CA7 (isolamento obrigatório para tabela nova).** *Dado* qualquer migração que cria tabela com `user_id`, *quando* o pipeline roda, *então* um teste automático falha se a tabela não tiver RLS ativa e forçada e um teste de isolamento entre usuários.

**CA8 (auditoria).** *Dado* uma ação relevante, *quando* ocorre, *então* existe um registro com quem, quando, o quê e o resultado. *Dado* uma tentativa de `UPDATE` ou `DELETE` na tabela de auditoria, *então* o banco recusa. *Dado* um evento de auditoria com um valor no lugar de um nome de campo, *então* o código recusa.

**CA9 (sem dado de saúde na saída).** *Dado* uma requisição com valores sentinela (CPF, e-mail, texto de saúde) no corpo, *quando* é processada com sucesso e com erro, *então* os valores **não** aparecem em log, trace, métrica nem no corpo do erro.

**CA10.** *Dado* um erro de validação, *quando* a API responde, *então* o corpo segue o Problem Details com `code` estável e `traceId` igual ao do trace.

**CA11.** *Dado* o contrato OpenAPI, *quando* o pipeline roda, *então* falha se o contrato gerado difere do versionado sem atualização, e o cliente TypeScript é regenerado e compila.

**CA12 (outbox).** *Dado* uma mudança de estado que publica um evento, *quando* a transação é desfeita, *então* nenhuma mensagem sai; *quando* é confirmada, *então* a mensagem chega à fila, mesmo que o publicador esteja parado no momento e volte depois.

**CA13 (idempotência).** *Dado* a mesma mensagem entregue duas vezes, *quando* o consumidor a trata, *então* o efeito ocorre **uma vez**.

**CA14 (falha e DLQ).** *Dado* uma mensagem que sempre falha, *quando* o consumidor tenta, *então* há novas tentativas com espera crescente e, ao esgotar, a mensagem vai à DLQ e a tarefa fica `FAILED`; o reprocessamento administrativo a devolve à fila e o efeito ocorre uma vez.

**CA15.** *Dado* uma tarefa assíncrona, *quando* o cliente consulta `GET /api/v1/tasks/{id}`, *então* vê o status e só o que a tela precisa, sem dado de saúde.

**CA16.** *Dado* que a idade da fila passa de um limite ou a DLQ não está vazia, *então* a métrica correspondente é emitida e o alarme definido no OpenTofu a captura.

**CA17.** *Dado* cada port, *quando* o ambiente é local ou de teste, *então* existe um adaptador local ou falso e os testes usam só ele, sem rede externa.

**CA18 (infraestrutura).** *Dado* o código OpenTofu, *quando* se roda `plan`, *então* mostra os recursos de RF10 e nada fora deles; *quando* o ambiente de fumaça efêmero é criado (com autorização e custo combinados), *então* o backend sobe, o teste de fumaça passa e o `destroy` remove tudo.

**CA19.** *Dado* um segredo no repositório, *quando* se tenta fazer push, *então* a proteção do GitHub bloqueia; nenhum segredo fica em imagem, em `.env` versionado ou na configuração do pipeline.

**CA20 (casca dos clientes, se aprovada).** *Dado* o app web e o app mobile, *quando* um usuário de teste entra pelo OIDC de mentira, *então* vê o shell conforme o perfil e o `/admin` só aparece para `ADMIN` e `MODERATOR`; uma chamada ao backend com o cliente gerado funciona.

## 5. Decisões propostas (a confirmar nesta revisão)

| Tema | Proposta |
|---|---|
| JDK e versões | JDK 25 (LTS), Spring Boot 4.1.x, Spring Modulith 2.1.x, como no projeto da clínica; **conferir cada versão e a compatibilidade ao gerar o `pom.xml`** |
| Pacote raiz | `com.heracles` (provisório, sem espalhar por configurações externas) |
| Emulador de S3 local | LocalStack 4.14.0 só para S3, fixado por digest |
| Eventos | Tabela de outbox própria no schema do módulo, ou registro de publicação de eventos do Spring Modulith; **decidido no plano**, com teste de CA12 |
| Dependências | Cada dependência de produção é aprovada pelo Dênis no plano (CLAUDE.md, seção 10); a lista sai do `plan.md` |
| Primeira tabela de teste | Tabelas **só de teste** (dado de usuário e de saúde) para provar RLS; não são o modelo de produto |

## 6. Fora de escopo

- Qualquer funcionalidade de produto: consentimento, anamnese, exames, cardápio, treino, assinatura, comunidade, gamificação.
- Adaptador real de IA, de pagamento, de push, de WhatsApp ou de e-mail.
- Telas do backoffice (só os endpoints e os casos de uso administrativos de filas).
- Deploy de produção, domínio, certificado e restauração de backup (Etapa 10); aqui só o ambiente de fumaça efêmero.
- Login real contra o Cognito (fica para a Etapa 2 em produção; a fundação usa o servidor de mentira).
- Sincronização offline no servidor (Etapa 5).

## 7. Perguntas e respostas (aprovação de 29/10/2026)

O Dênis aprovou a spec **sem alterações**, o que fixa as propostas como decisão:

1. **Decisões da seção 5 confirmadas:** JDK 25, Spring Boot 4.1.x, Spring Modulith 2.1.x (conferindo cada versão ao gerar o `pom.xml`), pacote `com.heracles` e LocalStack só para S3.
2. **Casca dos clientes (RF11) entra na Etapa 1**, mínima, para o cliente gerado e o login estarem provados antes da Etapa 2.
3. **Ambiente de fumaça na AWS (CA18) entra na Etapa 1**, no fim, com custo e conta combinados antes (CLAUDE.md, seção 10).

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
