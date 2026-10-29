# Spec: Etapa 0, decisões e provas técnicas

- **Status:** Aprovada (29/10/2026)
- **Origem:** `docs/roadmap.md`, Etapa 0; ADR-0002, 0004, 0005, 0006 e 0007
- **Saída desta etapa:** decisões registradas (ADRs novas ou atualizadas) e protótipos descartáveis. **Nenhum código de produção.**

## 1. Problema

Várias decisões da fundação dependem de fatos que ainda não temos: qual ferramenta de sincronização offline aguenta o uso real, se a extração de exame por IA é precisa o bastante, se a troca de alimento cabe no orçamento de latência, se wearables e bioimpedância são viáveis e o que o jurídico permite. Decidir sem prova obriga a refazer arquitetura depois. Esta etapa produz as provas e fecha as decisões antes da Etapa 1.

## 2. Regras da etapa

- **Protótipo é descartável.** Fica em `prototypes/` (fora do build de produção) ou em branch própria; só o resultado e a decisão entram em `docs/`.
- **Repositório público:** nenhum exame real, dado pessoal, chave ou contrato entra no git. Exames de teste ficam fora do repositório (pasta local ignorada pelo git); no repositório vão só métricas agregadas. Preços e propostas comerciais de provedores ficam no Notion.
- **Dado de saúde real em IA externa:** antes de enviar qualquer exame real a um provedor, o Dênis autoriza por escrito no chat, informando provedor e região. Exames de terceiros só com consentimento do titular e anonimizados. Sem isso, a prova usa documentos sintéticos ou do próprio Dênis.
- Cada prova termina em um registro `docs/stage-0/<prova>.md` (método, números, decisão) e, se houver decisão de arquitetura, numa ADR nova ou atualizada.
- Qualquer serviço que gere custo é combinado antes (CLAUDE.md, seção 10).

## 3. Provas e critérios de aceite

### P1. Sincronização offline (ADR-0005)

Comparar Expo SQLite com sync próprio, WatermelonDB e PowerSync num protótipo React Native com um servidor simples.

- **Dado** o mesmo cenário (registrar treino e refeição offline, editar perfil em dois aparelhos, reconectar), **quando** cada opção é exercitada, **então** registramos: resolução de conflito, desempenho com 10 mil registros de uso, suporte a criptografia local com chave no armazenamento seguro, esforço estimado para o protocolo no backend Kotlin e custo de manutenção solo.
- **Dado** a mesma mutação enviada duas vezes, **quando** o servidor a aplica, **então** o efeito é único (idempotência por `mutation_id`).
- **Dado** sessão expirada com treino em andamento, **quando** o usuário volta, **então** o progresso não se perde e nenhum dado fica exposto.
- **Saída:** ferramenta escolhida e ADR-0005 atualizada, removendo o rascunho da escolha.

### P2. Algoritmo de troca (ADR-0006)

- **Dado** 20 casos reais de troca (inclusive o exemplo arroz → purê de batata), **quando** o algoritmo em Kotlin roda, **então** respeita ±10% de calorias, piso de proteína, piso calórico seguro e filtros de alergia e restrição.
- Os casos são escritos como testes de aceitação em arquivo de dados versionado (sem dado pessoal). A validação pelo parceiro científico (nutricionista, ainda não definido) é uma **pendência registrada**: os casos ficam marcados como "propostos pela IA, não validados" até alguém qualificado revisá-los, e **nenhuma regra de troca vai a um usuário que não seja o Dênis antes dessa validação**.
- Regras de arredondamento e unidades fixas documentadas (sem `Double`).
- **Dado** o protótipo com dados de catálogo realistas, **quando** medimos a latência, **então** registramos p50, p95 e p99 e comparamos com a meta.
- **Saída:** casos de aceitação aprovados e regras de arredondamento prontas para a spec da Etapa 4.

### P3. Reconhecimento e extração de exame (ADR-0007)

- **Amostra inicial:** os exames do próprio Dênis (sangue, ultrassom e um PDF de bioimpedância), em PDF e foto, conforme a regra de dado real da seção 2. Calorimetria e teste de SIBO não temos por ora; entram como **documentos sintéticos** (modelos montados a partir de laudos públicos de exemplo) ou ficam como lacuna registrada. A amostra pequena limita a confiança dos números; o resultado é lido como indício e a medição se repete na Etapa 3 com mais exames. Se houver mais de um laboratório, melhor.
- **Dado** a amostra, **quando** rodamos OCR e LLM de pelo menos dois provedores, **então** medimos a precisão do **reconhecimento do tipo** e da **extração** (campo a campo, contra gabarito feito à mão), o custo por exame e **onde o dado é processado**.
- **Dado** um documento com texto malicioso ("ignore as instruções…"), **quando** processado, **então** o conteúdo é tratado como dado e a saída continua válida no esquema.
- **Dado** um provedor sem contrato que proíba treino e retenção, **quando** avaliado, **então** é eliminado.
- **Saída:** provedor recomendado, limiar de confiança que dispara conferência reforçada e lista de formatos que caem na fila de não reconhecidos. A meta mínima de precisão é definida com os primeiros números e registrada nesta spec antes de a prova ser dada como concluída.

### P4. Filas (ADR-0004)

- **Dado** produtor, worker, falha, nova tentativa com espera crescente e DLQ, **quando** exercitados ponta a ponta com SQS, **então** a mesma mensagem duas vezes não duplica o efeito e a mensagem que sempre falha vai à DLQ.
- Comparar emuladores locais (ElasticMQ e LocalStack fixado em versão sem token) quanto à compatibilidade com o SQS real nesses cenários.
- **Saída:** emulador escolhido para o `docker compose` local, registrado no ADR-0004.
- Usar SQS real exige combinar o custo antes; o padrão é o emulador mais um teste curto de fumaça na AWS, com `destroy` ao final.

### P5. Orçamento de latência

- **Dado** a proposta (leituras do dia e trocas até 300 ms no p95), **quando** comparada com P1, P2 e P4, **então** fixamos metas por tipo de operação e a regra "passou da meta, vira assíncrono".
- **Saída:** tabela de metas no `docs/architecture.md`.

### P6. Wearables e bioimpedância

- **Dado** Apple Health e Health Connect, **quando** testados em React Native, **então** registramos quais dados (passos, treino, frequência cardíaca, gordura corporal, massa magra) dá para ler, com quais permissões e quais bibliotecas.
- **Sem aparelho disponível por ora:** a parte de balança Bluetooth ou aparelho profissional fica **adiada** e registrada como pendência. Nesta etapa vale só a pesquisa documental e o teste com dados simulados: quais campos de gordura corporal e massa magra as plataformas de saúde expõem e como um fabricante entregaria a medição. O PDF de bioimpedância do Dênis é testado na P3, não aqui.
- Medir pelo app só com dado de aparelho de terceiros; a pergunta sobre ANVISA vai ao jurídico (P8).
- **Saída:** viável ou não, e em qual etapa entra (7 ou fase seguinte).

### P7. Telas, contrato de API e autenticação

- Fluxos principais de telas do app e do backoffice, em baixa fidelidade, para o Dênis revisar.
- Convenções do contrato de API: versionamento, erro padronizado, paginação e identificadores de cliente.
- Provedor de identidade escolhido (Cognito é candidato) com critérios: custo, OIDC, MFA e recuperação de conta.
- **Saída:** decisões registradas; ADR de autenticação se o provedor for escolhido.

### P8. Revisão jurídica e parceiro científico

- **Dado** os temas do roadmap (LGPD de dado de saúde, transferência internacional na IA, comunidade com conteúdo de usuário, termos e privacidade, suplementos, regras de cancelamento da assinatura, comunicação promocional, venda de produto pelo app, enquadramento ANVISA), **quando** o Dênis os leva a um advogado, **então** cada tema recebe parecer ou pendência registrada, sem expor o conteúdo sensível no repositório.
- **Dado** o parceiro científico (nutricionista e educador físico), **quando** definido, **então** tem acesso para validar P2 e as travas de segurança do produto.
- **Sem advogado por ora:** a decisão é seguir sem parecer jurídico nesta etapa. A IA prepara a lista de perguntas e cada tema fica registrado como **pendência jurídica aberta**. Enquanto ela estiver aberta valem estas travas: uso restrito ao Dênis, nada de IA externa com dado de terceiros e nenhum usuário externo antes do parecer sobre LGPD, termos e transferência internacional.
- O **parceiro científico** é quem valida o conteúdo de nutrição e treino: as regras e os casos da troca (P2), as travas de segurança do produto e os suplementos permitidos. Deve ter formação em nutrição ou educação física. O Dênis só apresenta o produto ao nutricionista com quem conversa depois de validá-lo sozinho, com o produto pronto e redondo; até lá a validação do conteúdo é pendência aberta e vale a regra de uso restrito ao Dênis.
- Esta prova depende de pessoas, não de código.

### P9. Gateway de pagamento

- Pesquisa do gateway mais barato (taxas, Pix, recorrência, regras das lojas de aplicativo). **Não bloqueia nada**, pois o gateway fica mockado.
- **Saída:** comparativo no Notion (informação comercial) e a decisão de gateway adiada até a Etapa 6.

### P10. Escopo do PWA

- Definir o papel do PWA do cliente: o que ele faz que o app faz, se funciona offline e quais recursos nativos faltam.
- **Saída:** escopo registrado no roadmap e no `docs/architecture.md`.

## 4. Fora de escopo

- Código de produção, esquema definitivo de banco e infraestrutura permanente.
- Marketing, audiência e Heracles TV.
- Escolha final do gateway de pagamento e dos provedores de push e WhatsApp.

## 5. Critério de conclusão da etapa

A Etapa 0 termina quando: todas as provas têm registro em `docs/stage-0/`; a ferramenta de sincronização, o emulador de SQS, o provedor de IA e OCR e o provedor de identidade estão escolhidos em ADR; os casos de aceitação da troca estão validados; o orçamento de latência está fixado; e o jurídico e o parceiro científico têm parecer ou pendência registrada para cada tema. Pendência de tema que não bloqueia a Etapa 1 pode seguir aberta, desde que listada. Fica **bloqueado até o fim das pendências**: abrir o produto a usuários além do Dênis (Etapa 6 em diante).

## 6. Respostas do Dênis (revisão de 29/10/2026)

- **Exames para as provas:** os próprios, de sangue e ultrassom, mais um PDF de bioimpedância.
- **Aparelho de bioimpedância ou balança:** não há; a parte de aparelho da P6 fica adiada.
- **Parceiro científico:** ainda não definido. Há um nutricionista em contato, mas a ideia e o produto não foram apresentados.
- **Advogado:** seguir sem por enquanto; pendência registrada e travas da P8.
- **Apresentação ao nutricionista:** primeiro o Dênis valida o produto pronto, usando-o sozinho; só depois de "redondo" o produto é apresentado. Não há resumo a preparar agora.

## 7. Perguntas em aberto

Nenhuma.

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
