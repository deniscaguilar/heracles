# P4: Filas e emulador de SQS

- **Prova:** P4 da Etapa 0 (`specs/platform/stage-0-proofs/spec.md`); ADR-0004
- **Estado:** cenários rodados nos dois emuladores em 29/10/2026. **Teste de fumaça no SQS real (T2.4) ainda não feito** (depende de autorização, por gerar custo e exigir conta AWS).

## 1. Pergunta

Qual emulador de SQS usar no `docker compose` local, e o fluxo produtor, worker, falha, nova tentativa e DLQ se comporta como o SQS espera?

## 2. Método

Protótipo descartável em `prototypes/queues/`: `docker-compose.yml` com os dois emuladores e `run_scenarios.py`, que usa só a biblioteca padrão do Python e o AWS CLI já instalado (nenhuma dependência nova). Mensagens levam só um identificador de tarefa.

| Emulador | Imagem (versão fixada) | Porta local |
|---|---|---|
| ElasticMQ | `softwaremill/elasticmq-native:1.7.1` | 19324 |
| LocalStack (somente SQS) | `localstack/localstack:4.14.0` | 14566 |

O LocalStack ficou na 4.14.0 porque as versões a partir de 2026.03 exigem token (lição do Sistema da Clínica). Os digests das imagens serão fixados quando entrarem no `docker compose` da Etapa 1.

Cenários (`maxReceiveCount=3`, visibilidade de 2 s):
- **C1:** enviar, receber e confirmar; a fila fica vazia.
- **C2:** mensagem que sempre falha; o worker devolve com espera crescente (2 s e 4 s); depois de 3 recebimentos a mensagem vai para a DLQ.
- **C3:** a mesma mensagem enviada duas vezes; o consumidor guarda o identificador da tarefa e o efeito ocorre uma vez.

## 3. Resultados

| Verificação | ElasticMQ | LocalStack |
|---|---|---|
| Entrega e confirmação | OK | OK |
| Fila vazia depois de confirmar | OK | OK |
| 3 tentativas antes da DLQ | OK | OK |
| Espera cresce entre tentativas | OK (2,2 s e 4,2 s) | OK (2,6 s e 4,9 s) |
| Mensagem vai para a DLQ | OK | OK |
| Duas entregas, um efeito | OK | OK |
| Memória em uso | cerca de 44 MiB | cerca de 125 MiB |
| Tamanho da imagem | 94 MB | 1,15 GB |

Observações:
- **Os dois se comportam igual** nos cenários. A primeira rodada falhou nos dois por erro do teste: no SQS a mensagem só vai para a DLQ no **próximo recebimento** depois de estourar `maxReceiveCount`, e o teste parava antes. O corrigido reflete o comportamento real.
- **A idempotência está no consumidor** (C3), não no emulador: o emulador entrega duas vezes e o consumidor descarta a repetição. Vale também no SQS real, que garante entrega ao menos uma vez.
- O LocalStack também emula S3 (necessário no ambiente local); o ElasticMQ só emula SQS.

## 4. Decisão proposta

**ElasticMQ para o SQS local**, fixado por versão e digest:
- empata em comportamento nos cenários da P4;
- usa cerca de um terço da memória e um décimo do tamanho de imagem;
- não tem risco de token ou licença, que já nos custou uma fixação de versão no projeto da clínica.

O **emulador de S3** é uma decisão à parte (CLAUDE.md, seção 8): fica para a spec da fundação, com a opção de manter o LocalStack só para S3. A decisão acima é **provisória até o teste de fumaça no SQS real** (T2.4) confirmar que o comportamento bate.

## 5. Limites

- Só foram testados envio, recebimento, falha, DLQ e duplicidade. Não foram testados filas FIFO, mensagens grandes, lote, long polling prolongado nem alarmes (CloudWatch).
- Os emuladores podem divergir do SQS real em detalhes de tempo; o teste de fumaça (T2.4) serve para isso.
- Sem medição de latência fim a fim (entra na P5).

## 6. Pendências

- **T2.4:** teste de fumaça no SQS real, com `destroy` ao final (pede autorização e conta AWS).
- Escolher o emulador de S3 na spec da fundação.
- Fixar digests das imagens no `docker compose` da Etapa 1.
