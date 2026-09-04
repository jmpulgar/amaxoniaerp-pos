# Progress — Worker M2 (POS Worker)

Last visited: 2026-08-31T17:41:11Z

## Status
- [x] Read DISPATCH.md, ORIGINAL_REQUEST.md, PROJECT.md, and explorer_pos handoff report.
- [x] Create BRIEFING.md and progress.md.
- [ ] Baseline test and build check on `amaxoniaerp-pos`.
- [ ] Implement R1: Verify/adjust DraftInvoices and CierreCaja if needed.
- [ ] Implement R2: CreditNotes date filter defaulting to today, DatePicker, validation (Hasta >= Desde, <= 31 days). Remove `sucursal`, `estatus`, `campo` in HistoryScreen/ViewModel/DTO/SalesApi.
- [ ] Implement R3: Active Caja filter in InvoiceHistoryFilter / HistoryViewModel / SalesApiImpl.
- [ ] Implement R5: Ticket reprint and PDF download/view in FacturaDetalleSheet & SalesApi.
- [ ] Implement R6: Electronic invoice status in Transaction model, badges in TransactionCard, resend electronic invoice action in FacturaDetalleSheet & HistoryViewModel & SalesApi.
- [ ] Write/update unit tests for all changed files.
- [ ] Execute all quality gates (test, detekt, ktlintCheck, lint, koverVerifyAmaxoniaDebug, assemble*Debug).
- [ ] Produce handoff.md.
