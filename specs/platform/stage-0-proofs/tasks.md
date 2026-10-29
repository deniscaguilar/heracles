# Tarefas: Etapa 0, decisões e provas técnicas

- **Spec:** `spec.md` (Aprovada) • **Plano:** `plan.md` (Aprovada)
- Cada tarefa é pequena e termina com algo verificável. Marque `[x]` ao concluir e registre em `docs/progress-log.md`.
- Tarefas marcadas **(pede ok)** exigem aprovação do Dênis antes de executar (plano, seção 6).

## Preparação

- [x] T0.1 Criar `prototypes/README.md` (aviso: descartável, não é referência de qualidade) e `docs/stage-0/README.md` com o modelo de registro. *Pronto quando:* os dois arquivos existem e seguem o plano, seções 2 e 3.
- [x] T0.2 Conferir que `local/` está no `.gitignore` e criar a pasta local com um README que lista o que pode ficar lá. *Pronto quando:* `git status` não mostra nada de `local/`.

## Ordem 1 (independentes)

- [x] T1.1 **P10:** propor o papel do PWA (o que faz, offline ou não, recursos nativos que faltam) e registrar em `docs/stage-0/p10-pwa.md`. *Pronto quando:* Dênis aprova e o roadmap e o `docs/architecture.md` refletem a decisão.
- [x] T1.2 **P8:** montar a lista de perguntas jurídicas por tema (LGPD, transferência internacional, comunidade, termos, suplementos, cancelamento, promoção, venda de produto, ANVISA) em `docs/stage-0/p8-pendencias.md`. *Pronto quando:* cada tema está listado como pendência aberta com as travas vigentes.
- [x] T1.3 **P8:** listar o que o parceiro científico precisa validar (regras e casos da troca, travas de segurança, suplementos) e registrar como pendência. *Pronto quando:* a lista está no mesmo arquivo.
- [x] T1.4 **P9:** pesquisar gateways (taxas, Pix, recorrência, regras das lojas) e guardar o comparativo **no Notion**, não no repositório. *Pronto quando:* o comparativo existe no Notion e `docs/stage-0/p9-gateway.md` só aponta que a decisão fica para a Etapa 6.

## Ordem 2

- [x] T2.1 **P4:** subir ElasticMQ e LocalStack fixado (versão sem token) via `docker compose` local em `prototypes/queues/`. *Pronto quando:* os dois respondem a criar fila e enviar mensagem.
- [x] T2.2 **P4:** produtor e worker de teste. *Pronto quando:* mensagem enviada é consumida e confirmada.
- [x] T2.3 **P4:** cenários de falha: nova tentativa com espera crescente, DLQ, mesma mensagem duas vezes sem efeito duplicado. *Pronto quando:* os três passam nos dois emuladores ou a diferença está anotada.
- [ ] T2.4 **P4 (pede ok):** teste de fumaça no SQS real, com `destroy` ao final. *Pronto quando:* resultado comparado ao emulador e recursos removidos.
- [ ] T2.5 **P4:** escrever `docs/stage-0/p4-filas.md` e atualizar o ADR-0004 com o emulador escolhido. *Pronto quando:* ADR revisada e aprovada no Plannotator.
- [x] T2.6 **P2:** criar o módulo Kotlin isolado em `prototypes/swap/` e um catálogo de alimentos de teste sem dado pessoal. *Pronto quando:* compila e carrega o catálogo.
- [x] T2.7 **P2:** escrever os 20 casos de troca em arquivo de dados (inclui arroz → purê de batata), marcados "propostos pela IA, não validados". *Pronto quando:* o arquivo existe e o Dênis revisou.
- [x] T2.8 **P2:** implementar o algoritmo (±10% de calorias, piso de proteína, piso calórico seguro, filtros de alergia e restrição) em `BigDecimal`, com as regras de arredondamento escritas. *Pronto quando:* os 20 casos passam.
- [x] T2.9 **P2:** medir a latência (p50, p95, p99) com carga sintética. *Pronto quando:* números no registro `p2-troca.md`.

## Ordem 3

- [x] T3.1 **P1:** app React Native mínimo e servidor de teste em Kotlin em `prototypes/sync/`. *Pronto quando:* o app cria um registro offline e o servidor o recebe ao reconectar. *(pede ok para dependências)*
- [x] T3.2 **P1 (decisão 29/10/2026: só a opção A; B e C não executadas):** implementar os cenários nas três opções (Expo SQLite com sync próprio, WatermelonDB, PowerSync), começando por mutação duplicada, conflito em dois aparelhos e sessão expirada. *Pronto quando:* resultado de cada cenário anotado por opção.
- [x] T3.3 **P1 (opção A medida; criptografia local fica como pendência):** medir 10 mil registros e verificar criptografia local nas três opções. *Pronto quando:* números e suporte anotados.
- [x] T3.4 **P1:** tabela comparativa, ADR-0005 atualizada com a ferramenta e `docs/stage-0/p1-sincronizacao.md`. *Pronto quando:* ADR aprovada no Plannotator.
- [x] T3.5 **P3:** reunir os exames do Dênis em `local/` (sangue, imagem e bioimpedância) e montar os gabaritos. *Pronto quando:* gabaritos prontos **em `local/gabaritos/`**. Eles contêm valores reais de saúde e por isso **não vão ao repositório público** (só métricas agregadas). Os exames são: 2 laudos de sangue (PDF), 2 laudos de imagem (tomografia e ressonância) e 1 foto de bioimpedância (InBody).
- [x] T3.6 **P3 (cancelada, lacuna registrada):** o Dênis não tem exames de calorimetria nem de SIBO e decidiu seguir só com os exames que tem (29/10/2026). A lacuna está registrada em `docs/stage-0/p3-extracao-exames.md` e se repete na Etapa 3.
- [ ] T3.7 **P3 (pede ok):** escolher provedores candidatos, informar provedor e região e obter autorização escrita do Dênis para enviar os exames. *Pronto quando:* autorização registrada no chat.
- [ ] T3.8 **P3:** pipeline de OCR e LLM com saída validada por esquema, rodando em cada provedor. *Pronto quando:* saída gerada para cada documento e provedor, só em `local/`.
- [ ] T3.9 **P3:** medir precisão do tipo e da extração, custo por exame e região de processamento; incluir o documento com texto de injeção. *Pronto quando:* métricas agregadas em `p3-extracao-exames.md`, sem dado pessoal.
- [ ] T3.10 **P3:** recomendar provedor e atualizar o ADR-0007; registrar a meta mínima de precisão na spec. *Pronto quando:* ADR aprovada.

## Ordem 4 e 5

- [ ] T4.1 **P6:** matriz de dados legíveis em Apple Health e Health Connect, com permissões e bibliotecas. *Pronto quando:* matriz em `p6-wearables.md`.
- [ ] T4.2 **P6:** teste com dados simulados em simulador ou aparelho. *Pronto quando:* leitura confirmada ou limite anotado; parte do aparelho de bioimpedância registrada como adiada.
- [ ] T5.1 **P7:** fluxos de tela do app e do backoffice em baixa fidelidade. *Pronto quando:* o Dênis revisa e aprova.
- [ ] T5.2 **P7:** convenções de API (versionamento, erro padronizado, paginação, identificadores de cliente). *Pronto quando:* documento aprovado.
- [ ] T5.3 **P7:** comparar provedores de identidade (custo, OIDC, MFA, recuperação de conta) e propor um. *Pronto quando:* ADR de autenticação aprovada.

## Fechamento

- [ ] T6.1 **P5:** fixar metas de latência por tipo de operação a partir de P1, P2 e P4 e escrever a tabela no `docs/architecture.md`. *Pronto quando:* aprovada no Plannotator.
- [ ] T6.2 Conferir o critério de conclusão da spec (seção 5), listar pendências abertas (jurídica, científica, aparelho de bioimpedância) e atualizar `docs/progress-log.md`. *Pronto quando:* tudo checado e o próximo passo (spec da Etapa 1) registrado.
