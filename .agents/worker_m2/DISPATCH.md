## 2026-08-31T17:41:11Z
You are the POS Worker (Worker M2) for the Amaxonia project.
Your working directory is `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\worker_m2`.
Create your BRIEFING.md and progress.md in your working directory.

Read the user requirements at `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\ORIGINAL_REQUEST.md`.
Read the project plan at `D:\PROGRAMMING\Kotlin\Amaxonia\PROJECT.md`.
Read the POS explorer report at `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\explorer_pos\handoff.md`.

You have EXCLUSIVE write ownership of `D:\PROGRAMMING\Kotlin\Amaxonia\amaxoniaerp-pos\`.
Do not touch `amaxoniaerp-backend` files.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Implement all Android POS changes for Requirements R1, R2, R3, R5, R6, R7:
1. R1: Verify "Facturas Pendientes" (`DraftInvoicesScreen.kt` / `DraftInvoicesViewModel.kt`) displays incomplete/draft invoices clearly and allows deletion and restoration. Ensure Cierre de Caja (`CierreCajaViewModel.kt`) handles summary and closing smoothly.
2. R2: In "Seleccionar Factura" (`CreditNotesViewModel.kt`, `CreditNotesScreen.kt`, `CreditNotesState.kt`), default date filter to current day (`LocalDate.now()`), replace text inputs with native/Compose DatePickers, and validate `Hasta >= Desde` and max 1 month range.
   In "Historial de Facturas" (`HistoryScreen.kt`, `HistoryViewModel.kt`, `InvoiceHistoryFilter`, `SalesApiImpl.kt`, `SalesApiFilterTest.kt`, `HistoryScreenPreviews.kt`), completely remove `sucursal`, `estatus`, and `campo` fields without leaving dead code. Add DatePickers and validate date range.
3. R3: In `HistoryViewModel.kt`, `HistoryGraph.kt`, and `InvoiceHistoryFilter`, inject `CajaRepository`, retrieve `activeCaja?.idCaja`, and pass `caja_id` in `SalesApiImpl.kt` so invoice history queries strictly filter by the active opening cash register.
4. R5: In `FacturaDetalleSheet.kt`, add "Reimprimir Ticket" button triggering `PrintInvoiceUseCase` (read-only print payload) and "Descargar / Ver PDF" button fetching PDF bytes via `SalesApi`, saving to cache, and opening via `FileProvider` `Intent.ACTION_VIEW`.
5. R6: In `domain/model/Transaction.kt`, `ApiTransactionRepository.kt`, and `TransactionCard.kt`, add electronic status fields and visual indicators ("FE Pendiente", "FE Fallida", "FE Exitosa"). In `FacturaDetalleSheet.kt`, add "Reenviar Factura Electrónica" action calling `POST /api/facturacion-electronica/{invoiceId}/enviar` via `SalesApi` and `HistoryViewModel`.
6. R7 & Quality Gates: Run all POS quality gate commands:
   - `./gradlew test`
   - `./gradlew detekt`
   - `./gradlew ktlintCheck`
   - `./gradlew lint`
   - `./gradlew :app:koverVerifyAmaxoniaDebug`
   - `./gradlew assembleAmaxoniaDebug`
   - `./gradlew assembleBanescoVenezuelaDebug`
   - `./gradlew assembleListoerpDebug`
   Ensure all tests pass, Kover coverage meets or exceeds ratchet (>= 4383 lines / >= 15%), and all 3 flavor APKs compile.

Write your complete structured handoff report to `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\worker_m2\handoff.md`.
Send a completion message back when done.
