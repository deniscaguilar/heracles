# ADR-0008: Provedor de identidade (Amazon Cognito, provisório)

- **Status:** Aprovada (provisória, com pendências)
- **Data:** 2026-10-29
- **Origem:** Etapa 0, prova P7 (T5.3); `docs/stage-0/p7-identidade.md`; ADR-0001 (port `IdentityProvider`), ADR-0003 e ADR-0005

## Contexto

O produto precisa de login, MFA e recuperação de conta para o app mobile (React Native) e o app web (React), e o backend precisa validar quem chama. Já está decidido que o backend é *resource server* OIDC atrás de um port `IdentityProvider`, com provedor a definir (Cognito era candidato). Restrições: desenvolvedor solo, custo baixo, infraestrutura já na AWS (ADR-0002), nenhum dado de saúde fora do banco do produto, e serviço novo exige aprovação (CLAUDE.md, seção 2).

A pesquisa (`docs/stage-0/p7-identidade.md` e o comparativo no Notion) é **quase toda de fontes de terceiros, com números que se contradizem**; nada foi confirmado em página oficial. Por isso a decisão é provisória.

## Decisão

- **Provedor: Amazon Cognito (user pool)**, usado por **OIDC com Authorization Code + PKCE**, no app mobile e no app web. O backend só valida o JWT (assinatura, emissor, público e expiração) e não conhece o Cognito além do adaptador do port `IdentityProvider`.
- **Formas de login do cliente (`USER`):** (1) **conta própria** com e-mail e senha, criada no app; (2) **Entrar com Google**; (3) **Entrar com Apple**. Os dois sociais entram como provedores federados do Cognito (OIDC), no mesmo fluxo com PKCE e pelo login gerenciado no navegador do sistema, sem SDK nativo de cada provedor no início. Regras:
  - **Apple é obrigatória quando o app oferece Google** no iOS (regra da App Store para login de terceiros; conferir o texto vigente na hora de publicar). Por isso os três saem juntos.
  - **Escopos mínimos** (`openid`, e-mail e nome). Nenhum escopo que leia dado do Google ou da Apple além da identidade.
  - **Cadastro com login social não dispensa o consentimento do produto:** o aceite dos termos e o consentimento de dado de saúde (módulo `consent`) são pedidos **antes de qualquer dado de saúde**, qualquer que seja a forma de entrar.
  - **Vínculo de contas:** a mesma pessoa pode entrar por e-mail e senha e depois por Google ou Apple. **Não há vínculo automático** no início (cada forma de login cria uma identidade distinta no `platform`); unir contas é um fluxo explícito, a especificar. Isso evita que alguém tome uma conta só por registrar o mesmo e-mail em outro provedor.
  - **E-mail retransmitido da Apple** ("ocultar e-mail"): o endereço pode ser um alias; o domínio de envio do produto precisa estar registrado na Apple para as mensagens chegarem.
  - **MFA não se aplica a quem entra por provedor social** (a segurança é a do Google ou da Apple). Por isso **`ADMIN` e `MODERATOR` entram só com conta própria e MFA obrigatório**, nunca por login social.
  - **Exclusão de conta:** para quem entrou pela Apple, a exclusão também **revoga o token da Apple** (exigência da App Store), além de remover o usuário no `platform` e no Cognito.
- **O Cognito guarda só o necessário para autenticar:** identificador (`sub`), e-mail e, se preciso, nome. **Nenhum dado de saúde, nenhum atributo personalizado de domínio.** Perfis `USER`, `ADMIN` e `MODERATOR` e permissões ficam no banco do `platform`, não em grupos do Cognito; o token traz só `sub`.
- **MFA** por aplicativo autenticador (TOTP), opcional para o usuário e **obrigatório para `ADMIN` e `MODERATOR`**. Login sem senha (passkeys) fica para quando o produto precisar e a camada contratada permitir.
- **Recuperação de conta** por código no e-mail (envio pelo Amazon SES). SMS só se houver necessidade e com o custo conhecido.
- **Sessão:** token de acesso de vida curta e *refresh token* de vida longa; o app renova em segundo plano e, se a sessão expirar, **não perde a fila local** (provado na P1, cenário C4). Sair da conta apaga os dados do aparelho (ADR-0005).
- **Exclusão e exportação (LGPD):** a exclusão do titular remove o usuário no `platform` e depois no Cognito pela API administrativa; a exportação junta o que o Cognito tem (identificador e e-mail) ao que o produto tem.
- **Local e testes:** o ambiente local usa um servidor OIDC de mentira no `docker compose`, atrás do mesmo port; o Cognito real só entra em ambiente na AWS.
- **Troca possível:** como tudo passa pelo port e o Cognito guarda pouco, trocar de provedor custa mover usuários e senhas (reautenticação ou migração gradual); esse custo é aceito.

## Pendências (a confirmar antes de produção)

0. **Contas de desenvolvedor e consentimento OAuth:** Apple Developer Program (pago) e o *Service ID* da Apple, e um projeto no Google Cloud com a tela de consentimento OAuth e as credenciais. Custo e prazo de aprovação a confirmar; o Dênis cria as contas.
1. **Região:** confirmar a disponibilidade do user pool em São Paulo e onde ficam os dados do usuário; se não for em São Paulo, avaliar a transferência internacional com o jurídico (pendência J2 em `p8-pendencias.md`).
2. **Preço oficial** por camada e a regra exata de usuário ativo (a pesquisa cita Lite, Essentials e Plus, com faixa gratuita para login direto, mas por fontes de terceiros).
3. **Idioma:** textos em pt-BR no login gerenciado ou telas de login próprias no app.
4. **E-mail e SMS:** SES na região escolhida, domínio verificado e custo de SMS, se houver.
5. **Exclusão e exportação por API:** confirmar como ficam no Cognito.
6. **Prova prática:** um login PKCE real no app mobile e no app web contra um user pool de teste (gera custo; pede autorização).

## Alternativas

- **Keycloak autohospedado (EC2):** controle total, sem custo por usuário; é um **serviço novo** para operar, atualizar e proteger sozinho. Plano B se o Cognito não servir.
- **Auth0, Clerk, Firebase Identity Platform:** gerenciados e maduros, mas com MFA em camada paga ou, no caso do Firebase, sob o ecossistema Google; dados fora do Brasil e custo maior; menos aderentes ao nosso desenho em AWS.
- **Supabase Auth, Ory (Kratos e Hydra) ou Zitadel:** open source, mas outro serviço a operar (Ory exige dois) e sem confirmação de PKCE no mobile.
- **Só conta própria, sem login social:** mais simples e sem Apple Developer no início, mas aumenta o atrito de cadastro; descartado como único caminho, mas pode ser a primeira entrega se as contas de desenvolvedor demorarem.
- **Construir o login no backend:** descartado; segurança de credenciais é o pior lugar para economizar.

## Consequências

- Sem serviço novo e com baixa operação; a conta já é a da AWS.
- Dependência da AWS para identidade; mitigada pelo port.
- A regra de "perfis no banco, token só com `sub`" mantém o provedor sem dado de domínio e facilita a troca.
- O custo real só se confirma na pendência 2; com poucos usuários ativos tende a ficar na faixa gratuita.

## Gatilhos para reavaliar

O user pool não estar disponível na região exigida, a faixa gratuita não cobrir o volume, o idioma ou a experiência de login não atenderem, ou a troca de provedor virar necessidade de negócio (SSO corporativo, por exemplo).

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
