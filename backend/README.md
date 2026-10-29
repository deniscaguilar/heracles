# backend

Backend em Kotlin e Spring Boot: monólito modular com Clean Architecture por módulo (ADR-0001). Um único módulo de build Maven, com perfis `api`, `worker` e `all`.

- **JDK 25** (LTS): com SDKMAN, `sdk env` nesta pasta usa o `.sdkmanrc`. O `maven-enforcer-plugin` recusa outro JDK.
- Build e testes: `./mvnw verify` (a partir desta pasta). Testes `*Test` rodam no Surefire e `*IT` no Failsafe.
- Saúde: `GET /actuator/health/liveness` e `/readiness`; só o endpoint `health` é exposto, sem detalhes.
- Módulos e dependências permitidas: `docs/architecture.md`.
- Regras de código: `CLAUDE.md` (seções 3, 6 e 9).

Estado: esqueleto (Etapa 1, fatia S1): aplicação Spring Boot mínima com os endpoints de saúde. Nenhum módulo de produto ainda.
