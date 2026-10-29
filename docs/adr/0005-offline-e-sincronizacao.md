# ADR-0005: App offline e sincronização

- **Status:** Aprovada (invariantes); ferramenta de sincronização: **opção A, provisória** (expo-sqlite + sincronização própria), decidida em 29/10/2026 pela prova P1
- **Data:** 2026-10-29
- **Origem:** roadmap v5, Etapas 0 e 5; decisão fechada em 29/10/2026: offline para tudo do dia, exceto o cálculo da troca de alimento, que exige conexão (ADR-0006)

## Contexto

O app precisa funcionar sem internet no que importa durante o dia: ler o treino e o cardápio, registrar treino e refeição, responder check-in e enfileirar exames. A troca de alimento e o "comi fora do plano" exigem conexão (ADR-0006) e ficam como ação pendente quando offline. Isso afeta o modelo de dados e a API desde a fundação, e consertar depois é caro. Não achamos comparação direta e confiável entre as opções de sincronização para React Native.

## Decisão

**Invariantes já decididos** (valem para qualquer ferramenta):
- **Identificadores gerados no cliente** (UUID), para criar registros offline sem colisão.
- **Log de mutações:** o app grava cada mudança numa fila local e a envia quando há conexão. O servidor aplica de forma **idempotente** (mesmo `mutation_id` duas vezes não duplica).
- **Política de conflito por tipo de dado:**
  - registros de uso (treino concluído, refeição registrada, check-in): **somente acréscimo**, sem conflito;
  - preferências e perfil: última escrita vence, com carimbo de tempo do servidor;
  - **plano, regras e catálogo:** o servidor é a autoridade; o app só consome;
  - cobrança e assinatura: só o servidor.
- **Troca de alimento e "comi fora do plano":** o cálculo roda só no servidor (ADR-0006). Offline, o app registra a intenção como **ação pendente** (alimento escolhido de um cache leve do catálogo, ou texto livre; definido na spec), mostra "pendente de cálculo" e a envia ao reconectar. O resultado volta como proposta que o usuário confirma.
- **Exame offline:** o arquivo e a intenção ficam em fila local; o envio ao S3 e a extração acontecem quando houver conexão.
- **Segurança do dado no aparelho:** dado de saúde guardado localmente é criptografado, com chave no armazenamento seguro do sistema, e **apagado ao sair da conta**. Sessão expirada não pode expor dado em cache nem perder o progresso do treino em andamento.
- **Estado visível:** o app mostra o que está pendente de envio e o que falhou.

**Ferramenta de sincronização (decisão de 29/10/2026, provisória):** **`expo-sqlite` com sincronização própria** (opção A da prova P1, `docs/stage-0/p1-sincronizacao.md`). Fica provisória porque a prova cobriu só a opção A:
- **O que a prova mostrou:** fila de mutações local, envio idempotente por `mutation_id`, pull por cursor, conflito de perfil por última escrita com o relógio do servidor, sessão expirada sem perda de fila ou de treino em andamento e apagar tudo ao sair; 10 mil registros no emulador Android 15 com envio em 0,75 s e pull em 0,22 s (gravações em lote dentro de transação), no Expo Go e sem serviço novo.
- **O que NÃO foi provado (pendências, sem prazo):** (1) **criptografia local com SQLCipher** (opção do plugin do `expo-sqlite`, exige build de desenvolvimento): obrigatória antes de qualquer dado de saúde real no aparelho de terceiros; (2) **WatermelonDB e PowerSync não foram executados**, só avaliados pela documentação; (3) o esforço real do protocolo no **backend Kotlin** (o servidor de teste é Node); (4) iOS (o Xcode não está instalado).
- **Gatilhos para reavaliar B ou C:** a SQLCipher não funcionar no `expo-sqlite` do SDK em uso, o protocolo próprio ficar caro de manter, ou aparecer requisito de sincronização que ele não cubra (por exemplo, dados compartilhados entre usuários).
- **Regras que a spec da Etapa 5 herda:** gravações em lote sempre em transação; o cliente de sincronização fica atrás de um adaptador de armazenamento; o servidor aplica mutação por `mutation_id` e responde `duplicate` para repetidas.

## Alternativas

- **Recálculo da troca no aparelho:** descartada nesta versão (ADR-0006); reavaliar se o uso real mostrar que a troca offline é necessária.
- **Sincronização totalmente própria sobre SQLite:** controle total, esforço alto.
- **Serviço gerenciado de sincronização:** reduz código, adiciona dependência e custo.

## Consequências

- O backend expõe endpoints de sincronização e aceita mutações idempotentes.
- Testes de conflito, de reconexão e de sessão expirada são obrigatórios.
- O treino e o cardápio guardados no aparelho têm versão; ao sincronizar, vale a do servidor e o app avisa se o plano mudou.

## Aprovação

- [x] Aprovada por: Dênis, via Plannotator (Approve), em 29/10/2026
