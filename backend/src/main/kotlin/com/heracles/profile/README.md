# Módulo `profile`

Anamnese (dado de saúde, isolado por usuário no banco), revisões, catálogos e a triagem de quem fica fora do plano
automático (`docs/architecture.md`; spec e plano em `specs/platform/stage-2-consentimento-anamnese/`). Schema Postgres
próprio (`profile`). Outros módulos só usam `profile.api`; este módulo só usa `platform.api` e `consent.api`.

```
com.heracles.profile/
  api/          EligibilityQuery (único acesso de outros módulos)
  domain/       anamnese, validação, triagem (sem Spring, sem JPA)
  application/  casos de uso e ports
  adapter/in/   web
  adapter/out/  JDBC
  config/
```
