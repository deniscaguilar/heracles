# ADR-0002: Infraestrutura AWS de baixo custo com filas

- **Status:** Aprovada
- **Data:** 2026-10-29
- **Origem:** roadmap v5, Etapa 1 e decisão fechada da seção 8 (EC2 + Compose + RDS + SQS). Substitui o esboço de ECS Fargate do Notion.

## Contexto

Uso próprio primeiro, custo mínimo, mas com dado de saúde real (exames) desde cedo. O Notion previa API Gateway, load balancer, CDN, ECS (depois EKS), S3, SQS, SNS e RDS. Para um MVP com um desenvolvedor, parte disso é complexidade e custo fixo sem ganho. O Sistema da Clínica adotou e validou um desenho enxuto.

## Decisão

| Item | Decisão |
|---|---|
| Região | **sa-east-1** (variável do OpenTofu) |
| Compute | **1 instância EC2 ARM** com Docker Compose: `api` + `worker` no perfil `all` e Caddy para TLS. Dimensionar pelo uso real |
| Banco | **RDS PostgreSQL** pequeno, single-AZ, gp3, backup automático com recuperação a ponto no tempo, criptografia KMS, sub-rede privada, sem acesso público |
| Rede | VPC com a EC2 em sub-rede pública (security group restrito); **sem NAT Gateway** |
| Arquivos | **S3 criptografado** (SSE-KMS). Upload de exame **direto do app para o S3** por URL pré-assinada de curta duração, sem passar pela API |
| Filas | **SQS** com DLQ por fila (ADR-0004), criptografia em repouso; a instância acessa por papel IAM, sem chaves estáticas |
| Segredos | SSM Parameter Store |
| Auth | Provedor OIDC atrás de port; autorização no banco da aplicação |
| IaC | **OpenTofu** em stacks (`foundation`, `data`, `app`, `observability`), state em S3 |
| Deploy | Pipeline separado da infra: CI constrói imagem arm64 e atualiza o serviço; GitHub Actions autentica por OIDC |
| Ambientes | Sem staging fixo: local e produção, mais **ambiente de fumaça efêmero** antes de go-live e de releases arriscadas |
| Fora do desenho inicial | API Gateway, load balancer, CDN, ECS e EKS |

## Consequências

- Custo baixo, mas **sem alta disponibilidade** (EC2 e RDS single-AZ). Aceitável para uso próprio.
- O upload direto ao S3 reduz carga e latência na API; a URL pré-assinada e o registro do arquivo precisam ser auditados.
- Imagens do feed (comunidade) e vídeos, se vierem, pedem CDN; fica para quando houver demanda.
- Custos devem ser conferidos nas tabelas atuais da AWS antes do primeiro `apply`.

## Gatilhos para reavaliar

Abrir o app a mais pessoas, SLA ou exigência de disponibilidade, volume de mídia da comunidade, ou necessidade de escalar o worker de IA separadamente: Multi-AZ, ECS Fargate, load balancer e CDN entram em nova decisão.

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
