# P2: Algoritmo de troca de alimento

- **Prova:** P2 da Etapa 0 (`specs/platform/stage-0-proofs/spec.md`); ADR-0006
- **Estado:** protótipo e 20 casos prontos em 29/10/2026. **Os casos são propostos pela IA e NÃO foram validados por nutricionista** (pendência C3, em `p8-pendencias.md`). Nenhuma regra daqui vai a usuário que não seja o Dênis antes dessa validação.

## 1. Pergunta

A regra de troca (ADR-0006) é implementável de forma determinística, em `BigDecimal`, com os pisos e filtros de segurança, e cabe no orçamento de latência?

## 2. Método

Protótipo descartável em `prototypes/swap/` (Maven, Kotlin 2.3.21, JDK 21 nesta máquina; o projeto real usará o JDK decidido na fundação). Dependências do protótipo, aprovadas pelo Dênis: `kotlin-stdlib`, `kotlin-maven-plugin`, JUnit 5 (6.0.3) e AssertJ (3.27.7).

- `SwapEngine`: função pura, sem relógio, banco ou rede.
- `data/catalog.tsv`: 26 alimentos com valores **ilustrativos** (não são tabela nutricional validada).
- `data/plan.tsv`: dia-base de 14 itens, 1.906,6 kcal e 142,9 g de proteína.
- `data/cases.tsv`: os 20 casos, lidos pelos testes de aceitação.
- Os resultados esperados dos casos foram calculados por uma **implementação de referência independente em Python** (mesmas regras, `Decimal`); o Kotlin bate com ela nos 20 casos.

## 3. Regras (candidatas à spec da Etapa 4)

**Entradas:** plano do dia, item a trocar, substituto, metas (calorias do dia, piso de proteína, piso calórico seguro) e marcadores excluídos pelo usuário.

**Rejeições** (a troca não é oferecida; todos os motivos aplicáveis são devolvidos):
1. `SAME_FOOD`: o substituto é o próprio alimento.
2. `EXCLUDED_TAG`: o substituto tem marcador que o usuário exclui (alergia, intolerância, restrição). Filtro rígido.
3. `DIFFERENT_GROUP`: o substituto é de outro grupo de equivalência.
4. `ABOVE_TOLERANCE` e `BELOW_TOLERANCE`: calorias do dia fora de alvo ± 10% (limites inclusivos).
5. `BELOW_KCAL_FLOOR`: dia abaixo do piso calórico seguro.
6. `BELOW_PROTEIN_FLOOR`: dia abaixo do piso de proteína.

**Cálculo da porção** (tudo em `BigDecimal`, nunca `Double`):
1. Energia do item original = gramas x kcal por 100 g / 100 (exata).
2. Gramas brutas do substituto = energia original x 100 / kcal por 100 g do substituto, 6 casas, `HALF_UP`.
3. Gramas = bruta arredondada ao **passo** do alimento (5 g para a maioria, 50 g para ovo, 10 g para laticínios líquidos, 1 g para azeite), `HALF_UP`.
4. Se passar do máximo, vira o máximo (`PORTION_CAPPED`); se ficar abaixo do mínimo, vira o mínimo (`PORTION_RAISED_TO_MIN`). São **notas**, não rejeições; a tolerância e os pisos decidem depois.
5. Totais do dia são somas exatas; só a saída é arredondada a 1 casa, `HALF_UP`.

**Resposta explicável:** gramas, total do dia, proteína do dia, motivos de rejeição e notas de ajuste.

## 4. Resultados

- **20 casos**, todos conforme o esperado: 10 aceitos e 10 rejeitados, cobrindo: troca equivalente (arroz por purê de batata, batata-doce, mandioca, macarrão), proteínas, laticínios, gorduras, alergia ao amendoim, sem glúten, sem lactose, grupo diferente, mesmo alimento, porção limitada, piso de proteína por pouco, tolerância acima e abaixo, e piso calórico.
- **Testes:** 9 verdes (2 de aceitação, 5 de regra, 2 de latência), `mvn test` em `prototypes/swap/`.
- **Exemplo do roadmap:** 150 g de arroz (192 kcal) viram **210 g** de purê de batata; o dia vai de 1.906,6 para 1.905,7 kcal.
- **Latência só do cálculo** (JVM aquecida, catálogo em memória):

| Operação | p50 | p95 | p99 |
|---|---|---|---|
| Uma troca | 0,0004 ms | 0,0006 ms | 0,0015 ms |
| Gerar opções varrendo 1.358 candidatos do mesmo grupo | 0,465 ms | 0,930 ms | 1,237 ms |

O orçamento proposto é 300 ms no p95. **O cálculo ocupa uma fração mínima disso**; o tempo real será de banco, rede e serialização, a medir na Etapa 4.

## 5. Decisão

- A regra em Kotlin puro, determinística e em `BigDecimal`, **é viável e cabe no orçamento**; o banco de alimentos em memória (ADR-0006) não é gargalo para este volume.
- As regras da seção 3 e os 20 casos seguem como **base da spec da Etapa 4**, sujeitos à validação científica (C1 a C5).

## 6. Limites

- Valores nutricionais **ilustrativos**; pisos e tolerância de teste (1.900 kcal, 120 g de proteína, piso de 1.500 kcal) **não são recomendação**.
- A regra cobre só a troca de um item por outro do mesmo grupo. **Não cobre** "comi fora do plano", recompor o dia com vários itens, ajuste de refeição inteira nem preferências do usuário.
- A tolerância é aplicada ao total do dia depois da troca; se o dia já estava fora, a troca não o corrige (caso C18).
- Medida de latência sem banco, rede, serialização nem concorrência.
- Os 20 casos foram escritos pela IA e conferidos por uma segunda implementação, não por um profissional.

## 7. Pendências

- Dênis revisar os 20 casos (revisão do diff no Plannotator).
- Validação por nutricionista: pisos, tolerância, equivalências, restrições e os próprios casos (C1 a C5 em `p8-pendencias.md`).
- Decidir na spec da Etapa 4 como tratar "comi fora do plano", recomposição do dia e pesos de preferência.
