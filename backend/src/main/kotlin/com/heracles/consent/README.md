# Módulo `consent`

Políticas e termos versionados, aceite e revogação como eventos append-only, e a pergunta "o usuário consentiu em X?"
(`docs/architecture.md`; spec e plano em `specs/platform/stage-2-consentimento-anamnese/`). Schema Postgres próprio
(`consent`), com pasta de migrações e histórico do Flyway próprios. Outros módulos só usam `consent.api`; este módulo só
usa `platform.api`.

```
com.heracles.consent/
  api/          ConsentChecker, RequiresConsent, ConsentType, eventos (único acesso de outros módulos)
  domain/       tipos de consentimento, estado derivado, política em blocos (sem Spring, sem JPA)
  application/  casos de uso e ports
  adapter/in/   web (cliente e backoffice), interceptador da barreira, consumidor de fila
  adapter/out/  JDBC
  config/
```
