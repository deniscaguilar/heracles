# P1: Sincronização offline

- **Prova:** P1 da Etapa 0 (`specs/platform/stage-0-proofs/spec.md`); ADR-0005
- **Estado:** a **opção A foi provada** (sem aparelho e no emulador Android). **As opções B (WatermelonDB) e C (PowerSync) NÃO foram executadas**: só foram avaliadas pela documentação. **Decisão do Dênis (29/10/2026): seguir com a opção A, de forma provisória** (ADR-0005 atualizada).

## 1. Pergunta

Qual ferramenta de sincronização offline atende os invariantes do ADR-0005 (id gerado no cliente, log de mutações idempotente, política de conflito por tipo de dado, dado criptografado no aparelho e apagado ao sair) com o menor esforço para um desenvolvedor só?

## 2. Opções

| | Opção | Como foi avaliada |
|---|---|---|
| A | `expo-sqlite` + sincronização própria | **Implementada e medida** |
| B | WatermelonDB | Só documentação |
| C | PowerSync | Só documentação |

## 3. Método da opção A

Protótipo descartável em `prototypes/sync/` (detalhes no README da pasta):
- **Servidor de teste** em Node (só `node:http` e `node:sqlite`), não em Kotlin como o plano previa. Ele aplica mutações de forma idempotente por `mutation_id`, mantém um log de mudanças com cursor e resolve o perfil por **última escrita por campo com o relógio do servidor**.
- **Cliente** com fila de mutações local (outbox), escrita local imediata, envio em lotes e pull por cursor.
- **Cenários automáticos (6, todos verdes):** C1 criação offline e reenvio; C2 mesma mutação duas vezes sem duplicar; C3 perfil editado em dois aparelhos converge; C4 sessão expirada no meio da fila (a fila e o treino em andamento são preservados e retomam depois do novo login); C5 sair da conta apaga tudo; C6 10 mil registros.
- **No aparelho:** emulador Android 15 (arm64) com Expo Go, SDK 58 (RN 0.88), `expo-sqlite` 58. O mesmo cliente, com adaptador próprio para o `expo-sqlite`.

## 4. Resultados (opção A)

| Medida | Resultado |
|---|---|
| Cenários C1 a C5 (conflito, duplicidade, sessão expirada, sair da conta) | todos verdes |
| Criar 10 mil registros no aparelho (escrita local, uma transação) | 1,4 a 1,6 s |
| Contar 10 mil registros no aparelho | 8 a 9 ms |
| Enviar 10 mil mutações ao servidor, em lotes de 500 | **0,75 s** (antes de agrupar em transação: 11,7 s) |
| Baixar 10 mil registros num aparelho novo | **0,22 s** (antes: 11,0 s) |
| Sair da conta apaga os dados locais | confirmado |

A primeira medida no aparelho foi ruim por **erro do meu cliente**: gravava linha por linha, sem transação. Agrupar as gravações em transação reduziu o envio de 11,7 s para 0,75 s e o pull de 11,0 s para 0,22 s. As duas medidas ficam registradas.

## 5. Opções B e C (documentação, sem prova)

| | A: expo-sqlite + próprio | B: WatermelonDB | C: PowerSync |
|---|---|---|---|
| Roda no Expo Go | **Sim** (provado) | Não: módulo nativo, exige build de desenvolvimento | Exige build de desenvolvimento (adaptador nativo) |
| Protocolo de sincronização | O nosso (simples, já provado) | Protocolo próprio (`pullChanges` e `pushChanges`); o servidor precisa implementá-lo | Serviço PowerSync entre o app e o banco; conector no app |
| Infraestrutura extra | Nenhuma | Nenhuma | **Novo serviço** (PowerSync Cloud, pago, ou auto-hospedado) e banco de origem configurado para replicação |
| Criptografia local | SQLCipher por opção do plugin do `expo-sqlite` (**a provar** num build de desenvolvimento; a documentação consultada cobre até o SDK 57) | Parâmetro de senha do adaptador (SQLCipher) via PR do projeto, com configuração nativa; sem guia oficial para Expo | Suportada pelo pacote `@powersync/op-sqlite` |
| Conflito | Nossa regra por tipo de dado (provada) | Por coluna; regra no servidor | "Última escrita vence" por padrão; regras mais ricas no backend próprio |
| Esforço no backend Kotlin | Pequeno: 2 endpoints (envio e pull) | Médio: implementar o protocolo da biblioteca | Médio: conector e sincronização com o serviço |
| Regra do projeto | Sem dependência nova de peso | Dependência nova | **Serviço novo exige aprovação** (CLAUDE.md, seção 2) |

Fontes (buscas de 29/10/2026, em parte de terceiros): documentação do Expo (`expo-sqlite`), documentação e PR do WatermelonDB, documentação do PowerSync (criptografia, backend e conflito). Nada disso foi testado.

## 6. Leitura provisória (não é decisão)

A opção A **já cumpre os invariantes do ADR-0005 que dá para provar sem um build nativo**, no Expo Go, sem serviço novo e com um backend pequeno. O que ela ainda não provou é a **criptografia local** (SQLCipher), que exige um build de desenvolvimento. B e C não foram executadas, então **não há base para escolhê-las nem para descartá-las com números**.

## 7. Limites

- **Servidor em Node, não em Kotlin:** a opção A **não prova o esforço real no backend Kotlin**; o protocolo é pequeno (dois endpoints), mas a estimativa de esforço é minha.
- **Emulador, não aparelho real**, e só Android; iOS (armazenamento seguro, Keychain) não foi testado porque o Xcode não está instalado.
- **Volume:** 10 mil registros de um tipo, um usuário, um aparelho por vez. Sem concorrência real e sem rede lenta.
- Os tempos incluem rede local (emulador para o computador) e não representam 4G.
- O `npm install` do app de teste avisou de vulnerabilidades em dependências transitivas; é um protótipo descartável, e o app real seguirá a política do CLAUDE.md (seção 7).

## 8. Decisão (Dênis, 29/10/2026)

**Opção A, provisória.** A criptografia local (SQLCipher) e as opções B e C ficam como pendências registradas no ADR-0005. Reavaliar B ou C só pelos gatilhos do ADR (SQLCipher inviável, protocolo caro de manter ou novo requisito de sincronização).

## 9. Pendências

- Provar SQLCipher num build de desenvolvimento.
- Estimar o protocolo no backend Kotlin quando a spec da fundação existir.
