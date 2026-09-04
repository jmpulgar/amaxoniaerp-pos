# BRIEFING — 2026-08-31T17:36:30Z

## Mission
Investigate the `amaxoniaerp-backend` codebase (Ktor) for requirements R1 through R7 and produce a detailed handoff report.

## 🔒 My Identity
- Archetype: explorer
- Roles: Backend Codebase Explorer
- Working directory: D:\PROGRAMMING\Kotlin\Amaxonia\.agents\explorer_backend
- Original parent: bdb03428-3c80-4d10-ac23-887615aa5628
- Milestone: Investigation R1-R7 for Ktor Backend

## 🔒 Key Constraints
- Read-only investigation — do NOT implement code changes in the source tree
- Analyze Ktor backend for R1, R2, R3, R4, R5, R6, R7
- Provide exact file paths, line numbers, table definitions, Exposed queries, routes, and root causes

## Current Parent
- Conversation ID: bdb03428-3c80-4d10-ac23-887615aa5628
- Updated: 2026-08-31T17:36:30Z

## Investigation State
- **Explored paths**:
  - `src/main/kotlin/com/amaxoniaerp/features/facturas/`
  - `src/main/kotlin/com/amaxoniaerp/features/caja/`
  - `src/main/kotlin/com/amaxoniaerp/features/creditnotes/`
  - `src/main/kotlin/com/amaxoniaerp/features/electronicinvoice/`
  - `src/main/kotlin/com/amaxoniaerp/features/sales/`
  - `src/test/kotlin/com/amaxoniaerp/`
  - `build.gradle.kts`, `gradle/libs.versions.toml`
- **Key findings**:
  - R1: Cash close blocks due to `CajaSessionWorkflow.kt:49` calling `CajaInventarioReader.kt:38-49` (`countFacturasTemporales`) which counts `cod_estatus=1 and formaPago != 'credito'`. In `FacturasSummaryMapper.kt:28-29`, `cod_estatus=1` with `contado` is mapped as "En Espera".
  - R2 & R3: Credit note invoice selection (`CreditNoteRoutes.kt:39` / `CreditNoteQueries.kt:98`) and invoice history (`FacturasRoutes.kt:34` / `FacturasRepository.kt:84`) require date range validation (Hasta >= Desde, max 1 month) and active cash register filtering (`tabla.idCaja eq filter.cajaId`). `estatusList`, `sucursalId`, `campo` must be cleaned from `FacturasFilter` without dead code.
  - R4: `caja.cod_almacen` fails in Panama DB because MySQL table `caja` in Panama has no `cod_almacen` column (only Venezuela has `caja.cod_almacen`; Panama uses `sucursal_almacen` or `parametros_generales.cod_almacen`). Exposed table objects like `CajaTable` / `SalesCajaTable` define `codAlmacen` unconditionally, so any query doing `.selectAll()` or selecting `CajaTable.columns` on Panama DB fails with `Unknown column 'caja.cod_almacen' in 'field list'`.
  - R5 & R6: `PanamaElectronicInvoiceClient.downloadPdf` (`POST /api/DescargaPDF`) and `PanamaCreditNotePdfStorage` handle PDFs. Manual resend endpoint `POST /api/facturacion-electronica/{invoiceId}/enviar` is idempotent; does not touch commercial ledger.
  - R7: Java 21 toolchain, Gradle tasks (`test`, `detekt`, `ktlintCheck`, `jacocoTestCoverageVerification`, `build`). In-memory H2 with MySQL mode.
- **Unexplored areas**: None for backend scope.

## Key Decisions Made
- All findings structured and ready for `handoff.md`.

## Artifact Index
- D:\PROGRAMMING\Kotlin\Amaxonia\.agents\explorer_backend\handoff.md — Final handoff report
