# P8: Pendências jurídica e científica

- **Prova:** P8 da Etapa 0 (`specs/platform/stage-0-proofs/spec.md`)
- **Estado:** pendências **abertas**. Não há advogado nem parceiro científico definidos; a decisão do Dênis (29/10/2026) foi seguir sem eles por enquanto.
- **Isto não é parecer jurídico nem validação científica.** São as perguntas que alguém qualificado precisa responder. A IA não responde a nenhuma delas.

## 1. Travas vigentes enquanto houver pendência

1. O produto é usado **só pelo Dênis**. Nenhum usuário externo, nem teste fechado, antes do parecer sobre LGPD, termos e transferência internacional (bloqueia a Etapa 6 em diante).
2. **Nenhuma regra de troca ou de plano vai a usuário que não seja o Dênis** antes da validação do parceiro científico.
3. **IA externa só com dado do próprio Dênis**, com autorização escrita por envio (provedor e região). Nada de dado de terceiros.
4. Comunidade, posts promocionais, cobrança real e venda de produto **não são abertos** a terceiros.
5. As travas de produto do roadmap (seção 7) valem integralmente.

## 2. Pendências jurídicas (T1.2)

Estado de todas: **aberta**. "Quando precisa" diz qual etapa fica bloqueada sem a resposta.

| # | Tema | Perguntas ao advogado | Quando precisa |
|---|---|---|---|
| J1 | **LGPD, dado de saúde** | Qual a base legal para tratar anamnese, exames, medidas e check-ins (consentimento específico, art. 11)? O que o consentimento precisa dizer? Há exigência de relatório de impacto (RIPD) e de encarregado (DPO) para o porte do produto? Quais prazos de retenção e de exclusão? Como atender o direito de exportação e de exclusão do titular? | Antes de qualquer usuário externo (Etapa 2 em produção) |
| J2 | **Transferência internacional na IA** | Enviar exame a provedor de IA ou OCR fora do Brasil é transferência internacional? Que cláusulas ou salvaguardas o contrato com o provedor exige? Anonimização reduz a exigência? O consentimento específico basta? | Antes de IA com dado de terceiros (Etapa 3 para usuários externos) |
| J3 | **Comunidade com conteúdo de usuário** | Qual a responsabilidade da plataforma por post de usuário (Marco Civil, notificação e retirada)? Que moderação mínima é exigida? Regras para menores de idade e para conteúdo sobre alimentação e corpo? Exigências das lojas de aplicativo para conteúdo de usuário (denúncia, bloqueio, termos)? | Antes de abrir a comunidade (Etapa 9) |
| J4 | **Termos de uso e política de privacidade** | Revisão dos textos que o Dênis cadastrará no backoffice: conteúdo mínimo, linguagem, versionamento e prova de aceite. Idade mínima do usuário. Foro e limitação de responsabilidade. | Antes de usuário externo (Etapa 2) |
| J5 | **Suplementos** | A sugestão de suplemento alimentar comum no cardápio, sem prescrição, é permitida? Que aviso é obrigatório? Há limite regulatório (ANVISA) para sugerir e para vender? | Antes de ativar sugestão a usuário externo (Etapa 4) |
| J6 | **Cancelamento da assinatura** | Cancelar só ao final do período e renovar automaticamente atende o Código de Defesa do Consumidor (direito de arrependimento, 7 dias, cancelamento fácil)? E as regras da App Store e do Google Play, se a venda passar pelas lojas? | Antes de cobrança real (Etapa 6) |
| J7 | **Comunicação promocional** | Que consentimento, canais e opção de sair a LGPD e a regulação de comunicação exigem para push, e-mail e WhatsApp? Como tratar WhatsApp (política do provedor e regras de consentimento)? Frequência permitida? | Antes de promocional a usuário externo (Etapas 8 e 9) |
| J8 | **Venda de produto pelo app** | Vender produto (por exemplo, suplemento ou material) dentro do app muda o enquadramento regulatório, fiscal ou das lojas? Quem é o vendedor (DCA Consultoria)? | Antes de qualquer venda de produto |
| J9 | **Enquadramento ANVISA (bioimpedância)** | Receber medição de bioimpedância de aparelho de terceiros e exibi-la no app enquadra o produto como software como dispositivo médico (SaMD)? Ler laudo enviado como PDF muda a resposta? Ajustar o plano com base nessa medição muda? | Antes de integração de aparelho (Etapa 7) e de qualquer ajuste automático do plano com dado de medição |
| J10 | **Menores, gestantes e quadros clínicos** | Como verificar idade e excluir menores e gestantes do plano automático? Que declaração e que aviso são suficientes? | Antes de usuário externo (Etapa 2) |
| J11 | **Responsabilidade pelo conteúdo gerado** | Responsabilidade da DCA por cardápio, treino e interpretação de exame sugeridos pelo app, e o texto do aviso "procure um médico". Seguro de responsabilidade? | Antes de usuário externo |
| J12 | **Titularidade e marca** | Uso da marca Heracles Society pela DCA e relação com futuros sócios, parceiros e a holding. Fica fora do app, mas condiciona contratos. | Quando houver contrato com parceiro |

**Como conduzir:** o Dênis leva esta lista a um advogado de direito digital e saúde. Cada resposta vira uma linha atualizada aqui (resumo, data, referência ao parecer, que fica fora do repositório público). Pareceres, contratos e valores ficam no Notion ou local, nunca no git.

## 3. Pendências científicas (T1.3)

O **parceiro científico** é um nutricionista e um educador físico (podem ser pessoas diferentes). O Dênis só os procura depois de validar o produto sozinho, quando estiver redondo. Estado de todas: **aberta**.

| # | O que validar | Responsável | Bloqueia |
|---|---|---|---|
| C1 | **Piso calórico seguro** por perfil (sexo, idade, peso, altura, nível de atividade) e piso de proteína | Nutricionista | Troca e plano a terceiros (Etapas 4 e 5) |
| C2 | **Tolerância de ±10% nas calorias do dia** e a regra de recompor o dia: está adequada? Quando é preciso menos? | Nutricionista | Etapa 4 |
| C3 | **Os 20 casos de troca** (P2, hoje "propostos pela IA, não validados"), incluindo o exemplo de arroz por purê de batata | Nutricionista | Etapa 4 |
| C4 | **Equivalências de alimentos**: grupos, porções e fatores de conversão do catálogo | Nutricionista | Etapa 4 |
| C5 | **Alergias, intolerâncias e restrições** como filtro obrigatório: lista mínima e regras para casos graves | Nutricionista | Etapa 4 |
| C6 | **Suplementos permitidos**: lista de suplementos alimentares comuns, texto do aviso e o que é proibido (hormônios, reposição em dose terapêutica) | Nutricionista | Etapa 4 |
| C7 | **Quando o plano automático não vale**: menores, gestantes, nutrizes, condições clínicas, uso de medicação, transtornos alimentares | Nutricionista | Etapa 2 |
| C8 | **Ajuste do plano por check-in**: limites de variação por semana e sinais de alerta | Nutricionista e educador físico | Etapa 7 |
| C9 | **Gamificação sem punir o corpo**: revisão das medalhas e missões para evitar incentivo a déficit, jejum ou excesso de treino | Nutricionista e educador físico | Etapa 7 |
| C10 | **Treino**: grupos de equivalência de exercícios, **regras da substituição de exercício** (mesmo grupo muscular e padrão de movimento, troca de equipamento como supino reto com halter por barra, e a carga que não se converte sozinha), progressão, volume e descanso seguros, e quando indicar procurar um profissional | Educador físico | Etapa 4 |
| C11 | **Faixas de referência dos marcadores** exibidos nas réguas de exame (fonte, população, unidade) e o texto "procure um médico" | Nutricionista ou médico parceiro | Etapa 3 |
| C12 | **Texto de avisos ao usuário**: tom, limites do que o app diz sobre saúde | Nutricionista e educador físico | Etapa 2 em diante |

**Como conduzir:** quando o produto estiver redondo, o Dênis apresenta o produto e esta lista. Cada validação vira nota aqui (quem, quando, o que mudou), e os casos de aceitação da troca ganham a marca de validados. Enquanto isso valem as travas da seção 1.

## 4. Quem fica livre de pendência

Até o fim das pendências, o uso do Dênis (Etapas 1 a 5, no marco de uso próprio) segue **sem bloqueio**, porque as travas restringem o produto a ele. O que fica bloqueado é abrir a terceiros (Etapa 6 em diante) e usar IA com dado de terceiros.
