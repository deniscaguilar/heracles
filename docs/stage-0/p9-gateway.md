# P9: Gateway de pagamento

- **Prova:** P9 da Etapa 0 (`specs/platform/stage-0-proofs/spec.md`)
- **Estado:** pesquisa inicial feita em 29/10/2026; **decisão adiada para a Etapa 6**.

## 1. Pergunta

Qual gateway de pagamento atende a assinatura (mensal, trimestral, semestral e anual, com renovação automática e cancelamento só ao fim do período) com o menor custo?

## 2. Onde está o comparativo

No **Notion** (página "Gateway de pagamento: comparativo (P9)", sob Heracles Society). Taxas, propostas e números comerciais ficam lá, e não neste repositório público.

## 3. Resultado

- Foram levantados Asaas, Stripe, Pagar.me, Mercado Pago, Efí e Iugu, por buscas na web.
- **Nenhum número foi confirmado na fonte oficial** e várias fontes se contradizem. O levantamento serve de pista, não de base de decisão.
- Leitura inicial, sem decisão: Asaas e Stripe aparecem como os mais prováveis por terem assinatura e API; os demais ficam como comparação.
- O custo que mais pesa pode não ser a taxa do gateway: **onde a assinatura é vendida** (app, web ou ambos) muda a conta por causa das regras das lojas (a Apple abriu pagamentos alternativos no Brasil por acordo com o Cade; o Google Play não mostrou regra equivalente no Brasil nas fontes encontradas).

## 4. Decisão

**Nenhuma por enquanto.** O gateway fica atrás do port `PaymentGateway` com adaptador mock (ADR-0001, ADR-0004 e roadmap, Etapa 6), então trocar de gateway é trocar de adaptador. A escolha vira ADR na Etapa 6, com taxas conferidas nas páginas oficiais.

## 5. Limites

- Pesquisa por busca na web, sem proposta comercial e sem teste de API.
- Não avalia antifraude, repasse nem conciliação contábil.

## 6. Pendências

- Conferir taxas e recursos nas páginas oficiais dos candidatos (Etapa 6).
- Responder com o jurídico (J6 em `p8-pendencias.md`) onde a assinatura é vendida e como as lojas entram.
- Confirmar o suporte a Pix Automático por gateway e a regra de nova tentativa de cobrança que falha.
