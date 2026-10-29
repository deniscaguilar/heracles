# ADR-0007: IA, OCR e dado de saúde (aigateway)

- **Status:** Aprovada
- **Data:** 2026-10-29
- **Origem:** roadmap v5, Etapa 3 e travas de segurança (seção 7)

## Contexto

O app usa IA para reconhecer o tipo de exame, extrair dados (PDF e imagem), interpretar a evolução e, depois, sugerir alimentos a partir de foto. Exame é **dado sensível de saúde** (LGPD, arts. 11 a 13), e enviá-lo a um LLM ou serviço de OCR é enviar dado sensível a terceiro, possivelmente fora do Brasil. Documentos enviados pelo usuário também podem conter texto malicioso feito para manipular o modelo.

## Decisão

**Acesso único pelo módulo `aigateway`**, atrás dos ports `LlmClient` e `OcrClient`, cada um com adaptador real e adaptador local falso. Nenhum outro módulo fala com provedor de IA.

**Condições para qualquer envio** (o gateway recusa se faltar uma):
- **Consentimento específico e vigente** do usuário para processar exame com IA externa (ADR-0003 e Etapa 2). Revogou, para; a digitação manual continua.
- **Provedor aprovado**, com contrato que **proíba treino com os dados e reter além do necessário**, e com a **região de processamento conhecida**. A comparação de provedores (precisão, custo e onde processam) é da Etapa 0.
- **Revisão jurídica** de transferência internacional e de base legal, antes de dado real.

**Minimização.** O gateway remove identificadores diretos (nome, CPF, endereço, número de pedido) do texto antes do LLM, sempre que possível, e envia só o necessário para a tarefa. Quando o próprio arquivo de imagem contém identificadores, o risco é avaliado na Etapa 0 e tratado por mascaramento da área de cabeçalho ou por escolha do provedor.

**Documento é dado, nunca instrução.**
- O texto extraído entra no prompt como conteúdo delimitado, com instrução fixa de que ele não contém ordens.
- A saída do modelo é **estruturada e validada por esquema** (campos, tipos, unidades, valores plausíveis); resposta fora do esquema é descartada.
- O modelo não executa ferramentas nem ações: só devolve dados.

**IA informa e compara; não diagnostica nem prescreve.** Os prompts e uma checagem de saída impedem linguagem de diagnóstico ou de conduta. Valor fora da referência leva ao aviso "procure um médico", mais forte quanto mais distante da faixa. Os textos fixos são revisados pelo parceiro científico e pelo jurídico.

**Conferência obrigatória.** Nada extraído é salvo sem o usuário revisar; o arquivo original é sempre preservado; campos de baixa confiança são destacados; o tipo de exame reconhecido é confirmado pelo usuário.

**Fluxo assíncrono.** Reconhecimento, extração e interpretação rodam em fila (ADR-0004), com a mensagem levando só identificadores.

**Registro e custo.** O gateway registra por chamada: usuário (identificador), tipo de tarefa, provedor, modelo, tokens ou páginas, custo e resultado, **sem o conteúdo**. Há limite de uso por usuário e alarme de custo.

**Chave de desligamento.** Desligar a IA por usuário (revogação) ou globalmente (incidente) não quebra o app.

## Alternativas

- **Chamar o provedor diretamente de cada módulo:** impossível de auditar e de anonimizar de forma consistente.
- **OCR e modelo rodando por conta própria:** controla melhor o dado, mas custa operação e qualidade; pode voltar se o contrato com provedores não for aceitável.
- **Sem IA:** descarta um diferencial pedido para o produto.

## Consequências

- Um ponto único para consentimento, minimização, auditoria e custo.
- Cada provedor novo é uma decisão de risco e exige parar e perguntar (CLAUDE.md, seção 10).
- A precisão da extração é medida antes de confiar nela, e a conferência manual continua como proteção permanente.

## Gatilhos para reavaliar

Provedor que não aceite as condições do contrato, precisão abaixo do necessário, custo por exame acima do orçado ou mudança regulatória sobre IA e dado de saúde.

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
