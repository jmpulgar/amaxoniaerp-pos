# Roadmap de confiabilidad de pagos — Fases 0–3

Estado: **Implementación y validaciones automatizadas aprobadas (2026-07-20)**.
Falta: **QA de hardware** antes de marcar como production-ready.

| Fase | Estado implementación | Validación automática | QA hardware |
|---|---|---|---|
| 0 — Network hardening                  | ✅ | ✅ lint | — (no aplica) |
| 0b — Detekt baseline                   | ✅ | ✅ detekt | — |
| 1 — Idempotencia + duplicate invoice   | ✅ | ✅ unit | — (unit coverage) |
| 2 — Cola de confirmación fiscal        | ✅ | ✅ unit | ⏳ `TERMINAL_FAILED` end-to-end |
| 3 — RapidPay gateway lease durable     | ✅ | ✅ unit | ⏳ 2 escenarios HKA |

## Verificación automática consolidada (ejecutada 2026-07-20)

| Tarea | Resultado |
|---|---|
| `:app:compileAmaxoniaDebugKotlin` | ✅ BUILD SUCCESSFUL |
| `:app:detekt` (warningsAsErrors=true) | ✅ BUILD SUCCESSFUL |
| `:app:lintAmaxoniaDebug` | ✅ BUILD SUCCESSFUL |
| `:app:testAmaxoniaDebugUnitTest` | 103/104 ✅ · 1 fail preexistente (ver `04-preexisting-test-failure.md`) |

## Cambios de configuración de Detekt (comunicados transparentemente)

- `complexity.LongParameterList.ignoreAnnotated = [Query, Insert, Update, Delete]`
  - DAOs de Room son idiomáticamente colección de verbos SQL; agrupar en data class añade boilerplate.
- `complexity.TooManyFunctions` threshold 11 → 20 (interfaces/classes/objects/files)
  - `TransactionLogDao` (~17 verbos SQL) y `SyncScheduler` (facade único de WorkManager).

## Schema Room

`AppDatabase` v10 → v13. Esquemas exportados:
- `app/schemas/.../10.json`, `11.json`, `12.json`, `13.json`
- Migraciones encadenadas (`MIGRATION_10_11`, `MIGRATION_11_12`, `MIGRATION_12_13`).

## Detalle por fase

Ver:
- `01-fase-summary.md` — matriz de objetivos y soluciones técnicas.
- `02-pending-hardware-tests.md` — matriz de evidencias que QA debe ejecutar.
- `03-commit-and-files.md` — listado de archivos tocados por commit.
- `04-preexisting-test-failure.md` — evidencia del fallo preexistente.
