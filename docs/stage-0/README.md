# Registros da Etapa 0

Um arquivo por prova, com o resultado e a decisão. Spec: `specs/platform/stage-0-proofs/spec.md`. Plano: `plan.md` na mesma pasta.

## Modelo de registro

Nome: `p<N>-<tema>.md` (por exemplo, `p4-filas.md`). Seções, em português:

1. **Pergunta** que a prova responde.
2. **Método:** o que foi feito, versões e configuração (sem segredos).
3. **Resultados:** números e observações; tabela quando houver comparação.
4. **Decisão** e o que ela muda (ADR, roadmap, arquitetura).
5. **Limites:** o que a prova não cobre (por exemplo, amostra pequena).
6. **Pendências** abertas.

## Regras

- Só métricas agregadas e gabaritos sem dado pessoal entram aqui. Exame real e saída bruta de IA ficam em `local/`.
- Informação comercial (preços, propostas de provedor) fica no Notion.
- Decisão de arquitetura vira ADR em `docs/adr/`, revisada no Plannotator.

## Registros

| Prova | Registro | Estado |
|---|---|---|
| P1 Sincronização offline | `p1-sincronizacao.md` | a fazer |
| P2 Algoritmo de troca | `p2-troca.md` | a fazer |
| P3 Reconhecimento e extração de exame | `p3-extracao-exames.md` | a fazer |
| P4 Filas | `p4-filas.md` | cenários rodados; falta fumaça no SQS real |
| P5 Orçamento de latência | tabela em `docs/architecture.md` | a fazer |
| P6 Wearables e bioimpedância | `p6-wearables.md` | a fazer |
| P7 Telas, API e autenticação | `p7-*.md` | a fazer |
| P8 Pendências jurídica e científica | `p8-pendencias.md` | listas prontas; pendências abertas |
| P9 Gateway de pagamento | `p9-gateway.md` | pesquisa feita; decisão na Etapa 6 |
| P10 Escopo do PWA | `p10-pwa.md` | decidido (29/10/2026) |
