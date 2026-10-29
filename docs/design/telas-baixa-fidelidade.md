# Telas em baixa fidelidade (app e backoffice)

> **Status:** Aprovada (29/10/2026; T5.1 da Etapa 0). Fluxos e telas aprovados pelo Dênis no Plannotator; **não é design visual** (cores, tipografia e identidade ficam para depois). Cada tela será detalhada na spec da etapa que a implementa.

Desenho feito em texto para caber no repositório e ser revisado sem ferramenta de design. Se preferir, as telas aprovadas viram protótipo no Figma (a combinar).

## 1. Princípios

1. **O dia em primeiro lugar.** A primeira tela mostra o que fazer agora (próxima refeição, treino de hoje), não um painel de métricas.
2. **Offline é normal.** O app nunca mostra erro de rede para registrar treino, refeição ou check-in: grava e mostra "enviando". A troca de alimento sem conexão mostra "pendente de cálculo" (ADR-0006).
3. **Estado sempre visível:** pendente de envio, enviando, falhou (com "tentar de novo"), processando (exame).
4. **Sem culpa, sem punição do corpo** (travas do roadmap, seção 7): nada de medalha por perda de peso, déficit ou pular refeição; sequência que não zera de forma punitiva; "dia livre" é um recurso, não uma falha.
5. **A IA informa, não diagnostica.** Todo valor fora da referência leva o aviso fixo "procure um médico".
6. **Dado de saúde só do dono.** Exame, peso e medidas nunca aparecem na comunidade nem são sugeridos para compartilhar.
7. **Textos em pt-BR por i18n**, com chaves em inglês.

## 2. Mapa de navegação do app (mobile e web)

```
Entrar / Criar conta (conta própria, Google, Apple)
  └─ Consentimentos (termos, dado de saúde, IA em exames, comunidade, notificações, promoções)
      └─ Anamnese (objetivo, nível, restrições, alergias, preferências)
          └─ Barra inferior
              ├─ Hoje
              ├─ Cardápio  (troca, "comi fora do plano", lista de compras)
              ├─ Treino
              ├─ Exames
              └─ Mais  (check-in, missões e medalhas, comunidade, assinatura, perfil e privacidade)
```

A comunidade, as missões e as medalhas entram nas Etapas 7 e 9; as telas ficam desenhadas aqui só como lugar na navegação.

## 3. Telas do app

### 3.1 Entrar e consentimentos (Etapa 2)

```
+-----------------------------+      +-----------------------------+
| Heracles Society            |      | Antes de começar            |
|                             |      |                             |
| [ Entrar com Google ]       |      | [x] Termos de uso (v3)  ver |
| [ Entrar com Apple  ]       |      | [x] Tratar meus dados de    |
| ----------- ou -----------  |      |     saúde (obrigatório)  ver|
| e-mail  [________________]  |      | [ ] Usar IA externa nos     |
| senha   [________________]  |      |     meus exames          ver|
| [ Entrar ]  Criar conta     |      | [ ] Compartilhar na         |
| Esqueci a senha             |      |     comunidade           ver|
+-----------------------------+      | [ ] Notificações / [ ] Promo|
                                     | [ Continuar ]               |
                                     +-----------------------------+
```

Cada consentimento é separado e revogável depois em Mais > Privacidade. Sem o de dado de saúde, o app não segue.

### 3.2 Anamnese (Etapa 2)

Fluxo curto em passos: objetivo e nível; restrições, **alergias e intolerâncias**; preferências alimentares; idade e condições que tiram do plano automático (menores, gestantes e quadros que exigem acompanhamento recebem a mensagem "procure um profissional" em vez de plano automático).

### 3.3 Hoje (Etapas 4 e 5)

```
+-----------------------------------+
| Quinta, 29/10        ● sincronizado|
|                                   |
| PRÓXIMA REFEIÇÃO  12:30           |
|  Arroz 150 g · Feijão 100 g ·     |
|  Frango 150 g                     |
|  [ Trocar ] [ Comi fora do plano ]|
|                                   |
| TREINO DE HOJE                    |
|  Peito e tríceps · 55 min         |
|  [ Começar ]                      |
|                                   |
| CHECK-IN da semana        [ Fazer ]|
| Dia livre  [ ativar ]             |
+-----------------------------------+
```

### 3.4 Cardápio, troca e "comi fora do plano" (Etapas 4 e 5)

```
Cardápio de hoje          Troca de alimento
 Café   ▸                  Arroz 150 g  →  escolha:
 Almoço ▾                   ( ) Purê de batata   210 g
  Arroz 150 g   [trocar]    ( ) Batata-doce      250 g
  Feijão 100 g              ( ) Mandioca         155 g
  Frango 150 g              Por que: mesmas calorias, dia
 Lanche ▸                   dentro de ±10%, proteína ok.
 Jantar ▸                   [ Confirmar ]
 [ Lista de compras ]
```

- **Sem conexão:** "Pendente de cálculo. Vamos calcular quando você voltar a ter sinal" (ADR-0006); a resposta volta como proposta que o usuário confirma.
- **Troca recusada:** mostra o motivo em linguagem simples (por exemplo, "contém glúten, que você marcou como restrição").
- **Comi fora do plano:** escolher alimento ou digitar texto; o app propõe o ajuste do resto do dia, nunca abaixo dos pisos de segurança.

### 3.5 Treino (Etapas 4 e 5, offline)

```
Treino · Peito e tríceps      Execução
 1 Supino reto   4x8           Supino reto   série 2 de 4
 2 Supino incl.  3x10          carga [ 60 kg ]  reps [ 8 ]
 3 Crucifixo     3x12          [ Concluir série ]   descanso 01:30
 [ Começar ]                   [ Trocar exercício ] (grupo de equivalência)
```

O treino em andamento fica salvo localmente e sobrevive a sessão expirada e a fechar o app.

### 3.6 Check-in (Etapa 7)

Perguntas curtas e periódicas (energia, sono, fome, adesão, como se sentiu); resultado: "ajustamos seu plano" com o motivo, ou "mantivemos". Sem pergunta que incentive restrição; sempre com saída "falar com um profissional".

### 3.7 Exames (Etapa 3)

```
Exames                      Enviar exame                 Conferir valores
 ● Sangue 01/12  ✓ pronto    [ Tirar foto ]               Tipo: Sangue  [mudar]
 ● Sangue 14/08  ✓ pronto    [ Escolher PDF ]             Ferro   [ 94 ] µg/dL
 ● Imagem 11/01  ✓ pronto    [ Digitar à mão ]            Ureia   [ 43 ] mg/dL
 ● Foto  25/08   processando  Usaremos IA externa? ver     ...
 [ + Novo exame ]             (precisa do consentimento)   [ Está correto ]
```

- **Processamento em segundo plano** com estado visível; arquivo original sempre guardado.
- **Conferência obrigatória** antes de salvar qualquer valor extraído; o tipo de exame é sempre confirmado pelo usuário.
- **Régua e comparativo:** cada marcador mostra a faixa de referência e a evolução entre datas; valor fora da faixa traz o aviso fixo "procure um médico", sem interpretação clínica.
- **Relatório em PDF** para levar ao médico, e lembrete de refazer o exame.
- Sem consentimento de IA, ou se revogado: digitação manual continua.

### 3.8 Mais: privacidade, assinatura, comunidade, missões

- **Privacidade:** consentimentos com liga e desliga e histórico de versões aceitas; **exportar meus dados**; **excluir minha conta** (para quem entrou pela Apple, revoga o token da Apple).
- **Assinatura (Etapa 6):** planos mensal, trimestral, semestral e anual; renovação automática; **cancelar só vale ao fim do período**, dito de forma clara; acesso de aluno grátis ou vitalício aparece como tal.
- **Comunidade (Etapa 9):** feed, comunidades, amigos, posts e posts promocionais com rótulo "promocional", opção de silenciar e denúncia. Nunca exibe exame, peso nem medidas.
- **Missões e medalhas (Etapa 7):** por constância e prática (treinos feitos, refeições registradas, check-ins), nunca por perda de peso, déficit calórico ou pular refeição.

### 3.9 Estados compartilhados

| Estado | Como aparece |
|---|---|
| Sem conexão | Faixa discreta "Offline: suas ações ficam salvas" |
| Pendente de envio | Ícone no item e contador em Mais > Sincronização |
| Falhou | Item com "tentar de novo" e motivo |
| Processando (exame) | Cartão com progresso e atualização automática |
| Sessão expirada | Pede login de novo sem perder o que está na fila |
| Plano mudou | Aviso "seu cardápio foi atualizado" ao sincronizar |

## 4. Backoffice (app web, `/admin`)

Acesso só para `ADMIN` e `MODERATOR`, com **conta própria e MFA obrigatório**. O backoffice **nunca mostra dado de saúde** (exame, anamnese, medidas).

```
Menu lateral
 ├─ Políticas e termos     (cadastrar texto, versionar, publicar; quem aceitou qual versão, só contagem)
 ├─ Usuários e acessos     (buscar por e-mail; aluno grátis e vitalício, com motivo e auditoria)
 ├─ Planos e assinaturas   (planos, preços, estado de cobrança, concessões)
 ├─ Catálogos              (alimentos, exercícios e grupos de equivalência, tipos de exame, marcadores)
 ├─ Posts promocionais     (para todos, um usuário ou uma comunidade; sem segmentar por dado de saúde)
 ├─ Moderação              (denúncias, posts, comunidades)
 ├─ Tarefas e filas        (tarefas assíncronas, DLQ, reprocessar)
 └─ Auditoria              (quem fez o quê e quando, sem dado de saúde no texto)
```

```
Políticas e termos                          Post promocional
 Termos de uso        v3 publicada          Público: ( ) todos ( ) um usuário ( ) comunidade
 Política de privacid v2 publicada          Título [______]  Texto [__________]
 Dado de saúde        v1 publicada          Canais: [x] no app  [ ] notificação*
 IA em exames         v1 publicada          *só para quem aceitou promoções
 [ + Nova versão ]  [ Publicar ]            Limite de frequência: 1/semana
 Alterar o texto cria nova versão;          [ Pré-visualizar ] [ Enviar ]
 o usuário aceita de novo.
```

## 5. Perguntas para o Dênis

1. A barra inferior (Hoje, Cardápio, Treino, Exames, Mais) está boa, ou você quer outra organização (por exemplo, Exames dentro de Mais)?
2. O "Hoje" como primeira tela, mostrando refeição e treino, é o que você imagina?
3. Algum fluxo que ficou de fora e que você considera central para o primeiro uso (o seu uso próprio, na Etapa 5)?
4. Quer que as telas aprovadas virem protótipo no Figma, ou o texto basta até a spec de cada etapa?

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
