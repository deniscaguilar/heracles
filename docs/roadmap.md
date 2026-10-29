# Roadmap: Heracles Society (app, backoffice, backend e infraestrutura)

**Status:** Aprovada (v5, com a alteração de 29/10/2026: troca de alimento só no backend)
**Base:** Notion "Heracles Society — Central de Planejamento", lições do Sistema da Clínica (EndoAcademy) e o feedback das versões anteriores (29/10/2026)
**Autor:** Dênis Casado Aguilar

> **Escopo deste documento:** somente o **produto técnico**: app mobile, backoffice, backend e infraestrutura. Marketing, audiência, redes sociais e Heracles TV serão tratados depois, em outro documento.
>
> Estimativas em tamanho relativo (P / M / G), **sem datas e sem prazo de lançamento**. O cronograma de sprints do Notion está descartado. Cada etapa termina por um gate.

---

## 1. Norte

Construir o **melhor app possível**, diferente do que existe no mercado, e usá-lo primeiro em casa (o Dênis é o usuário zero). O produto lançado inclui assinatura e pagamento.

**O que o app faz:**
1. **Treino e cardápio** gerados por regras científicas, com **troca de alimento** e **"comi fora do plano"**, ambos com recálculo do dia dentro de ±10%.
2. **Exames de qualquer tipo** (sangue, imagem, bioimpedância, calorimetria, SIBO e outros): o usuário digita, ou sobe PDF ou imagem; o app **reconhece o tipo de exame**, **IA e OCR extraem os dados**, o usuário confere e corrige; o app monta **réguas, curvas e comparativos** entre datas e a IA ajuda a interpretar. Valor fora da referência gera o aviso "procure um médico".
3. **Gamificação:** check-ins recorrentes que lançam desafios e **ajustam o plano**, missões e medalhas, no estilo do Nike Training Club.
4. **Comunidade** dentro do app: adicionar pessoas, postar e criar comunidades. O backoffice lança **posts promocionais** (novidades ou venda de produto) para todos os usuários, para um usuário ou para uma comunidade específica.
5. **Offline** para tudo do dia (treino, cardápio, registros, check-in e exames em fila), com sincronização. A troca de alimento exige conexão; sem sinal, fica como ação pendente.
6. **Assinatura e pagamento:** planos mensal, trimestral, semestral e anual, com gateway mockado até a escolha do mais barato.
7. **Resposta rápida:** na tela aparece na hora só o que é relevante para aquele momento; o resto é processado em segundo plano, por filas, e a tela se atualiza sozinha.

**Prova de valor:** o Dênis segue o plano por semanas, troca refeições sem frustração, acompanha os exames e volta ao app por vontade própria.

## 2. Mudanças da v4 para a v5

| Tema | v4 | v5 |
|---|---|---|
| Tipos de exame | Sobretudo exames de sangue | Sangue, imagem (ultrassom e outros) e específicos (bioimpedância, calorimetria, SIBO e outros); o app **reconhece o tipo** do que foi enviado |
| Bioimpedância | Só como exame enviado | Também verificar a viabilidade de receber a medição por aparelho integrado (Etapa 0) |
| Posts promocionais | Não previstos | Lançados pelo backoffice para **todos os usuários, um usuário ou uma comunidade**, com consentimento e regras próprias |
| Ideias da v4 | Aprovadas | Incorporadas nas etapas (seção 9) |

## 3. Princípios de sequenciamento

1. **Motores primeiro.** O plano gerado e a troca de alimento são a razão de ser do app.
2. **Dado de saúde só com proteção pronta.** Consentimento, criptografia e retenção são fundação.
3. **IA com gate.** Exame vai para um serviço externo só com consentimento específico, anonimização e revisão jurídica.
4. **Síncrono só o que é relevante agora; o resto é assíncrono.** A fundação já nasce com filas e processamento em segundo plano.
5. **Uso próprio cedo.** O marco de uso próprio vem logo depois do app com os motores; o uso real orienta o resto.
6. **Offline desde o desenho.** Identificadores no cliente e sincronização entram na fundação.
7. **Segurança do usuário acima de engajamento.** Comida, corpo e saúde pedem travas (seção 7).
8. **Gates, não datas.**

## 4. Visão geral

| Etapa | Nome | Tamanho | Gate de saída |
|---|---|---|---|
| 0 | Decisões e provas técnicas | P | Sync, algoritmo de troca, extração de exame, filas e wearables provados em protótipo |
| 1 | Fundação técnica | M | Login, auditoria, isolamento por usuário, filas, CI e infraestrutura em homologação |
| 2 | Cadastro, consentimento e anamnese | M | Cliente aceita termos no cadastro; políticas versionadas no backoffice |
| 3 | Exames com IA | G | Exames reais de cada família (laboratorial, imagem, específico) reconhecidos, extraídos, conferidos e exibidos; relatório e lembretes |
| 4 | Motores de treino e cardápio | G | Planos, trocas e "comi fora do plano" validados por profissional habilitado |
| 5 | App mobile offline | G | **Marco de uso próprio**: tudo do dia no celular; treino e leitura do cardápio funcionam offline |
| 6 | Assinatura e pagamento | M | Quatro periodicidades, renovação automática, cancelamento no fim do período, aluno grátis e vitalício |
| 7 | Gamificação e check-ins | M | Check-ins ajustam o plano; missões e medalhas com as travas de segurança; refeição por foto |
| 8 | Backoffice completo | M | Conteúdo, regras, missões, acessos, tipos de exame, posts promocionais e moderação sem tocar no banco |
| 9 | Comunidade | G | Amizades, posts e comunidades com denúncia, bloqueio e moderação; posts promocionais chegando à audiência escolhida |
| 10 | Uso próprio, medição e prontidão | M | Indicadores medidos, QA de segurança limpo e gateway real escolhido |

Um backoffice **mínimo** (exercícios, alimentos, regras, marcadores de exame e políticas) nasce junto com as etapas 2, 3 e 4. A Etapa 8 completa o que falta.

---

## 5. Detalhamento

### Etapa 0: Decisões e provas técnicas
- **Sincronização offline.** Comparar em protótipo Expo SQLite com sync próprio, WatermelonDB e PowerSync (a pesquisa não achou comparação direta). Definir resolução de conflito, identificadores gerados no cliente e a fila de envio.
- **Algoritmo de troca**, com 20 casos reais, implementado **só no backend (Kotlin)**; os casos viram testes de aceitação validados pelo parceiro científico. Medir a latência da troca contra o orçamento.
- **Reconhecimento e extração de exame.** Reunir de 20 a 30 exames reais que cubram as três famílias (sangue de laboratórios diferentes, laudos de imagem como ultrassom, e específicos como bioimpedância de aparelhos diferentes, calorimetria e teste respirado de SIBO), em PDF e foto. Medir a precisão do **reconhecimento do tipo** e da **extração** com OCR e LLM. Comparar provedores por precisão, custo e onde os dados são processados.
- **Filas.** Provar o fluxo ponta a ponta com SQS (produtor, worker, falha, nova tentativa, DLQ) e decidir como emular SQS no ambiente local. Lição do projeto da clínica: o LocalStack exige token a partir da versão 2026.03 e foi fixado na 4.14.0; ElasticMQ é uma alternativa compatível com SQS.
- **Orçamento de latência.** Definir metas por tipo de operação (proposta inicial: leituras do dia e trocas respondem em até 300 ms no p95; tudo que passa disso vira assíncrono). Os números são uma proposta a validar.
- **Wearables e bioimpedância.** Verificar a viabilidade de Apple Health e Health Connect em React Native; se viável, entra na Etapa 7, senão fica para a próxima fase. Verificar também se dá para **receber a medição de bioimpedância pelo app**: o celular sozinho não mede (bioimpedância depende de corrente elétrica e de hardware), então o teste é integrar balança com Bluetooth ou aparelho profissional, direto pelo fabricante ou pelos dados de gordura corporal e massa magra que as plataformas de saúde dos celulares expõem (conferir na prova). Avaliar com o jurídico se medir pelo app enquadra o produto como dispositivo médico (ANVISA).
- **Telas do app, contrato de API e autenticação** (os três P0 do Notion).
- **Parceiro científico** (nutricionista e educador físico).
- **Revisão jurídica:** LGPD de dado de saúde, transferência internacional na IA, comunidade com conteúdo de usuário, termos e privacidade, suplementos, as regras de cancelamento da assinatura (Etapa 6), a comunicação promocional (consentimento, canais e opção de sair) e a venda de produto pelo app.
- Pesquisar o **gateway de pagamento mais barato** (não bloqueia nada, pois o gateway fica mockado).

### Etapa 1: Fundação técnica
- Repositórios, CI com scan de dependências, SBOM e portão de cobertura, ambiente local reproduzível.
- Identidade e sessão atrás de um port de provedor de identidade.
- Auditoria append-only, sem dado pessoal no texto.
- Isolamento por usuário no banco (um bug de consulta não expõe dado de outro).
- Observabilidade sem dado de saúde em log, trace ou métrica.
- Erros padronizados e contrato de API versionado.
- Infraestrutura inicial: EC2 com Docker Compose, RDS PostgreSQL, S3, **SQS**, segredos no SSM.
- **Fluxos assíncronos** (seção 6): publicação de eventos por outbox transacional, workers, consumidores idempotentes, nova tentativa com espera crescente e DLQ, alarmes de idade da fila e de DLQ não vazia.
- **Ports com adaptador local:** canais de notificação (push e WhatsApp), gateway de pagamento (mock), provedor de IA e OCR, armazenamento de arquivos, fila.

### Etapa 2: Cadastro, consentimento e anamnese
- **Consentimento no cadastro do cliente**, antes de qualquer dado de saúde:
  - termos de uso e política de privacidade;
  - consentimento de tratamento de dado sensível de saúde;
  - consentimentos separados e revogáveis para: **processar exames com IA externa**, compartilhar conteúdo na comunidade, receber notificações e **receber comunicações promocionais**.
- **Backoffice:** área onde o Dênis **cadastra as políticas e os termos de consentimento**, com versão. Alterar o texto cria nova versão; o cliente aceita de novo quando a versão muda; os aceites antigos continuam ligados à versão que ele viu.
- Aceite e revogação registrados e auditados, com confirmação ao usuário (enviada em segundo plano).
- Retenção, exclusão e exportação dos dados do titular.
- Anamnese: objetivo, nível, restrições, **alergias e intolerâncias**, preferências alimentares.

### Etapa 3: Exames com IA
- **Entrada:** digitação, ou upload de **PDF ou imagem** em armazenamento criptografado. O arquivo original é sempre guardado. O envio responde na hora ("recebido") e o processamento segue em segundo plano.
- **Reconhecimento do tipo de exame.** A IA identifica **o que foi enviado** antes de extrair, e o usuário confirma o tipo. Famílias previstas:
  - **Laboratoriais** (sangue, urina e outros): marcadores numéricos com faixa de referência.
  - **Imagem** (ultrassom e similares): o app lê o **laudo escrito** (medidas, conclusão e impressão), nunca a imagem do exame; não interpreta imagem médica.
  - **Específicos:** bioimpedância (peso, gordura, massa magra, água, gordura visceral e demais campos, que variam por aparelho), calorimetria (taxa metabólica, gasto energético, quociente respiratório) e **SIBO** (teste respirado: hidrogênio e metano em partes por milhão ao longo do tempo de coleta).
  - Um mesmo PDF pode trazer **vários exames**: o app separa e trata cada um.
  - Tipo não reconhecido: o arquivo é guardado como "outro", o usuário pode anotar à mão, e o caso entra numa **fila de exames não reconhecidos** no backoffice para o Dênis cadastrar o novo tipo.
- **Extração por OCR e IA** (atrás do `aigateway`, em fila), com o **esquema do tipo reconhecido**: para laboratoriais, marcador, valor, unidade, data, laboratório e a **faixa de referência do próprio laudo**; para os demais, os campos definidos no catálogo daquele tipo.
- **Conferência obrigatória:** nada é salvo sem o usuário revisar. A tela mostra o valor extraído ao lado do trecho do documento, destaca o que veio com baixa confiança e permite **corrigir qualquer campo manualmente**.
- **Validações automáticas:** unidade coerente, valor plausível, marcador reconhecido pelo catálogo (com sinônimos, por exemplo "CT" e "Colesterol total").
- **Visualização por tipo:** marcadores laboratoriais numa **régua** entre mínimo, faixa de referência e máximo, com a posição do valor; bioimpedância e calorimetria com campos e réguas do aparelho e perfil (sexo, idade) quando o laudo os traz; SIBO como **curva** de hidrogênio e metano pelo tempo; laudos de imagem como texto estruturado com as medidas.
- **Comparativo:** o histórico do mesmo marcador (por exemplo, colesterol a cada 3 meses) numa régua "antes e agora" e na linha do tempo.
- **Interpretação por IA:** resumo da evolução e do que mudou, gerado em segundo plano. O app **informa e compara; não diagnostica nem prescreve**. Valor fora da referência mostra o aviso **"procure um médico"**, mais forte quando o valor está muito distante da faixa.
- **Relatório em PDF para levar ao médico**, com o resumo dos exames e a evolução, gerado em segundo plano e entregue quando pronto.
- **Lembrete de refazer o exame** conforme a periodicidade de cada marcador (por exemplo, colesterol a cada 3 meses), enviado pelo canal de notificação.
- **Catálogo de tipos de exame e de marcadores** (esquemas de campos, nomes, sinônimos, unidades, periodicidade sugerida, faixas de apoio) mantido no backoffice, junto com a fila de exames não reconhecidos. Um tipo novo entra pelo catálogo, sem deploy.
- Se o usuário revogar o consentimento de IA, a digitação manual continua funcionando.
- **Gate de IA:** só com consentimento específico, anonimização no `aigateway`, provedor aprovado e revisão jurídica.

### Etapa 4: Motores de treino e cardápio
- Banco de 150 a 250 exercícios com grupos de equivalência.
- Banco de alimentos baseado em TACO ou TBCA, com porções caseiras e **filtros rígidos de alergia e restrição**.
- Motor de treino por regras (volume, intensidade, periodização por objetivo).
- Motor de cardápio (calorias e macros por refeição).
- **Troca de alimento com recálculo do dia** (síncrona, calculada no servidor, com conexão):
  1. O usuário diz o que quer trocar ("arroz" por "purê de batata inglesa").
  2. O sistema calcula a quantidade do substituto que mantém as calorias da refeição e mostra a diferença de proteína, carboidrato e gordura.
  3. Confere o total do dia contra a meta, aceitando **±10% de calorias**. Se estourar, propõe ajustes de porção em outras refeições e mostra as opções.
  4. Respeita um **piso de proteína configurável** e um piso calórico seguro.
- **"Comi fora do plano":** o usuário registra o que comeu e o resto do dia é recalculado com o mesmo algoritmo.
- **Lista de compras** da semana, com as trocas já aplicadas.
- **Receitas e preparo em lote**, ligados ao banco de alimentos.
- **Suplementos como sugestão no cardápio**, só os alimentares comuns (por exemplo whey e creatina), sem hormônios e sem reposição de vitaminas e minerais em dose terapêutica, com texto revisado pelo parceiro e pelo jurídico.
- Backoffice mínimo para manter exercícios, alimentos, receitas e regras.
- Algoritmo **só no backend** (ADR-0006), com os casos reais da Etapa 0 como testes de aceitação. App, PWA e backoffice usam o mesmo serviço, e corrigir uma regra de segurança vale na hora para todos.
- Validação das regras e do algoritmo pelo parceiro científico.

**Gate:** sem validação científica pontual, o plano gerado não é considerado confiável.

### Etapa 5: App mobile offline (marco de uso próprio)
- Login, home do dia, treino do dia, cardápio do dia, área de troca de alimento e "comi fora do plano", exames com régua e comparativo, lista de compras, progresso e perfil.
- **Offline:** treino (ler e registrar), leitura do cardápio e check-in funcionam sem internet. **A troca de alimento e o "comi fora do plano" exigem conexão:** sem sinal, o usuário registra a intenção como **ação pendente**, que o servidor calcula ao reconectar e devolve como proposta para confirmar. Envios (exame, check-in, post, ação pendente) entram numa fila local e sincronizam depois. Upload de exame offline fica enfileirado e a extração acontece quando houver conexão.
- **Estados de processamento visíveis:** o que roda em segundo plano mostra "processando", "pronto" ou "falhou, tentar de novo". A tela se atualiza ao voltar ao primeiro plano, por consulta periódica com espera crescente e por push quando disponível.
- Push, se o provedor escolhido permitir.
- **Modo "dia livre" e pausa saudável**, para viagem e doença sem culpa.
- Analytics com os eventos que medem o uso próprio.
- Distribuição por TestFlight e teste interno do Google Play.

**Gate:** o Dênis usa o app no dia a dia no lugar de planilhas e anotações.

### Etapa 6: Assinatura e pagamento
- **Planos de conteúdo:** Treino, Nutrição e Treino + Nutrição.
- **Periodicidades:** mensal, trimestral, semestral e anual.
- **Renovação automática.** A cobrança do próximo período é tentada no vencimento, com nova tentativa e período de tolerância em caso de falha.
- **Cancelamento só ao final do período já pago.** O cancelamento desliga a renovação; o acesso continua até o fim do período. A assinatura passa pelo estado "cancelamento agendado".
- **Upgrade e downgrade** com regra de cobrança proporcional definida na spec.
- **Backoffice, concessão de acesso sem cobrança:**
  - **aluno grátis:** acesso a um plano de conteúdo escolhido, sem cobrança, com motivo e data de validade opcional;
  - **vitalício:** acesso sem nunca cobrar nem renovar.
  - Toda concessão exige permissão específica e é auditada (quem, quando, motivo).
- Cobrança recorrente atrás de um port, com **gateway mockado**. Os eventos do gateway (pagamento aprovado, falho, estornado) chegam por webhook, vão para fila e são processados de forma idempotente. A escolha do provedor real (o mais barato, incluindo PIX e parcelamento) acontece depois e troca só o adaptador.
- Tela de "sem assinatura ativa" e tela de gestão da assinatura.
- **Pontos para o jurídico:** cancelamento só no fim do período convive com o direito de arrependimento em compras feitas fora do estabelecimento, previsto no Código de Defesa do Consumidor, e com as regras das lojas para assinatura dentro do app. A forma de vender (web, dentro do app ou ambas) fecha com essa revisão.

### Etapa 7: Gamificação e check-ins
- **Check-ins recorrentes** por período (diário, semanal, mensal): sono, energia, fadiga, aderência, dificuldades.
- **Ciclo único:** as respostas **lançam desafios** e **ajustam volume de treino e calorias** (dentro dos pisos de segurança). O recálculo roda em segundo plano e o app mostra o plano atualizado quando pronto.
- **Missões e medalhas**, com área de conquistas.
- **Refeição por foto:** a IA sugere os alimentos e porções, o usuário confere e corrige. Entra só depois do marco de uso próprio, porque a precisão para porções é menor do que parece.
- **Wearables** (Apple Health e Health Connect) alimentando missões sem digitação, se a Etapa 0 mostrar viabilidade.
- **Bioimpedância pelo app**, se a Etapa 0 mostrar que dá para integrar um aparelho: a medição entra no mesmo histórico e no mesmo comparativo do exame enviado por laudo.
- **Exame ligado ao plano:** marcador fora da referência vira aviso e orientação de procurar um médico. Ajuste de cardápio por exame só no futuro e revisado por profissional.
- Regras de missão e medalha mantidas no backoffice.

### Etapa 8: Backoffice completo
- Exercícios, alimentos, receitas, regras dos motores, marcadores de exame, textos e faixas de referência.
- Políticas e termos de consentimento.
- Missões, medalhas e perguntas dos check-ins.
- Planos, assinaturas e **concessão de aluno grátis e vitalício**.
- Moderação da comunidade (fila de denúncias, suspensão, remoção).
- **Posts promocionais:** criar, agendar, pré-visualizar, pausar e encerrar (detalhes na Etapa 9).
- **Tipos de exame** e fila de exames não reconhecidos.
- Visão de auditoria e painel de filas (mensagens paradas e DLQ).

### Etapa 9: Comunidade
- Perfil, **adicionar amigos**, **postar mensagens**, **criar e entrar em comunidades**, tudo junto.
- Feed e notificações montados em segundo plano; o post aparece para o autor na hora e chega aos outros na próxima atualização.
- **Moderação desde o primeiro dia:** denunciar, bloquear, silenciar, filtro de conteúdo e fila no backoffice. As lojas costumam exigir esses mecanismos em apps com conteúdo de usuário; conferir as regras atuais antes do envio.
- Comunidades com regras, administradores e moderadores próprios.
- **Posts promocionais** (divulgação de novidades ou venda de produto), lançados pelo backoffice:
  - **Audiência:** todos os usuários, um usuário específico ou uma comunidade específica.
  - **Aparece no feed** da audiência, marcado como promocional, e pode ir também por notificação, só para quem aceitou receber comunicações promocionais.
  - **Agendamento, pausa e encerramento**, com limite de frequência por usuário para não saturar o feed.
  - **Conteúdo:** texto, imagem e botão de ação (link ou tela do app). Venda de produto leva a uma tela de compra; as regras das lojas para venda dentro do app valem e são conferidas antes (a forma de cobrar produto físico e digital é diferente).
  - **Métricas:** alcance, aberturas e cliques, sem identificar o usuário individualmente além do necessário.
  - **Segmentação sem dado de saúde:** a audiência é escolhida por todos, usuário ou comunidade; **nunca** por resultado de exame, peso, objetivo de saúde ou qualquer dado sensível.
  - A entrega à audiência roda em segundo plano (fila), porque o público pode ser grande; o autor vê o post como "agendado" ou "entregue".
  - Permissão específica e auditoria de quem lançou, quando e para quem.
- Privacidade por padrão (seção 7).

### Etapa 10: Uso próprio, medição e prontidão
- Definir 3 a 5 indicadores com linha de base (por exemplo: dias por semana seguindo o plano, trocas por semana e quantas precisaram de ajuste manual, exames extraídos sem correção, check-ins respondidos, retorno ao app sem lembrete, tempo de resposta percebido).
- Testes de carga e segurança, ambiente de fumaça efêmero e restauração de backup testada.
- Escolha e ativação do gateway real.
- Decisão sobre abrir a mais pessoas.

---

## 6. Fluxos síncronos e assíncronos

**Regra:** só é síncrono o que o usuário precisa ver **agora** para continuar. Todo o resto vira mensagem numa fila, é processado em segundo plano e a tela se atualiza sozinha. O objetivo é o melhor tempo de resposta possível no que o usuário toca.

| Síncrono (resposta imediata) | Assíncrono (fila, tela atualiza depois) |
|---|---|
| Ler o treino e o cardápio do dia | Reconhecimento do tipo e extração de exame por OCR e IA |
| Troca de alimento e "comi fora do plano" (calculadas no servidor, com conexão) | Interpretação por IA e comparativos pesados |
| Registrar série, treino concluído ou refeição | Relatório em PDF para o médico |
| Login e leitura do perfil | Envio de e-mail, push e WhatsApp |
| Confirmar valores de exame extraídos | Lembretes de exame e de check-in |
| Escrever o post e ver o próprio post | Montagem do feed e entrega aos outros usuários |
| Responder o check-in | Recalcular o plano a partir do check-in |
| Iniciar o pagamento | Webhooks do gateway, renovação e cobrança |
| | Entrega de post promocional à audiência escolhida |
| | Moderação, auditoria pesada e limpeza por retenção |

**Como funciona:**
- **SQS** para as filas. SNS só se algum evento precisar chegar a vários consumidores.
- **Outbox transacional:** a gravação no banco e a intenção de publicar acontecem juntas; um publicador envia para a fila. Evita perder mensagem ou publicar algo que foi desfeito.
- **Consumidores idempotentes:** o SQS entrega pelo menos uma vez, então a mesma mensagem pode chegar duas vezes. Cada tarefa tem um identificador e o consumidor ignora o que já fez.
- **Nova tentativa com espera crescente e DLQ** para mensagens que falham de forma repetida, com alarme e tela de reprocessamento no backoffice.
- **Fila FIFO só onde a ordem importa;** o padrão é a fila comum, mais barata e mais rápida.
- **A mensagem leva só identificadores,** nunca dado de saúde. Os workers leem o dado no banco com o isolamento por usuário ativo.
- **Estado visível:** toda tarefa assíncrona tem status (pendente, processando, pronto, falhou) que o app mostra.
- **Atualização da tela:** ao voltar ao primeiro plano, por consulta periódica com espera crescente e por push quando disponível. Não há conexão aberta permanente no MVP.
- **Mesmo artefato, dois perfis:** `api` e `worker` rodam juntos no início e podem se separar sem reescrita.
- **Observabilidade:** idade da mensagem mais antiga, tamanho da fila, tamanho da DLQ e tempo de processamento por tipo de tarefa.
- **Custo:** SQS cobra por requisição e é barato no volume de um MVP; conferir a tabela atual de preços antes de ativar.

## 7. Travas de segurança do produto (para você validar ou vetar)

- **Exame e IA:** nada extraído é salvo sem conferência do usuário; o arquivo original é preservado; o conteúdo do documento é tratado como dado, nunca como instrução ao modelo (um PDF pode conter texto malicioso). A IA informa e compara; nunca diagnostica, prescreve ou recomenda conduta. Valor fora da referência leva ao aviso "procure um médico".
- **Comunidade e dado de saúde:** exame, valores de exame, peso e medidas **nunca** são publicados nem sugeridos para compartilhamento. Medalhas e métricas de treino só vão ao perfil público se o usuário escolher. A LGPD dá a dado de saúde regime próprio (arts. 11 a 13), com vedação de compartilhar para obter vantagem econômica; revisão jurídica antes de abrir a comunidade.
- **Gamificação sem punir o corpo:** sem medalhas por perda de peso, por déficit calórico ou por pular refeição; sem ranking de aparência; sequência (streak) que **não zera de forma punitiva**. Alimentação e imagem corporal são terreno em que gamificação mal desenhada causa dano.
- **Pisos de segurança:** nenhuma troca, ajuste de check-in ou "comi fora do plano" leva o dia abaixo de um piso calórico e de proteína seguro, definido com o parceiro científico. Menores de idade, gestantes e quadros que exijam acompanhamento ficam fora do plano automático.
- **Suplementos:** só os alimentares comuns; sem hormônios e sem reposição de vitaminas e minerais em dose terapêutica.
- **Concessões de acesso:** aluno grátis e vitalício só por perfil com permissão específica, sempre com motivo e auditoria.
- **Posts promocionais:** só para quem aceitou comunicações promocionais (no caso de notificação), com rótulo "promocional", opção de silenciar e limite de frequência. A audiência nunca é escolhida por dado de saúde: a LGPD veda usar dado de saúde para vantagem econômica (art. 11, §3º), e segmentar venda por resultado de exame cai exatamente nisso.
- **Tipos de exame:** o app lê o **laudo**, nunca a imagem de um exame de imagem. O tipo reconhecido é sempre confirmado pelo usuário, e exame não reconhecido nunca é "adivinhado".
- **Evidência de gamificação:** a pesquisa não achou estudo independente e sólido de que medalhas, sequências e missões aumentem a retenção a longo prazo em apps de saúde; o que aparece são blogs de fornecedores e uma pesquisa de preferência. Por isso a Etapa 10 mede o efeito no uso real.

## 8. Decisões

**Em aberto:** nenhuma.

**Fechadas:**
- Bioimpedância pelas duas vias: ler o laudo enviado por PDF ou foto (Etapa 3) e verificar a viabilidade de receber a medição de um aparelho integrado (Etapa 0, com entrada na Etapa 7 se for viável).

- Infraestrutura: EC2 + Docker Compose + RDS, com SQS para as filas.
- Motores antes do pagamento; assinatura e pagamento na Etapa 6 com gateway mockado.
- Planos mensal, trimestral, semestral e anual, com renovação automática e cancelamento só ao final do período; aluno grátis e vitalício pelo backoffice.
- Offline para tudo do dia (treino, leitura do cardápio, check-in e exames em fila), com sincronização.
- Algoritmo de troca **só no backend**; a troca exige conexão e, sem sinal, fica como ação pendente (alterado em 29/10/2026, aprovada em 29/10/2026).
- Tolerância de ±10% nas calorias do dia, com piso de proteína configurável.
- Comunidade completa desde o início.
- Exames: digitação, upload de PDF ou imagem, extração por IA e OCR, interpretação por IA, réguas, comparativos e ajuste manual.
- Aviso "procure um médico" em vez de interpretação clínica.
- Suplementos como sugestão no cardápio, só os alimentares comuns.
- Fluxos assíncronos por filas; síncrono só o que é relevante no momento.
- Push e WhatsApp desejados; ficam atrás de um port e o provedor pago fica em aberto.
- Marketing, audiência e Heracles TV: outro documento, depois. O que fica neste documento é o mecanismo técnico de post promocional.
- O app reconhece o tipo de exame enviado (sangue, imagem, específicos como bioimpedância, calorimetria e SIBO) e extrai conforme o tipo.
- Posts promocionais lançados pelo backoffice para todos, um usuário ou uma comunidade.

## 9. Ideias aprovadas e onde entram

| Ideia | Etapa |
|---|---|
| "Comi fora do plano" | 4 e 5 |
| Check-in que adapta o plano | 7 |
| Lista de compras | 4 e 5 |
| Exame ligado ao plano, com aviso "procure um médico" | 3 e 7 |
| Wearables (se viável) | 0 e 7 |
| Modo "dia livre" e pausa saudável | 5 |
| Receitas e preparo em lote | 4 |
| Suplementos como sugestão no cardápio | 4 |
| Lembrete de refazer o exame | 3 |
| Relatório em PDF para levar ao médico | 3 |
| Refeição por foto | 7 |
| Reconhecimento automático do tipo de exame | 3 |
| Posts promocionais para todos, um usuário ou uma comunidade | 8 e 9 |
| Bioimpedância medida por aparelho integrado (se viável) | 0 e 7 |

## 10. Fora deste roadmap

- **Divulgação e negócio:** marketing, audiência, redes sociais, Heracles TV e calendário editorial (outro documento).
- **Produto futuro:** camada médica e Plano Full, nutricionista e médico parceiros assinando planos, suplementos de marca própria, farmácia, centro de treino, clínicas, laboratório e estética.

## 11. Riscos e mitigação

| Risco | Impacto | Mitigação |
|---|---|---|
| Escopo grande para um só desenvolvedor | Nunca fica pronto | Marco de uso próprio na Etapa 5; gamificação e comunidade depois |
| Extração de exame errada | Dado incorreto na régua e no comparativo | Conferência obrigatória; arquivo original; medição de precisão na Etapa 0 |
| Tipo de exame reconhecido errado ou formato novo | Campos errados ou exame perdido | Usuário confirma o tipo; fila de não reconhecidos; tipos novos pelo catálogo, sem deploy |
| Formatos de laudo muito variados (aparelhos e laboratórios) | Extração frágil | Amostra real e variada na Etapa 0; esquema por tipo no catálogo |
| Post promocional visto como spam ou uso indevido de dado de saúde | Reclamação, risco jurídico e de loja | Consentimento próprio, segmentação sem dado de saúde, limite de frequência, opção de silenciar |
| Medição de bioimpedância pelo app enquadrada como dispositivo médico | Risco regulatório | Usar dado de aparelho de terceiros; revisão jurídica na Etapa 0 |
| Dado de exame enviado a IA externa | Risco jurídico | Consentimento específico, anonimização, provedor aprovado, gate de IA |
| Mensagem entregue duas vezes pelo SQS | Cobrança ou ação em duplicidade | Consumidores idempotentes com identificador da tarefa |
| Mensagem que sempre falha trava o fluxo | Tarefa nunca termina | DLQ, alarme e reprocessamento pelo backoffice |
| Usuário não entende o "processando" | Sensação de app quebrado | Estados claros, atualização automática e opção de tentar de novo |
| Sincronização offline mais difícil do que parece | Retrabalho de arquitetura | Prova na Etapa 0; IDs no cliente desde a fundação |
| Troca de alimento exige conexão | Frustração sem sinal ou com latência alta | Ação pendente calculada ao reconectar; orçamento de latência medido na Etapa 0; reavaliar a troca offline com o uso real |
| Sugestão de suplemento vista como prescrição | Risco regulatório | Só suplementos comuns; revisão do parceiro e do jurídico |
| Cancelamento só no fim do período conflita com direito do consumidor ou regras das lojas | Risco jurídico e de recusa na loja | Revisão jurídica na Etapa 0; definir canal de venda |
| Gamificação incentiva comportamento ruim | Dano ao usuário | Travas da seção 7; medir na Etapa 10 |
| Comunidade com conteúdo problemático | Risco jurídico e de loja | Moderação desde o primeiro dia; privacidade por padrão |
| Gateway real com particularidades | Retrabalho na cobrança | Port com mock; trocar só o adaptador |
| Provedores pagos de push e WhatsApp | Custo inesperado | Port com adaptador local; decidir com custo conhecido |

## 12. Próximos passos

| Responsável | Ação |
|---|---|
| Dênis | Avaliar a v5 e responder a decisão da seção 8 |
| Dênis e Claude | Depois da aprovação: `CLAUDE.md` e ADRs iniciais |
| Dênis e Claude | Etapa 0: provas de sincronização, algoritmo de troca, extração de exame, filas e wearables |

## 13. Referências consultadas

- [Nutrium: sugerir alimentos com quantidades equivalentes](https://help.nutrium.com/en/articles/7068252-how-can-i-suggest-foods-with-equivalent-quantities)
- [Fitia: trocar um alimento do plano](https://fitia.app/help/articles/how-to-swap-or-replace-food-in-fitia-plan/)
- [Berger e Jung: preferências de elementos de jogo em apps de nutrição (HICSS)](https://aisel.aisnet.org/hicss-54/da/gamification/5)
- [Offline-first em React Native com Expo SQLite e Drizzle](https://reactnativerelay.com/article/building-offline-first-react-native-apps-2026-expo-sqlite-drizzle-orm-sync-strategies)
- [WatermelonDB](https://github.com/Nozbe/watermelondb)
- [LGPD: dados sensíveis (Confidata)](https://confidata.com.br/blog/lgpd-comentada-09-dados-sensiveis-anonimizacao)

## 14. Aprovação

- [x] Aprovado por: Dênis, via Plannotator (Approve), em 29/10/2026
- [x] Alteração de 29/10/2026 (troca de alimento só no backend; offline para tudo do dia exceto a troca) aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
