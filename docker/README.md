# docker

Ambiente local reproduzível com `docker compose`: PostgreSQL, ElasticMQ (SQS), LocalStack só para S3, servidor OIDC de mentira (`mock-oauth2-server`), observabilidade (`grafana/otel-lgtm`) e o backend. A mesma imagem roda em todos os ambientes; só variáveis mudam (`CLAUDE.md`, seção 8). Imagens fixadas por digest.

## Subir e conferir

```sh
docker compose -f docker/compose.yml up -d --build --wait
docker/check.sh          # confirma cada serviço; sai com código diferente de zero se algum falhar
docker compose -f docker/compose.yml down   # derruba (o volume do banco fica; use -v para apagar)
```

O projeto se chama `heracles-dev` para não colidir com outros ambientes locais. As portas do host também são deslocadas e podem ser trocadas por variáveis:

| Serviço | Porta padrão | Variável |
|---|---|---|
| PostgreSQL | 5433 | `HERACLES_PG_PORT` |
| ElasticMQ (SQS) | 9324 | `HERACLES_SQS_PORT` |
| LocalStack (S3) | 4567 | `HERACLES_S3_PORT` |
| OIDC de mentira | 8082 | `HERACLES_OIDC_PORT` |
| Grafana (LGTM) | 3001 | `HERACLES_GRAFANA_PORT` |
| OTLP gRPC / HTTP | 4327 / 4328 | `HERACLES_OTLP_GRPC_PORT` / `HERACLES_OTLP_HTTP_PORT` |
| Backend | 8080 | `HERACLES_API_PORT` |

As credenciais do `compose.yml` são só do ambiente local.

## Entrar no app web local

1. Suba a pilha (`docker compose up -d --wait`) e o app web (`cd web && npm run dev`, porta 5173; o `/api` é repassado ao backend na 8080, então o navegador só fala com a própria origem).
2. Em `http://localhost:5173`, clique em **Entrar**. O servidor de mentira abre um formulário: digite qualquer nome em **Username** (ele vira o `sub`) e deixe as claims vazias. O primeiro acesso cria o usuário com o perfil `USER`.
3. Para ver o `/admin`, promova esse usuário no banco (o perfil nunca vem do token) e recarregue a página:

```bash
docker compose exec postgres psql -U postgres -d heracles \
  -c "update platform.app_user set role='ADMIN' where sub='maria'"
```

O `oidc-config.json` fixa a audiência `heracles-api` nos tokens; sem ele o servidor de mentira põe só o id do emissor (`default`) no access token e a API responde 401. O `client_id` do app web é `heracles-api` pelo mesmo motivo (o ID token precisa ter o `client_id` como audiência).

## Imagem do backend

`backend/Dockerfile` (multi-stage, bases com digest, usuário `10001` sem privilégio). O perfil vem de `SPRING_PROFILES_ACTIVE` (`api`, `worker` ou `all`); a mesma imagem serve aos três.

## Atualizar um digest

`docker pull <imagem:tag>` e `docker image inspect <imagem:tag> --format '{{index .RepoDigests 0}}'`; troque o digest no `compose.yml` (ou no `Dockerfile`). O Dependabot cobre a imagem do `Dockerfile` (tarefa E1-S1.10).
