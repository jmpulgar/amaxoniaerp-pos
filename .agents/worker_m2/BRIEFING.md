# BRIEFING — 2026-08-31T17:41:11Z

## Mission
Implement all Android POS changes for Requirements R1, R2, R3, R5, R6, R7 in `amaxoniaerp-pos` and ensure all quality gates (unit tests, Detekt, ktlint, Android Lint, Kover ratchet >= 4383 lines / 15%, and 3 flavor builds) pass.

## 🔒 My Identity
- Archetype: worker
- Roles: implementer, qa, specialist
- Working directory: D:\PROGRAMMING\Kotlin\Amaxonia\.agents\worker_m2
- Original parent: bdb03428-3c80-4d10-ac23-887615aa5628
- Milestone: M2 (POS UI & Client Integration) & M3 (Quality Gates)

## 🔒 Key Constraints
- EXCLUSIVE write ownership of `D:\PROGRAMMING\Kotlin\Amaxonia\amaxoniaerp-pos\`.
- DO NOT touch `amaxoniaerp-backend` files.
- DO NOT CHEAT: Genuine implementation, no hardcoded test results, no dummy facades.
- Must pass `./gradlew test`, `./gradlew detekt`, `./gradlew ktlintCheck`, `./gradlew lint`, `./gradlew :app:koverVerifyAmaxoniaDebug`, and assemble for all 3 flavors.

## Current Parent
- Conversation ID: bdb03428-3c80-4d10-ac23-887615aa5628
- Updated: not yet

## Task Summary
- **What to build**:
  - R1: Facturas Pendientes lifecycle verification (DraftInvoicesScreen/ViewModel) and non-blocking Cierre de Caja UI/ViewModel.
  - R2: Date filters in "Seleccionar Factura" (CreditNotes) defaulting to today, DatePickers, validation Hasta >= Desde and <= 1 month. In "Historial de Facturas", remove `sucursal`, `estatus`, and `campo` fields cleanly, add DatePickers & validation.
  - R3: Inject `CajaRepository` to `HistoryViewModel`/`HistoryGraph`, pass `caja_id` to strictly filter by active opening cash register.
  - R5: Ticket reprint button in `FacturaDetalleSheet` via `PrintInvoiceUseCase` (read-only); PDF download button via `SalesApi`, cached locally and opened via `FileProvider` / `Intent.ACTION_VIEW`.
  - R6: Add electronic invoice fields to `Transaction`, update `ApiTransactionRepository` and `TransactionCard` (badges: "FE Pendiente", "FE Fallida", "FE Exitosa"), and add "Reenviar Factura Electrónica" action in `FacturaDetalleSheet` calling `POST /api/facturacion-electronica/{invoiceId}/enviar`.
  - R7: Full Quality Gates verification.
- **Success criteria**: All requirements implemented genuine with passing tests, zero linter/detekt/ktlint violations, Kover threshold met, 3 APKs assemble.
- **Interface contracts**: `PROJECT.md` § Interface Contracts.
- **Code layout**: `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/`.

## Key Decisions Made
- Use Compose Material 3 DatePicker / DateRangePicker or DatePickerDialog with custom validation for date range.
- Propagate `cajaId` from `cajaRepository.activeCaja` in `HistoryViewModel` when fetching history.
- Map PAC electronic invoice status cleanly in `Transaction` model.

## Artifact Index
- `DISPATCH.md` — Assignment instructions
- `BRIEFING.md` — Persistent state and situational awareness
- `progress.md` — Liveness and step tracking
- `handoff.md` — Final structured report

## Change Tracker
- **Files modified**: [TBD]
- **Build status**: [TBD]
- **Pending issues**: None

## Quality Status
- **Build/test result**: [TBD]
- **Lint status**: [TBD]
- **Tests added/modified**: [TBD]

## Loaded Skills
- **Source**: `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\skills\android-kotlin\SKILL.md`
  - **Local copy**: `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\skills\android-kotlin\SKILL.md`
  - **Core methodology**: Coroutines, Flow, Compose, Hilt, MockK, Turbine, clean architecture.
- **Source**: `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\skills\android-viewmodel\SKILL.md`
  - **Local copy**: `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\skills\android-viewmodel\SKILL.md`
  - **Core methodology**: StateFlow for UI state, SharedFlow for events, immutable state, testability.
