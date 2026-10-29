# Protótipo P1: sincronização offline (descartável)

- `server/`: servidor de teste (Node, só módulos nativos). `dev.ts` sobe em 0.0.0.0:8787 para o emulador.
- `client/`: cliente de sincronização próprio (opção A): fila de mutações local, push idempotente, pull por cursor. Armazenamento por adaptador.
- `test/`: cenários C1 a C6, rodam sem aparelho (`node --test test/sync.test.ts`, Node 22).
- `app/`: app Expo mínimo que roda o cliente no emulador com `expo-sqlite` e mede 10 mil registros. Rode `./copy-client.sh` depois de mudar `client/sync-client.ts`.

Não é código de produção. Resultados e decisão: `docs/stage-0/p1-sincronizacao.md`.
