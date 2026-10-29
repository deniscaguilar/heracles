# P3: Provedores de OCR e IA para exames (instruções da prova)

- **Prova:** P3 da Etapa 0 (`specs/platform/stage-0-proofs/spec.md`); ADR-0007
- **Estado:** candidatos levantados em 29/10/2026. **Nenhum exame foi enviado a nenhum provedor.** A escolha do provedor e o ADR-0007 atualizado vêm no fim da prova (T3.10).

## 1. Onde está o comparativo

No **Notion** (página "Provedores de OCR e IA para exames: comparativo (P3)", sob Heracles Society). Preços, propostas e contratos ficam lá, e não neste repositório público.

## 2. Candidatos

| | Candidato | Parte |
|---|---|---|
| A | AWS: Textract + Bedrock com Claude | OCR e LLM |
| B | Google: Document AI + Gemini na Vertex AI (São Paulo) | OCR e LLM |
| C | Anthropic API direta | LLM |
| D | Azure: Document Intelligence + Azure OpenAI | OCR e LLM |
| E | OpenAI API direta | LLM |
| F | **Local** (Tesseract ou PaddleOCR + modelo aberto), sem enviar nada | OCR e LLM |

**Ordem de teste:** F primeiro (linha de base, sem autorização), depois B e A, depois C se for preciso; D e E só se algum dos anteriores ficar fora. Nada aqui foi confirmado em documentação oficial ou contrato; o levantamento é pista.

## 3. Critérios de escolha

1. **Região de processamento** conhecida e, de preferência, no Brasil (transferência internacional é a pendência jurídica J2).
2. **Sem treino e sem retenção** do dado do cliente, por contrato; opção de retenção zero.
3. **Precisão** do reconhecimento do tipo e da extração, medida contra os gabaritos.
4. **Resistência a injeção de prompt** (documento com instrução maliciosa vira dado, saída continua válida no esquema).
5. **Custo** por exame (no Notion).
6. Encaixe na infraestrutura: já estamos na AWS (sa-east-1).

Um provedor sem contrato que proíba treino e retenção é eliminado (spec, P3).

## 4. Amostra

Os exames do Dênis estão em `local/exams/` (ignorado pelo git): 2 PDFs de sangue, 2 PDFs em `others` e 1 imagem JPEG de bioimpedância. O PDF de bioimpedância citado antes é, na verdade, uma imagem. Calorimetria e SIBO não temos: entram como documentos sintéticos (T3.6) ou como lacuna registrada.

**A amostra é pequena (5 arquivos).** O resultado é um indício; a medição se repete na Etapa 3.

## 5. Regras de dado (valem para toda a prova)

- Exame real só em `local/`. No repositório entram apenas métricas agregadas e gabaritos sem dado pessoal (sem nome, CPF, data de nascimento, laboratório identificável nem número de pedido).
- **Nada é enviado a provedor externo sem autorização escrita do Dênis, a cada envio**, informando provedor e região (T3.7).
- **Ler o conteúdo de um exame neste ambiente (com a IA que ajuda no desenvolvimento) também é enviá-lo a um serviço externo.** Por isso a IA não abre os arquivos de `local/exams/` sem autorização do Dênis. O gabarito (T3.5) pode ser feito à mão pelo Dênis, com o modelo da seção 6, ou com a leitura autorizada.
- Saída bruta dos provedores fica em `local/`.

## 6. Instruções de execução

**T3.5: gabaritos.** Para cada arquivo, criar em `local/exams/` um gabarito com: tipo de exame, e para cada marcador o nome, o valor, a unidade e a faixa de referência. Depois gerar a versão para o repositório **sem dado pessoal** (ver seção 5). Modelo:

```
arquivo: <nome local>
tipo: sangue | ultrassom | bioimpedancia | outro
marcadores:
  - nome: <como aparece>
    valor: <número>
    unidade: <unidade>
    referencia: <mín - máx ou texto>
```

**T3.6: sintéticos.** Montar documentos de calorimetria e de teste respirado de SIBO a partir de laudos públicos de exemplo, ou registrar a lacuna.

**T3.7: provedor, região e autorização.** Para cada candidato a testar, preencher: região real de processamento, retenção padrão, retenção zero, treino, DPA, custo e se guarda o arquivo. Depois o Dênis autoriza por escrito, com provedor e região.

**T3.8: pipeline.** Para cada documento e provedor: classificar o tipo, extrair os campos e validar a saída contra um esquema. Rodar F (local) primeiro. Saída em `local/`.

**T3.9: medição.** Acertos do tipo e de cada campo contra o gabarito, custo por exame e região de processamento, mais um documento com texto de injeção de prompt. Registrar só números agregados em `p3-extracao-exames.md`.

**T3.10: decisão.** Recomendar o provedor, definir a meta mínima de precisão e o limiar que dispara a conferência reforçada, e atualizar o ADR-0007.

## 7. Pendências

- Conferir região, retenção e treino de cada candidato em documentação oficial e contrato.
- Autorização escrita do Dênis antes do primeiro envio (T3.7).
- Resposta jurídica sobre transferência internacional (J2, em `p8-pendencias.md`) antes de qualquer uso com dado de terceiros.
