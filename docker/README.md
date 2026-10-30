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

## Imagem do backend

`backend/Dockerfile` (multi-stage, bases com digest, usuário `10001` sem privilégio). O perfil vem de `SPRING_PROFILES_ACTIVE` (`api`, `worker` ou `all`); a mesma imagem serve aos três.

## Atualizar um digest

`docker pull <imagem:tag>` e `docker image inspect <imagem:tag> --format '{{index .RepoDigests 0}}'`; troque o digest no `compose.yml` (ou no `Dockerfile`). O Dependabot cobre a imagem do `Dockerfile` (tarefa E1-S1.10).
