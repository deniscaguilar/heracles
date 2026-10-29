# ADR-0005: App offline e sincronização

- **Status:** Aprovada (invariantes); a ferramenta de sincronização será decidida pela prova da Etapa 0
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

**A decidir na Etapa 0, por prova em protótipo** (Expo SQLite com sincronização própria, WatermelonDB, PowerSync): critérios de escolha são resolução de conflito, desempenho com o volume de registros de uso, suporte a criptografia local, esforço de implantar o protocolo no backend Kotlin e custo de manutenção solo. A ferramenta escolhida substitui este rascunho.

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
