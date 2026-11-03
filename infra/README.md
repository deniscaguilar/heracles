# infra

Infraestrutura como código (OpenTofu 1.13) para a AWS em `sa-east-1` (ADR-0002): rede sem NAT, EC2 ARM com Docker Compose e Caddy, RDS PostgreSQL, S3, SQS e SSM.

**Nenhum `apply` e nenhum recurso com custo sem autorização do Dênis** (`CLAUDE.md`, seção 10). Nada aqui roda sozinho: o pipeline só faz `fmt`, `validate`, um `plan` offline (sem credenciais) e o scan de configuração.

## Estrutura

```
modules/
  network/   VPC, sub-rede pública (EC2) e duas privadas (RDS), internet gateway, sem NAT; grupos de segurança
  database/  RDS PostgreSQL single-AZ, gp3, KMS, backup com recuperação a ponto no tempo, sem acesso público
  storage/   bucket S3 privado, versionado, SSE-KMS, só TLS, CORS para URL pré-assinada
  queues/    fila de trabalho e DLQ (redrive após 5 entregas), SSE, alarmes (idade da mais antiga, DLQ não vazia) e tópico SNS
  secrets/   SSM Parameter Store (SecureString): senhas do banco, geradas aqui
  compute/   EC2 ARM (IMDSv2, sem SSH, acesso por Session Manager), papel IAM mínimo, Elastic IP, Compose e Caddy no primeiro boot
stack/       raiz que liga os módulos; `environment` = `prod` ou `smoke`
  envs/      `*.tfvars` (sem segredo) e modelos de `*.backend.hcl`
scripts/     check-plan.sh e check_plan.py: o plan offline contra as regras de RF10 (CA18)
```

Um único diretório raiz (`stack/`) serve produção e fumaça: muda `environment` (e o estado, por chave). No fumaça não há proteção contra exclusão no banco e o bucket pode ser esvaziado pelo `destroy`. A ADR-0002 fala em quatro stacks (`foundation`, `data`, `app`, `observability`); com um desenvolvedor e uma instância, um estado só é mais simples, e dividir depois é barato.

## Comandos

```bash
# Validar sem credenciais e sem estado remoto
cd infra/stack && tofu init -backend=false && tofu validate

# O plan offline contra as regras de RF10 (o mesmo do pipeline)
infra/scripts/check-plan.sh smoke   # ou prod
```

`check-plan.sh` reprova com qualquer recurso fora de RF10 (NAT, load balancer, ECS...), instância que não seja ARM, IMDSv2 desligado, banco público ou multi-AZ, porta de entrada que não seja 80, 443 ou 5432 (do grupo da aplicação), bucket sem bloqueio público, fila sem criptografia, alarme de CA16 ausente e segredo que não seja `SecureString`.

## Estado remoto (pendente de autorização)

O `backend "s3" {}` está declarado vazio. O bucket de estado **ainda não existe**: criá-lo é uma ação na conta AWS e fica para depois do OK do Dênis (conta e região). Quando existir, copie `envs/prod.backend.hcl.example` para `envs/prod.backend.hcl` (ignorado pelo git) e rode `tofu init -backend-config=envs/prod.backend.hcl`. O estado guarda as senhas do banco: o bucket tem de ser privado, versionado e cifrado, e o fumaça usa outra chave.

## Dependências

Provedores fixos: `hashicorp/aws` 6.71.0 e `hashicorp/random` 3.8.1, com `.terraform.lock.hcl` versionado (linux_amd64, linux_arm64 e darwin_arm64). O Dependabot acompanha `infra/stack`.

## Antes do primeiro `apply`

- Conferir custos nas tabelas atuais da AWS (instância, RDS, Elastic IP, KMS, CloudWatch).
- Confirmar a versão menor do PostgreSQL disponível em `sa-east-1` (`engine_version = "18"`).
- Definir a imagem do backend (arm64, por digest) e de onde ela é baixada.
- Definir o destino dos alarmes (`alarm_email`); sem ele o tópico existe, mas ninguém recebe.
