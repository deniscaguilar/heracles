# ADR-0003: Isolamento por usuário (RLS) e auditoria append-only

- **Status:** Aprovada
- **Data:** 2026-10-29
- **Origem:** roadmap v5, Etapas 1 e 2; padrão validado no Sistema da Clínica (ADR-0007 e ADR-0010 de lá), adaptado de "clínica" para "usuário"

## Contexto

O app guarda dado sensível de saúde (anamnese, exames, medidas, check-ins) de pessoas físicas. Um erro de consulta não pode expor o dado de uma pessoa a outra. Diferente do Sistema da Clínica, aqui não há multi-clínica: o isolamento é **por usuário**, com uma parte compartilhada de propósito (comunidade).

## Decisão

**Papéis do banco.** `app_migrator` é dono dos schemas e roda o Flyway. `app_runtime` é usado pela aplicação, sem `BYPASSRLS` e sem ser dono de tabela. Os papéis são criados pelo ambiente (init local, provisionamento do RDS); as migrações só fazem `GRANT`. Duas `DataSource`s: migração e aplicação.

**Contexto por transação.** Um gerenciador de transação próprio executa `set_config(..., true)` no início de cada transação, então o valor vale só nela:
- `app.user_id`: o usuário dono da requisição.
- `app.scope`: `user` (padrão), `admin` (backoffice) ou `system` (workers e jobs).

**Tabelas de dado de saúde** (anamnese, exames, marcadores, medidas, check-ins, refeições registradas): política padrão `user_id = app.user_id`, com `USING` e `WITH CHECK`, RLS `ENABLE` e `FORCE`. Sem variável definida, nenhuma linha (falha fechada). **Nenhum escopo, inclusive `admin` e `system`, lê essas tabelas fora do contexto do próprio usuário.** Workers processam uma tarefa dentro de `UserScope.runAs(userId)` com o `userId` vindo da mensagem.

**Tabelas compartilhadas** (comunidade: posts, comunidades, amizades, posts promocionais): políticas próprias de visibilidade (autor, amigos, membros da comunidade, bloqueios), definidas na spec do módulo. O escopo `admin` pode moderar conteúdo da comunidade e gerir catálogos, políticas, planos e concessões; nunca dado de saúde.

**Tabelas de catálogo** (exercícios, alimentos, tipos de exame, missões): leitura por qualquer usuário autenticado, escrita só por `admin`.

**Garantias em teste.**
- Teste de catálogo que reprova tabela com `user_id` sem RLS habilitada, forçada e com política.
- `UserIsolationIT` genérico por tabela: sem contexto não vê nada; um usuário não lê, altera nem apaga linha de outro; `WITH CHECK` recusa gravar no nome de outro; o escopo `admin` não abre tabela de saúde.
- Teste de vazamento de contexto entre transações no pool (várias threads alternando usuários).

**Auditoria.**
- Tabela `audit_event` **append-only**: o papel da aplicação só tem `SELECT` e `INSERT`, e gatilhos recusam `UPDATE`, `DELETE` e `TRUNCATE`.
- Cada registro guarda data e hora, usuário, ação, entidade, resultado e origem (canal e IP). `details` aceita só **nomes de campo**, nunca valores.
- Sucesso entra na mesma transação da ação; negação e falha entram em transação própria e sobrevivem à reversão.
- Auditam-se, no mínimo: login, leitura e escrita de dado de saúde, aceite e revogação de consentimento, envio de exame a IA, concessão de acesso (aluno grátis e vitalício), lançamento de post promocional e ações de moderação.

**Identidade.** O `sub` do token (provedor OIDC) mapeia para o usuário no banco da aplicação; perfis e permissões ficam no banco. Perfis iniciais: `USER` e os de operação (`ADMIN`, `MODERATOR`), com permissões fixas no código (a definir na spec).

## Consequências

- Um bug de consulta não vaza dado de saúde entre usuários, porque a barreira está no banco.
- Custo: todo módulo com dado de usuário segue a convenção e escreve o teste de isolamento.
- Risco: a obtenção da conexão no início da transação depende do suporte do Hibernate; o Sistema da Clínica provou o padrão (estender o gerenciador de transação do Spring). Se o app usar só JDBC, o risco some.
- Altera RLS e autenticação: exige esta ADR aprovada (CLAUDE.md, seção 10).

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
