# P6: Wearables e bioimpedância integrada

- **Prova:** P6 da Etapa 0 (`specs/platform/stage-0-proofs/spec.md`); tarefas T4.1 e T4.2
- **Estado:** pesquisa em documentação (T4.1) e teste com **dados simulados no Android e no iOS** (T4.2), este no simulador do iOS 26.5, em 29/10/2026. **Não há aparelho de bioimpedância** (decisão de 29/10/2026).

## 1. Pergunta

Dá para ler, no app React Native, dados de saúde (peso, gordura corporal, massa magra, metabolismo basal, passos, frequência cardíaca, treinos) que outros aplicativos e aparelhos gravam nas plataformas de saúde do celular, e assim receber a **medição de bioimpedância** sem integrar o aparelho diretamente?

## 2. Matriz (T4.1)

Fontes: documentação do Android e do Google Play, documentação da Apple e das bibliotecas, consultadas por busca em 29/10/2026 (em parte de terceiros). Onde a busca não confirmou, está marcado.

| | Android: Health Connect | iOS: HealthKit |
|---|---|---|
| Disponibilidade | Nativo no Android 14 ou superior; nas versões anteriores, é um aplicativo da Play Store. Provado no emulador (Android 15) | Nativo no iPhone |
| Dados de que precisamos | Peso, gordura corporal, massa magra, metabolismo basal, altura, passos, frequência cardíaca, sessão de exercício. **Todos aceitos no teste** (seção 3) | Peso, gordura corporal, massa magra, energia basal e passos: **todos existem e foram gravados ou lidos no teste do simulador** (seção 3.1; peso e gordura ficaram sem permissão nesse teste). Frequência cardíaca e treinos: não testados |
| Permissões | Uma por tipo e por direção, no formato `android.permission.health.READ_<TIPO>` e `WRITE_<TIPO>`; **histórico além de 30 dias** exige a permissão `READ_HEALTH_DATA_HISTORY` (Android 14 ou superior) e leitura em segundo plano exige `READ_HEALTH_DATA_IN_BACKGROUND` | Autorização por tipo, pedida ao usuário; textos `NSHealthShareUsageDescription` (leitura) e `NSHealthUpdateUsageDescription` (escrita) no `Info.plist`; capacidade HealthKit no Xcode |
| Segundo plano | Permissão própria (acima) | Entrega em segundo plano exige o *entitlement* de HealthKit para entrega em segundo plano; **o armazenamento fica criptografado com o aparelho bloqueado**, então não há leitura nesse estado |
| Publicação | **Formulário de apps de saúde** no Play Console com justificativa de cada tipo; política de privacidade no app e no console; política do Google restringe o uso do dado (publicidade e venda) | **Revisão da App Store mais rigorosa**; a diretriz 5.1.3 veda publicidade e compartilhamento do dado de saúde. Conferir o texto vigente |
| Biblioteca React Native | `react-native-health-connect` 4.1.3 (plugin do Expo incluído, `minSdk` 26); **não roda no Expo Go**, exige build de desenvolvimento | `@kingstinct/react-native-healthkit` 16.2.0 (plugin do Expo; **não roda no Expo Go**; exige `react-native-nitro-modules`). **No teste, o pedido de permissão apareceu normalmente** no simulador, apesar do relato de um problema em build de desenvolvimento |
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

### 3.1 iOS (HealthKit, simulador do iOS 26.5)

Mesmo protótipo, com `@kingstinct/react-native-healthkit` 16.2.0 e `react-native-nitro-modules` 0.37.1 (dependências aprovadas pelo Dênis), compilado com o Xcode 26.6 para um iPhone 17 virtual.

| Verificação | Resultado |
|---|---|
| App Expo compila para iOS e roda no simulador, em build de desenvolvimento | sim |
| HealthKit disponível | sim |
| Tela de permissão do HealthKit aparece (em pt-BR, com os textos de uso do `Info.plist`) | sim |
| `requestAuthorization` | retornou verdadeiro |
| Gravar 5 amostras simuladas | **3 gravadas** (massa magra, energia basal e passos); **2 recusadas** (peso e gordura corporal): erro `Not authorized` |
| Ler de volta | 3 de 3 gravadas, com os valores certos e a origem igual ao pacote do app |
| Peso e gordura corporal: leitura | **0 registros, sem erro** |

**Achados do iOS:**
- **A permissão é por tipo, e o usuário pode liberar só alguns.** Os dois tipos recusados provavelmente ficaram desligados na tela de permissão (a lista tem rolagem e o toque liberou só parte dela). Isso mostra o comportamento real que o app terá de tratar.
- **Negação de leitura é silenciosa:** sem permissão de leitura, a consulta devolve lista vazia **sem erro**, igual a "não há dado". O app não consegue distinguir os dois casos; deve explicar na tela que só vê o que o usuário liberou e como mudar isso (Ajustes > Saúde).
- **Negação de escrita dá erro** (`Not authorized`), então a gravação precisa de tratamento por tipo.
- Os dois tipos recusados não voltam a perguntar: para liberar depois, o usuário muda em Ajustes, no app Saúde.

**Achados práticos:**
- A configuração do plugin sugerida pela biblioteca (`compileSdkVersion` 36) **não compila** com o Expo SDK 58: as dependências exigem 37.
- `insertRecords` aceita **um tipo de registro por chamada**.
- O build de desenvolvimento do Android funciona nesta máquina; isso **destrava** também a prova de criptografia local (SQLCipher) da P1.

## 4. Bioimpedância por plataforma de saúde

- O aplicativo da InBody informa na App Store que sincroniza composição corporal com o Apple Health; **no Health Connect não foi confirmado**. Balanças de consumo costumam gravar peso, gordura corporal e massa magra nas plataformas; marcas específicas não foram confirmadas.
- O aparelho do Dênis (InBody 370S, profissional) entrega o resultado por **código QR e site da InBody**, não por Bluetooth; para chegar ao app o caminho é o aplicativo da InBody gravar na plataforma de saúde, o que **não foi verificado**.
- Quem grava é outro aplicativo: o registro traz a origem, e o app deve **mostrar a origem e o método** (medido, automático ou digitado) e nunca tratar o dado como verdade clínica. O enquadramento regulatório (ANVISA) segue como pendência jurídica J9.

## 5. Decisão

- **Viável no Android e no iOS** (provado com dados simulados; no iOS, no simulador), **só no app mobile**. O app precisa tratar a permissão por tipo e a negação silenciosa de leitura do iOS.
- A leitura de composição corporal por plataforma de saúde é o **caminho recomendado** para receber a bioimpedância; a integração direta por Bluetooth com aparelhos fica fora (sem aparelho disponível).
- Entra na **Etapa 7** como funcionalidade opcional, com consentimento próprio para ler dado de plataforma de saúde, dado só do próprio usuário e nada compartilhado.

## 6. Limites

- Dados **simulados e escritos pelo próprio app**; **não foi lido dado gravado por outro aplicativo** (por exemplo, a InBody).
- A tela de consentimento do Health Connect foi ignorada (permissões por `adb`); o fluxo real de pedido de permissão não foi testado.
- **iOS só no simulador:** o teste não passou por aparelho real, e a leitura de dados de outro aplicativo (como a InBody) também não. Peso e gordura corporal ficaram sem permissão no teste do iOS (3 de 5 tipos gravados); a repetição com todos os tipos liberados continua pendente. Histórico além de 30 dias e leitura em segundo plano não foram testados.
- Emulador, não aparelho; relógio ou pulseira reais não foram usados.
- As regras de publicação foram conferidas só em fontes de terceiros ou de forma parcial.

## 7. Pendências

- Repetir o teste do iOS com todos os tipos liberados (pede apagar o simulador e tocar em "Ativar Tudo") e depois em aparelho real.
- Verificar com o aplicativo real da InBody se o resultado chega ao Health Connect e ao Apple Health.
- Declaração de apps de saúde no Play Console e revisão da App Store: preparar na Etapa 7.
- Parecer jurídico sobre bioimpedância e dispositivo médico (J9) e sobre dado de plataforma de saúde (J1).
