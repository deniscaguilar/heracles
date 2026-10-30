# ADR-0009: Scan de dependências com OSV-Scanner sobre o SBOM

- **Status:** Rascunho
- **Data:** 2026-10-30
- **Origem:** Etapa 1, tarefa E1-S1.8; `specs/platform/stage-1-foundation/plan.md` (pipeline); `CLAUDE.md`, seção 7

## Contexto

O CI precisa falhar com vulnerabilidade Alta ou Crítica em dependência, com supressão só com justificativa e data de expiração. O plano deixou a escolha entre **OWASP dependency-check** e **OSV-Scanner** para ser feita com a medição de tempo e de falsos positivos.

## Medição (30/10/2026, máquina local, 60 componentes do SBOM do backend)

| | OSV-Scanner 2.7.0 | OWASP dependency-check |
|---|---|---|
| Tempo sobre o SBOM | ~1,3 s | **não medido**: exige a base do NVD (chave de API e download inicial longo), o que pesa no CI e no cache |
| Chave ou conta | nenhuma | chave do NVD recomendada |
| Casamento de pacote | por coordenada Maven exata (OSV.dev, GHSA) | por CPE, com mais falsos positivos conhecidos |
| Teste negativo | `log4j-core 2.14.1` reprovado (CVSS 9.0 e 10.0) | não rodado |
| Supressão com validade | `[[IgnoredVulns]]` com `ignoreUntil` e `reason` | arquivo de supressão com `until` |

## Decisão

- **OSV-Scanner**, lendo o **SBOM CycloneDX** gerado pelo build (as 60 dependências já resolvidas, inclusive as transitivas; ler o `pom.xml` direto enxerga só 8 pacotes).
- O OSV-Scanner não filtra por severidade; o script `.github/scripts/osv-gate.sh` lê o JSON e **reprova com CVSS ≥ 7,0 ou severidade desconhecida** (falha fechada). Média e baixa são só informadas.
- Supressões em `osv-scanner.toml`, cada uma com `reason` e `ignoreUntil` obrigatórios. Prazos de correção: crítica ≤ 48 h, alta ≤ 7 dias (`CLAUDE.md`, seção 7).
- A imagem do scanner é fixada por digest. **Trivy** continua cobrindo a imagem do backend (`.trivyignore.yaml`, com `expired_at`), separado deste scan.

## Consequências

- Scan em segundos e sem segredo no CI.
- Depende do OSV.dev estar no ar; indisponibilidade reprova o job (falha fechada) e é refeita.
- Se a cobertura do OSV mostrar lacuna que o NVD cobriria, reabrir esta ADR e somar o OWASP.
