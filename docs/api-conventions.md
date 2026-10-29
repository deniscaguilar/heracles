# Convenções da API

> **Status:** Rascunho (T5.2 da Etapa 0). Proposta para revisão no Plannotator; vira regra depois de aprovada. As specs de cada módulo detalham os endpoints; este documento fixa o que é comum a todos.

O contrato é o **OpenAPI**, gerado pelo backend e consumido pelo cliente TypeScript gerado em `packages/` (app mobile e app web). Cada PR que muda um endpoint atualiza o contrato (CLAUDE.md, seção 5).

## 1. Estrutura e versão

- Prefixo `/api/v1`. Endpoints administrativos ficam em `/api/v1/admin/<módulo>`; recursos do próprio usuário em `/api/v1/me/...` (o identificador do usuário nunca vai na URL; vem do token).
- **Versão no caminho.** Mudança que não quebra (campo novo opcional, endpoint novo) entra na mesma versão. Mudança que quebra cria `/api/v2` e a `v1` continua enquanto houver versão do app em uso, porque o app mobile atualiza devagar.
- Recursos no plural e em inglês (`/meal-plans`, `/exams`); verbos de domínio como sub-recurso (`POST /me/meal-plans/{id}/substitutions`).

## 2. Formato

- JSON em UTF-8; campos em **camelCase**; enums em `UPPER_SNAKE_CASE`.
- **Identificadores:** UUID. Entidades criadas offline recebem o UUID **no cliente** (ADR-0005); o servidor o respeita e rejeita duplicata com o mesmo id e conteúdo diferente.
- **Instantes** em ISO-8601 com fuso (`2026-10-29T14:03:00-03:00`); datas sem hora (`2026-10-29`) só para dia civil, sempre em `America/Sao_Paulo`. Nunca `LocalDateTime` sem fuso.
- **Dinheiro** em centavos (`amountCents`, inteiro) mais `currency` (`BRL`).
- **Quantidades decimais** (gramas, calorias, proteína) como **string decimal** (`"210"`, `"1905.7"`), nunca número de ponto flutuante (ADR-0006).
- Campo ausente significa "não informado"; `null` só quando o contrato o define.

## 3. Erros

Formato **Problem Details** (RFC 9457), `Content-Type: application/problem+json`:

```json
{
  "type": "https://heracles.example/problems/validation",
  "title": "Validation failed",
  "status": 422,
  "code": "swap.excluded_ingredient",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "errors": [{ "field": "substituteId", "code": "excluded_ingredient" }]
}
```

- `code` é **estável, em inglês e em `snake.case`**, e é a chave de i18n do cliente (o texto em pt-BR mora no app).
- **Nunca** repetir valores enviados pelo usuário, nem dado de saúde, no corpo do erro (CLAUDE.md, seção 4).
- Códigos HTTP: `400` requisição malformada; `401` sem token ou expirado; `403` sem permissão; `404` não existe (ou é de outro usuário, sem distinguir); `409` conflito de estado; `422` validação de regra; `429` limite de uso (com `Retry-After`); `5xx` falha do servidor.

## 4. Listas e paginação

- Paginação por **cursor**: `?limit=50&cursor=...`; máximo de `limit` é 100. Resposta `{ "items": [...], "nextCursor": "..." }` (`nextCursor` ausente na última página).
- Ordenação e filtros por parâmetros nomeados; ordem padrão estável.
- **Sem dado de saúde na URL ou na query.** Busca por dado sensível usa `POST .../search` com corpo (lição do Sistema da Clínica).

## 5. Idempotência

- **Sincronização offline:** cada mutação leva `mutation_id`; repetir não duplica (ADR-0005). Endpoints `POST /api/v1/sync/push` e `GET /api/v1/sync/pull?since=` seguem o protocolo provado na P1.
- Demais `POST` que criam algo com efeito externo (pagamento, envio de exame) aceitam o cabeçalho **`Idempotency-Key`**; o servidor guarda a resposta por 24 h e devolve a mesma para a mesma chave.

## 6. Operações assíncronas

- Operação que passa do orçamento de latência (`docs/architecture.md`, seção 7.1) responde **`202 Accepted`** com `Location: /api/v1/tasks/{id}`.
- `GET /api/v1/tasks/{id}` devolve `{ id, type, status, createdAt, updatedAt, result? }` com `status` em `PENDING`, `PROCESSING`, `DONE` ou `FAILED` (ADR-0004). O resultado só traz identificadores e o que a tela precisa; dado de saúde segue a regra do módulo dono.

## 7. Autenticação e autorização

- `Authorization: Bearer <JWT>` de um provedor OIDC atrás do port `IdentityProvider`. O servidor é *resource server*.
- Token expirado: `401`; o app renova e reenvia, **sem perder a fila local** (provado na P1, cenário C4).
- Perfis fixos `USER`, `ADMIN` e `MODERATOR`. O escopo do backoffice nunca abre tabela de dado de saúde, e isso vale no backend (ADR-0003).

## 8. Cache, rastreio e limites

- Dado de saúde e de usuário: `Cache-Control: no-store`. Catálogos (alimentos, exercícios, tipos de exame): `ETag` e `Cache-Control` curto.
- Entrada `traceparent` (W3C) e saída `X-Request-Id`; o `traceId` do erro é o mesmo do trace. Só identificadores nos atributos de trace, nunca dado de saúde.
- Limite de tamanho de corpo e de taxa por usuário; `429` com `Retry-After`.

## 9. Compatibilidade com o app mobile

- O app envia `X-App-Version`. O servidor pode responder `426 Upgrade Required` quando a versão do app for antiga demais para um contrato descontinuado; isso nunca apaga dado do aparelho.
- Campos desconhecidos na resposta são ignorados pelo cliente; campos novos obrigatórios exigem nova versão.
- Descontinuar um endpoint: cabeçalhos `Deprecation` e `Sunset`, aviso no registro de mudanças e remoção só depois de a versão do app que o usa sair de uso.

## 10. Em aberto (decididos nas specs)

- Formato exato do cursor (opaco, assinado) e política de rotação do `Idempotency-Key`.
- Limites numéricos de taxa e de tamanho de corpo.
- Política de suporte a versões antigas do app (quantas versões atrás).

## Aprovação

- [ ] Aprovada por: Dênis, via Plannotator
