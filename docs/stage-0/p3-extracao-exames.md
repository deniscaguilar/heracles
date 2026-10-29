# P3: Reconhecimento e extração de exame (resultados)

- **Prova:** P3 da Etapa 0 (`specs/platform/stage-0-proofs/spec.md`); instruções em `p3-provedores.md`
- **Estado:** **só o candidato F (local) foi medido**, em 29/10/2026. Nenhum exame foi enviado a provedor externo. Os candidatos B (Google) e A (AWS) esperam as tarefas T3.7 a T3.9.

## 1. Pergunta

Quanto dá para reconhecer e extrair dos exames **sem enviar nada a ninguém** (regras e OCR locais)? Isso é a linha de base contra a qual os provedores de IA serão comparados.

## 2. Amostra e método

- **5 arquivos** do próprio Dênis, em `local/exams/` (fora do git): 2 laudos de sangue em PDF (31 e 34 páginas, mesmo laboratório e datas diferentes), 2 laudos de **imagem** em PDF (um de tomografia e um de ressonância; **não há ultrassom**) e 1 **foto** de bioimpedância (InBody 370S, inclinada, tirada de celular).
- **Gabaritos** em `local/gabaritos/` (fora do git, porque contêm valores reais de saúde). Os de sangue foram **pré-anotados por uma regra e corrigidos à mão** onde ela falhou (7 marcadores a mais por laudo, mais os valores absolutos do diferencial). A foto e os laudos de imagem foram lidos por inspeção.
- **Linha de base:** `prototypes/exam-extraction/local_baseline.py`, só com Python padrão, `pdftotext -raw` (poppler) e `tesseract -l por` (instalados via Homebrew). Regras por palavra-chave para o tipo; expressões regulares para os campos. **Não foi ajustada aos 5 documentos**: uma única passagem.

## 3. Resultados

| Medida | Resultado |
|---|---|
| Reconhecimento do tipo (sangue, imagem, bioimpedância) | **5 de 5** |
| Laudos de imagem: modalidade, existência da impressão diagnóstica e frases-chave da impressão | **2 de 2**, **2 de 2** e **6 de 6** |
| Laudos de imagem: valor numérico (quando o laudo traz um escore) | 1 de 1 |
| Sangue, laudo 1 (59 marcadores esperados): precisão e recall (nome, valor e unidade) | 67% e 52% |
| Sangue, laudo 2 (64 marcadores esperados): precisão e recall | 49% e 55% |
| Sangue: recall ignorando o nome (só valor e unidade) | 60% e 64% |
| Foto de bioimpedância: campos corretos entre os 7 avaliados | **1 de 7** |

## 4. O que os números mostram

- **Reconhecer o tipo é fácil**, até com regras simples, nos 3 tipos desta amostra.
- **Extrair marcador de sangue por regra é frágil.** O laudo tem várias disposições: nome em duas linhas, percentual e valor absoluto no diferencial, tabelas de referência e notas com números, e nomes colados pelo PDF ("PSATotal"). Resultado: cerca da metade dos marcadores certa e muita extração a mais (títulos, notas e valores de referência lidos como resultado).
- **O OCR local em foto inclinada falha** (1 de 7). Texto ilegível em tabelas pequenas, valores trocados de lugar.
- **A camada de texto dos PDFs ajudou muito**: os PDFs de sangue e de imagem **não precisam de OCR**; só a foto precisa. Isso reduz o custo e o envio de dado: PDFs com texto podem ser lidos sem OCR.
- **Os laudos de imagem são texto narrativo**, não tabela de marcadores. O esquema de extração desses tipos é outro (técnica, análise, impressão, escore).

## 5. Decisão parcial

- A extração **precisa de um modelo de IA** (estruturado por esquema) para sangue e bioimpedância; a linha de base local não serve como solução, **só como referência e como plano B sem envio de dado**.
- Para PDFs com camada de texto, o pipeline pode **extrair o texto localmente** e mandar à IA só o texto (menos dado, sem imagem), se o provedor e a minimização do ADR-0007 permitirem. A confirmar nos candidatos B e A.
- A escolha do provedor continua em aberto (T3.10).

## 6. Limites

- **Amostra pequena (5 arquivos), de um só paciente e de um só laboratório de sangue.** Resultado é indício, não medida de produção; repete-se na Etapa 3 com mais exames e mais laboratórios.
- **Os gabaritos de sangue foram pré-anotados por uma regra parecida com a linha de base**, o que tende a **favorecer** a linha de base; os números reais podem ser piores. Um gabarito totalmente manual é o ideal.
- Sem ultrassom, calorimetria nem SIBO (T3.6 trata dos sintéticos).
- Sem teste de injeção de prompt e sem custo (a linha de base é local).
- A foto é uma só e está inclinada; não representa todas as fotos.

## 7. Pendências

- T3.6: documentos sintéticos de calorimetria e SIBO (e, se possível, ultrassom).
- T3.7: checklist de provedor e **autorização escrita do Dênis** antes de enviar qualquer exame (provedor e região).
- T3.8 e T3.9: rodar B e A com a mesma amostra e comparar com esta linha de base; incluir o documento com injeção de prompt.
- Revisar à mão uma amostra do gabarito de sangue para tirar o viés da pré-anotação.
