# P6: Wearables e bioimpedância integrada

- **Prova:** P6 da Etapa 0 (`specs/platform/stage-0-proofs/spec.md`); tarefas T4.1 e T4.2
- **Estado:** pesquisa em documentação (T4.1) e teste com **dados simulados no Android** (T4.2) em 29/10/2026. **iOS não foi testado** (o Xcode não está instalado) e **não há aparelho de bioimpedância** (decisão de 29/10/2026).

## 1. Pergunta

Dá para ler, no app React Native, dados de saúde (peso, gordura corporal, massa magra, metabolismo basal, passos, frequência cardíaca, treinos) que outros aplicativos e aparelhos gravam nas plataformas de saúde do celular, e assim receber a **medição de bioimpedância** sem integrar o aparelho diretamente?

## 2. Matriz (T4.1)

Fontes: documentação do Android e do Google Play, documentação da Apple e das bibliotecas, consultadas por busca em 29/10/2026 (em parte de terceiros). Onde a busca não confirmou, está marcado.

| | Android: Health Connect | iOS: HealthKit |
|---|---|---|
| Disponibilidade | Nativo no Android 14 ou superior; nas versões anteriores, é um aplicativo da Play Store. Provado no emulador (Android 15) | Nativo no iPhone |
| Dados de que precisamos | Peso, gordura corporal, massa magra, metabolismo basal, altura, passos, frequência cardíaca, sessão de exercício. **Todos aceitos no teste** (seção 3) | Tipos equivalentes do HealthKit (peso, gordura corporal, massa magra, energia basal, passos, frequência cardíaca, treinos). **Lista exata não confirmada na pesquisa**; conferir na documentação da Apple |
| Permissões | Uma por tipo e por direção, no formato `android.permission.health.READ_<TIPO>` e `WRITE_<TIPO>`; **histórico além de 30 dias** exige a permissão `READ_HEALTH_DATA_HISTORY` (Android 14 ou superior) e leitura em segundo plano exige `READ_HEALTH_DATA_IN_BACKGROUND` | Autorização por tipo, pedida ao usuário; textos `NSHealthShareUsageDescription` (leitura) e `NSHealthUpdateUsageDescription` (escrita) no `Info.plist`; capacidade HealthKit no Xcode |
| Segundo plano | Permissão própria (acima) | Entrega em segundo plano exige o *entitlement* de HealthKit para entrega em segundo plano; **o armazenamento fica criptografado com o aparelho bloqueado**, então não há leitura nesse estado |
| Publicação | **Formulário de apps de saúde** no Play Console com justificativa de cada tipo; política de privacidade no app e no console; política do Google restringe o uso do dado (publicidade e venda) | **Revisão da App Store mais rigorosa**; a diretriz 5.1.3 veda publicidade e compartilhamento do dado de saúde. Conferir o texto vigente |
| Biblioteca React Native | `react-native-health-connect` 4.1.3 (plugin do Expo incluído, `minSdk` 26); **não roda no Expo Go**, exige build de desenvolvimento | `@kingstinct/react-native-healthkit` (plugin do Expo; **não roda no Expo Go**; exige `react-native-nitro-modules`). Há relato, sem solução na pesquisa, de o pedido de permissão não aparecer em build de desenvolvimento |
| Web (PWA) | **Não existe** | **Não existe** |

**Consequência:** a integração é **só do app mobile**. O app web não lê dado de saúde de plataforma.

## 3. Teste com dados simulados no Android (T4.2)

Protótipo descartável em `prototypes/health-devices/app/` (Expo SDK 58, `react-native-health-connect` 4.1.3) em **build de desenvolvimento** (APK de depuração), rodando no emulador Android 15.

| Verificação | Resultado |
|---|---|
| Health Connect disponível no emulador | sim (`SDK_AVAILABLE`) |
| `initialize` | verdadeiro |
| Permissões concedidas (por `adb pm grant`, sem a tela de consentimento) | leitura e escrita de peso, gordura corporal, massa magra, metabolismo basal, altura, passos, frequência cardíaca e sessão de exercício |
| Gravar 5 registros simulados (peso, gordura, massa magra, metabolismo basal, passos) | 5 gravados |
| Ler de volta | 5 de 5; a origem do dado (`dataOrigin`) é o pacote do próprio app |

**O que isso prova:** o app consegue ler e gravar os tipos de dado de que precisamos no Health Connect, com a biblioteca e a configuração do Expo escolhidas, e a origem do registro vem identificada.

**Achados práticos:**
- A configuração do plugin sugerida pela biblioteca (`compileSdkVersion` 36) **não compila** com o Expo SDK 58: as dependências exigem 37.
- `insertRecords` aceita **um tipo de registro por chamada**.
- O build de desenvolvimento do Android funciona nesta máquina; isso **destrava** também a prova de criptografia local (SQLCipher) da P1.

## 4. Bioimpedância por plataforma de saúde

- O aplicativo da InBody informa na App Store que sincroniza composição corporal com o Apple Health; **no Health Connect não foi confirmado**. Balanças de consumo costumam gravar peso, gordura corporal e massa magra nas plataformas; marcas específicas não foram confirmadas.
- O aparelho do Dênis (InBody 370S, profissional) entrega o resultado por **código QR e site da InBody**, não por Bluetooth; para chegar ao app o caminho é o aplicativo da InBody gravar na plataforma de saúde, o que **não foi verificado**.
- Quem grava é outro aplicativo: o registro traz a origem, e o app deve **mostrar a origem e o método** (medido, automático ou digitado) e nunca tratar o dado como verdade clínica. O enquadramento regulatório (ANVISA) segue como pendência jurídica J9.

## 5. Decisão

- **Viável no Android** (provado com dados simulados) e **provavelmente viável no iOS** (documentação), **só no app mobile**.
- A leitura de composição corporal por plataforma de saúde é o **caminho recomendado** para receber a bioimpedância; a integração direta por Bluetooth com aparelhos fica fora (sem aparelho disponível).
- Entra na **Etapa 7** como funcionalidade opcional, com consentimento próprio para ler dado de plataforma de saúde, dado só do próprio usuário e nada compartilhado.

## 6. Limites

- Dados **simulados e escritos pelo próprio app**; **não foi lido dado gravado por outro aplicativo** (por exemplo, a InBody).
- A tela de consentimento do Health Connect foi ignorada (permissões por `adb`); o fluxo real de pedido de permissão não foi testado.
- **iOS sem teste.** Histórico além de 30 dias e leitura em segundo plano também não.
- Emulador, não aparelho; relógio ou pulseira reais não foram usados.
- As regras de publicação foram conferidas só em fontes de terceiros ou de forma parcial.

## 7. Pendências

- Testar no iOS quando houver Xcode ou um Mac com Xcode (pedido de permissão e tipos do HealthKit).
- Verificar com o aplicativo real da InBody se o resultado chega ao Health Connect e ao Apple Health.
- Declaração de apps de saúde no Play Console e revisão da App Store: preparar na Etapa 7.
- Parecer jurídico sobre bioimpedância e dispositivo médico (J9) e sobre dado de plataforma de saúde (J1).
