# mobile

App iOS e Android em React Native (Expo SDK 58), com offline e sincronização (ADR-0005). Os protótipos descartáveis da Etapa 0 ficam em `prototypes/`, não aqui.

Estado: shell com login OIDC (Authorization Code + PKCE pelo `expo-auth-session`), tokens no `expo-secure-store` (Keychain no iOS, Keystore no Android) e o perfil lido de `GET /api/v1/me` pelo cliente gerado (`@heracles/api-client`), nunca do token. A administração fica no app web. A criptografia local do banco entra na E1-S7.5.

## Comandos (Node 22)

Antes, `npm ci && npm run build` em `packages/api-client` (o mobile o usa como dependência local). Depois, em `mobile/`:

- `npm ci`, `npm run typecheck`, `npm test` (Jest).
- `npm run ios` e `npm run android`: geram os projetos nativos em `ios/` e `android/` (não versionados, vêm do `app.json`) e instalam um build de desenvolvimento. Exige Xcode com CocoaPods (iOS) ou o SDK do Android com JDK 21 (`ANDROID_HOME` e `JAVA_HOME`).
- `npx expo start --dev-client`: o Metro; abra o app com `heracles://expo-development-client/?url=http%3A%2F%2Flocalhost%3A8081`.

## Entrar localmente

Suba a pilha (`docker compose -f ../docker/compose.yml up -d --wait`). No iOS, o simulador enxerga `localhost` do Mac. No **emulador Android**, encaminhe as portas para que `localhost` valha também lá (o emissor do token é `http://localhost:8082/default`):

```bash
adb reverse tcp:8080 tcp:8080 && adb reverse tcp:8082 tcp:8082 && adb reverse tcp:8081 tcp:8081
```

Toque em **Entrar**; o servidor de mentira abre no navegador do sistema: digite qualquer nome em *Username* e deixe as claims vazias. O primeiro acesso cria o usuário com o perfil `USER` (como virar `ADMIN`: `docker/README.md`).

Se o aparelho parecer não refletir o código que você acabou de editar, o Metro perdeu as edições em disco: pare-o e rode `npx expo start --dev-client --clear`.

Ajustes de build: `EXPO_PUBLIC_OIDC_AUTHORITY`, `EXPO_PUBLIC_OIDC_CLIENT_ID` e `EXPO_PUBLIC_API_BASE_URL` (os padrões apontam para a pilha local).

O identificador do app (`com.heracles.mobile`, em `app.json`) é **provisório**, como o pacote raiz do backend: defina-o antes de qualquer configuração externa (lojas, Cognito).

## Banco local cifrado

Depois do login, o app abre `heracles-<userId>.db` (expo-sqlite com SQLCipher). A chave é aleatória (256 bits), por usuário, e fica no Keychain ou Keystore (`expo-secure-store`); o `PRAGMA key` é o primeiro comando e o app **se recusa** a abrir o banco se `PRAGMA cipher_version` vier vazio. Sair (ou um 401) apaga o arquivo, a chave e o ponteiro do usuário atual; se outro usuário entrar, os dados do anterior são apagados antes. Como conferir: o arquivo não tem o cabeçalho `SQLite format 3` e o `sqlite3` responde "file is not a database". Mudar o plugin `expo-sqlite` exige novo `expo prebuild` e rebuild nativo.
