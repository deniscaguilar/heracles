# ADR-0006: Algoritmo de troca de alimento só no backend

- **Status:** Aprovada
- **Data:** 2026-10-29
- **Origem:** roadmap v5, Etapa 4. Substitui a versão anterior deste ADR ("nos dois lados"). Decisão do Dênis na revisão de 29/10/2026: a regra roda só no backend, no formato híbrido.

## Contexto

A troca de alimento e o "comi fora do plano" recalculam o dia dentro de ±10% das calorias, com piso de proteína. A versão anterior previa o algoritmo no aparelho e no servidor, com casos de teste compartilhados, para permitir recálculo offline. Na revisão ficou claro que o offline pesa mais no **treino** (academia sem sinal) do que na troca, que normalmente acontece na hora de comer, e que **corrigir uma regra de segurança no servidor vale na hora para todos**, enquanto no aparelho depende de o usuário atualizar o app.

## Decisão

- **A regra da troca e do "comi fora do plano" roda só no backend** (Kotlin, módulo `nutrition`). O app, o PWA e o backoffice usam o mesmo serviço.
- **Com conexão, a troca é síncrona:** o app envia o alimento a trocar e o substituto escolhido; o servidor calcula a quantidade, confere o total do dia contra a meta (±10% de calorias), respeita o piso de proteína e o piso calórico seguro, aplica os filtros rígidos de alergia e restrição e devolve as opções com o motivo de cada ajuste. A resposta precisa caber no **orçamento de latência** definido na Etapa 0 (proposta: p95 de 300 ms).
- **Sem conexão, a troca fica como ação pendente** (ADR-0005): o app registra a intenção, mostra "pendente de cálculo" e a envia ao reconectar. O resultado volta como **proposta** que o usuário confirma; se o cardápio do dia mudou nesse intervalo, o servidor recalcula com o estado atual e o app avisa.
- **Offline continua completo** para treino (ler e registrar), leitura do cardápio, check-in e fila de exames.
- **Determinismo:** a função de cálculo é pura (sem relógio, sem acaso, sem acesso a banco ou rede dentro da regra; os dados entram como parâmetros). Quantidades em `BigDecimal` ou inteiros em unidade fixa, com **regras de arredondamento escritas na spec**, nunca `Double`.
- **Versionamento:** o plano guarda a versão do algoritmo e a do banco de alimentos que o geraram, para auditoria e para explicar mudanças.
- **Pisos e restrições dentro do algoritmo:** nenhuma saída leva o dia abaixo do piso calórico e do piso de proteína; alimento que o usuário não pode comer nunca é sugerido.
- **Resultado explicável:** a resposta traz o motivo de cada ajuste para a tela mostrar por que a troca é sugerida.
- **Banco de alimentos em memória** no processo do servidor, recarregado quando o catálogo muda, para manter a latência baixa sem cache externo.
- **Casos reais como testes de aceitação:** os 20 casos reais da Etapa 0, validados pelo parceiro científico, viram testes automatizados do backend. Mudar a regra exige atualizar os casos.

## Alternativas

- **Duas implementações (app em TypeScript e servidor em Kotlin), com casos de teste compartilhados:** permite recálculo offline, mas custa duas bases de código, aritmética em inteiros para evitar divergência entre linguagens e versionamento do banco de alimentos no aparelho; e uma correção de regra de segurança espera a atualização do app. Descartada para a versão inicial.
- **Só no app:** o servidor não valida nem gera planos com a mesma regra; descartada.
- **Código compartilhado entre os dois lados** (Kotlin Multiplatform ou o algoritmo em TypeScript executado no servidor): evita duplicar, mas muda a stack do backend ou adiciona um runtime.

## Consequências

- Uma implementação só, correção de regra imediata, sem divergência entre plataformas, e o PWA ganha a troca sem trabalho extra.
- **A troca depende de conexão.** Sem sinal, o usuário não vê o resultado na hora; a ação pendente atenua, mas não elimina a espera.
- Mais carga no servidor e uma dependência da latência de rede; o orçamento da Etapa 0 e a medição do uso real mostram se é aceitável.
- Simplifica o ADR-0005: não há recálculo nem banco de alimentos completo no aparelho.

## Gatilhos para reavaliar

O uso real mostrar que a troca offline é necessária, a latência da troca ficar acima do orçamento, ou o servidor sofrer com a carga do recálculo. Evoluções possíveis sem mudar a decisão: o servidor **pré-calcular sugestões de troca** de cada refeição ao gerar o cardápio e o app guardá-las para uso offline; ou, se o recálculo offline virar requisito, implementar a regra também no aparelho com casos de teste compartilhados entre as linguagens.

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
