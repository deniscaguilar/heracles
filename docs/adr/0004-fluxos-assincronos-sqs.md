# ADR-0004: Fluxos assíncronos com SQS, outbox e idempotência

- **Status:** Aprovada
- **Data:** 2026-10-29
- **Origem:** roadmap v5, seção 6 e Etapa 1

## Contexto

Premissa do produto: o melhor tempo de resposta possível; só aparece na hora o que é relevante para o momento, e o resto é processado em segundo plano com a tela se atualizando sozinha. Várias tarefas são lentas ou dependem de terceiros: reconhecimento e extração de exame por OCR e IA, interpretação, relatório em PDF, notificações, montagem do feed, entrega de posts promocionais, recálculo de plano por check-in, webhooks do gateway de pagamento.

## Decisão

**SQS** para as filas, com **outbox transacional** e **consumidores idempotentes**.

- **Síncrono** é o que o usuário precisa ver agora (ler o dia, trocar alimento, registrar treino, confirmar valores de exame). O restante é assíncrono. A divisão por operação está na seção 6 do roadmap.
- **Outbox:** a gravação no banco e a intenção de publicar acontecem na mesma transação (tabela de outbox, que pode ser o registro de eventos do Spring Modulith); um publicador envia para o SQS e marca como enviado. Evita perder mensagem ou publicar algo que foi desfeito.
- **Entrega ao menos uma vez:** o SQS padrão pode entregar a mesma mensagem duas vezes. Toda tarefa tem um `task_id`; o consumidor registra o que já processou (tabela de tarefas processadas) e ignora duplicatas. Efeitos externos (cobrança, envio) usam chave de idempotência.
- **Falha e nova tentativa:** `maxReceiveCount` por fila, espera crescente entre tentativas, e **DLQ** por fila. Mensagem na DLQ gera alarme e pode ser reprocessada pelo backoffice.
- **Visibilidade:** o tempo de visibilidade é maior que o tempo máximo de processamento da tarefa (e estendido em tarefas longas).
- **Filas:** uma por tipo de carga (extração de exame, notificações, entrega promocional, recálculo de plano, webhooks de pagamento, manutenção), para isolar tempos e prioridades. Fila **FIFO** só onde a ordem importa; o padrão é a fila comum.
- **Conteúdo da mensagem:** apenas identificadores, tipo, versão do esquema e `task_id`. **Nunca dado de saúde.** O worker lê o dado no banco sob `UserScope.runAs(userId)` (ADR-0003). Filas com criptografia em repouso.
- **Estado visível ao usuário:** cada tarefa assíncrona tem registro com status (`PENDING`, `PROCESSING`, `DONE`, `FAILED`) que a API expõe e o app mostra.
- **Atualização da tela:** ao voltar ao primeiro plano, por consulta periódica com espera crescente e por push quando disponível. Sem conexão permanente aberta no MVP.
- **Perfis:** `api` e `worker` no mesmo artefato, juntos no início; podem separar sem reescrita.
- **Observabilidade:** idade da mensagem mais antiga, tamanho da fila, tamanho da DLQ e tempo de processamento por tipo de tarefa; alarmes de idade e de DLQ não vazia.
- **Ambiente local:** **ElasticMQ** (`softwaremill/elasticmq-native`, versão e digest fixados; provado na 1.7.1) como emulador de SQS. Na prova P4 (`docs/stage-0/p4-filas.md`, 29/10/2026) ele e o LocalStack 4.14.0 passaram nos mesmos cenários (entrega, nova tentativa com espera crescente, DLQ, duplicidade), e o ElasticMQ usa cerca de um terço da memória e um décimo do tamanho de imagem, sem risco de token ou licença. A decisão é provisória até o teste de fumaça no SQS real. O emulador de S3 é decidido na spec da fundação. O adaptador real e o local ficam atrás do port `TaskQueue`. O adaptador real e o local ficam atrás do port `TaskQueue`.
- **SNS** só se algum evento precisar chegar a vários consumidores.

## Alternativas

- **Fila em tabela do Postgres com `SKIP LOCKED`** (usada no Sistema da Clínica, ADR-0011 de lá): mais simples, sem serviço novo. Descartada aqui por decisão do Dênis: o volume esperado de exames, feed e entrega promocional e a premissa de latência favorecem uma fila gerenciada.
- **Processamento síncrono:** prende a requisição e perde trabalho em falha.
- **Redis:** fora por enquanto. Entra só se uma medição mostrar um fluxo que deva ser cacheado, com ADR própria.
- **Kafka:** proibido sem aprovação; custo e operação sem necessidade.

## Consequências

- Resposta rápida nas ações do usuário e tolerância a falha nas lentas.
- Complexidade de idempotência, DLQ e estados de tarefa; cobrada por teste de integração (mesma mensagem duas vezes não duplica efeito).
- Custo do SQS é por requisição e baixo no volume de um MVP; conferir a tabela atual de preços antes de ativar.
- Mensagem só com identificadores obriga o worker a ler o banco, o que também mantém o dado sob RLS.

## Gatilhos para reavaliar

Volume que exija ordenação forte, vários consumidores por evento (SNS ou EventBridge), custo do SQS acima do esperado ou latência de fila acima do orçamento definido na Etapa 0.

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
