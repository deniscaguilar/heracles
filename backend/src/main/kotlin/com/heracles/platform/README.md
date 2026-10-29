# Módulo `platform`

Identidade, perfis, auditoria, isolamento por usuário e fluxos assíncronos (`docs/architecture.md`). Estrutura de cada módulo (`CLAUDE.md`, seção 3):

```
com.heracles.platform/
  api/          fachada pública e eventos (único acesso de outros módulos)
  domain/       entidades, value objects e regras (sem Spring, JPA, Jackson nem AWS)
  application/  casos de uso e ports (in e out)
  adapter/in/   web, listeners de evento, consumidores de fila
  adapter/out/  persistência e integrações externas
  config/
```

Os pacotes são criados junto com a primeira classe de cada camada (a partir da fatia S2). As regras estão em `src/test/kotlin/com/heracles/architecture/ArchitectureRules.kt` e valem para todo módulo novo.
