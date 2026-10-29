# Plano: Etapa 0, decisões e provas técnicas

- **Status:** Aprovada (29/10/2026)
- **Spec:** `specs/platform/stage-0-proofs/spec.md` (Aprovada em 29/10/2026)

## 1. Abordagem

Esta etapa não entrega produto: entrega **fatos e decisões**. O plano organiza as provas P1 a P10 em ordem de dependência, define onde fica cada artefato e o formato do registro de cada prova. Protótipos são descartáveis e não seguem as regras de produção (cobertura, Clean Architecture), mas seguem as regras de segurança: nada de segredo, dado real ou dado de terceiros no repositório.

## 2. Estrutura de artefatos

```
prototypes/                  protótipos descartáveis (sem build de produção, sem CI de cobertura)
  sync/                      P1: app React Native mínimo + servidor de teste
  swap/                      P2: algoritmo em Kotlin + casos de aceitação
  exam-extraction/           P3: scripts de OCR e LLM, gabaritos sem dado pessoal
  queues/                    P4: produtor, worker, DLQ contra emulador
  health-devices/            P6: leitura de dados de saúde no celular
docs/stage-0/                um registro por prova: p1-sincronizacao.md ... p10-pwa.md
docs/adr/                    ADRs novas ou atualizadas (0005, 0004, 0007 e autenticação)
local/                       ignorado pelo git: exames reais, saídas brutas, chaves
```

- `prototypes/` ganha um `README.md` dizendo que o conteúdo é descartável e não é referência de qualidade.
- Exames reais e saídas brutas de IA ficam **só em `local/`** (já no `.gitignore`). No repositório entram apenas métricas agregadas e gabaritos sem dado pessoal.
- Mudar `.gitignore` ou criar pasta nova de topo não é necessário além de `prototypes/` e `docs/stage-0/`.

## 3. Formato do registro de prova

Cada `docs/stage-0/<prova>.md` segue o mesmo modelo, em português:
1. **Pergunta** que a prova responde.
2. **Método:** o que foi feito, versões e configuração (sem segredos).
3. **Resultados:** números e observações; tabela quando houver comparação.
4. **Decisão** e o que ela muda (ADR, roadmap, arquitetura).
5. **Limites:** o que a prova não cobre (por exemplo, amostra pequena).
6. **Pendências** abertas.

O registro entra no mesmo PR da decisão. Decisão de arquitetura vira ADR, revisada no Plannotator.

## 4. Ordem e dependências

| Ordem | Prova | Depende de | Observação |
|---|---|---|---|
| 1 | P10 Escopo do PWA | nada | Decisão de conversa; destrava P1 e P7 |
| 1 | P8 Pendências jurídica e científica | nada | A IA monta as listas de perguntas; o Dênis conduz quando puder |
| 1 | P9 Gateway de pagamento | nada | Pesquisa; resultado no Notion |
| 2 | P4 Filas e emulador | nada | Barata e independente |
| 2 | P2 Algoritmo de troca | catálogo de alimentos de teste | Casos marcados "não validados" |
| 3 | P1 Sincronização offline | P10 | A mais cara; define o formato do protocolo no backend |
| 3 | P3 Reconhecimento e extração | exames do Dênis em `local/`; autorização de provedor | Antes de enviar dado real, o Dênis autoriza provedor e região |
| 4 | P6 Wearables e bioimpedância | P1 (projeto RN pronto) | Só pesquisa e dados simulados; aparelho adiado |
| 5 | P7 Telas, API e autenticação | P1, P10 | Baixa fidelidade; provedor de identidade com critérios |
| 6 | P5 Orçamento de latência | P1, P2, P4 | Fecha a etapa; tabela de metas em `docs/architecture.md` |

As provas de mesma ordem não dependem entre si. P8 e P9 correm em paralelo ao resto, sem bloquear.

## 5. Desenho por prova

- **P1:** o mesmo conjunto de cenários nas três opções: treino e refeição offline, edição de perfil em dois aparelhos, mutação duplicada, sessão expirada, 10 mil registros. Servidor de teste mínimo (Kotlin, para refletir o esforço real do protocolo no backend). Resultado: tabela comparativa e ADR-0005 atualizada com a ferramenta escolhida.
- **P2:** módulo Kotlin isolado, com `BigDecimal` e regras de arredondamento escritas. Os 20 casos ficam em um arquivo de dados (JSON ou CSV) versionado, usados por testes de aceitação. Latência medida com carga sintética de catálogo. Resultado: casos e regras prontos para a Etapa 4, com status de validação "pendente".
- **P3:** pipeline de script que lê um documento, classifica o tipo e extrai campos com saída validada por esquema; roda para cada provedor candidato (OCR e LLM). Compara com gabarito feito à mão. Inclui um documento com texto de injeção de prompt. Custo por exame e região de processamento anotados. Dados reais só em `local/`.
- **P4:** produtor e worker falando com ElasticMQ e com LocalStack fixado; cenários de duplicidade, falha com nova tentativa e DLQ. Um teste curto de fumaça no SQS real só depois de combinar o custo, com `destroy` ao final.
- **P5:** metas por tipo de operação a partir dos números de P1, P2 e P4; regra "passou da meta vira assíncrono".
- **P6:** matriz de dados legíveis em Apple Health e Health Connect, permissões e bibliotecas; teste com dados simulados em aparelho ou simulador. Aparelho de bioimpedância adiado.
- **P7:** fluxos de tela em baixa fidelidade (ferramenta a combinar com o Dênis), convenções de API e comparação de provedores de identidade.
- **P10:** decisão de papel do PWA registrada no roadmap e no `docs/architecture.md`.

## 6. Pontos que exigem aprovação antes de executar

Conforme o `CLAUDE.md`, a IA pára e pergunta antes de:
- enviar qualquer exame, mesmo do Dênis, a um provedor de IA ou OCR externo (provedor e região informados);
- criar conta, fila ou qualquer recurso na AWS (gera custo);
- adicionar dependência nos protótipos;
- escolher ferramenta ou provedor que vira ADR.

## 7. Riscos do plano

| Risco | Mitigação |
|---|---|
| Amostra pequena dá falsa confiança na extração | Registrar o limite; repetir a medição na Etapa 3 com mais exames |
| Protótipo vira produção sem querer | `prototypes/` separado, README de aviso, não entra no build |
| Exame real vazar no repositório público | Só em `local/` (ignorado); revisão de diff antes de cada commit; secret scanning |
| P1 estoura o tempo | Limitar a três cenários essenciais primeiro; cenários extras só se os três não decidirem |
| Decisão sem parceiro ou advogado | Pendência registrada; produto restrito ao Dênis |

## 8. Saídas esperadas

ADR-0005 atualizada (sincronização), ADR-0004 atualizada (emulador de SQS), ADR de IA e OCR (provedor) e ADR de autenticação; metas de latência no `docs/architecture.md`; escopo do PWA no roadmap; registros em `docs/stage-0/`; casos de aceitação da troca prontos para a Etapa 4.

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
