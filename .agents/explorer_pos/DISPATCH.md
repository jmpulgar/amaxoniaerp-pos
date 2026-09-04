## 2026-08-31T17:31:47Z
You are the POS Codebase Explorer for the Amaxonia project.
Your working directory is `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\explorer_pos`.
Create your BRIEFING.md and progress.md in your working directory.

Read the user requirements at `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\ORIGINAL_REQUEST.md`.

Investigate the `amaxoniaerp-pos` codebase (Android app) thoroughly for requirements R1, R2, R3, R5, R6, R7:
1. R1 (En Espera & Cierre de Caja):
   - Where are invoices created and stored locally/remotely?
   - How does "En Espera" state get assigned?
   - How does "Facturas Pendientes" screen work? How can pending/en espera invoices be listed and safely deleted?
   - How does "Cierre de Caja" currently validate open/pending/en espera invoices? Where is the blocking logic located?
2. R2 (Filtros de Fecha en Seleccionar Factura e Historial):
   - In "Seleccionar Factura" (Credit Note flow): Current date filtering logic, default loading, datepicker component, validation (Hasta >= Desde, max 1 month).
   - In "Historial de Facturas": Where are Estatus, Campo, Sucursal fields currently defined in UI, ViewModel, Repository, DTOs, and queries? Identify all files needing removal of these dead fields.
3. R3 (Filtrado por Caja de la Apertura Activa):
   - How is the active cash register session / opening (`Apertura de caja -> Caja -> Factura`) tracked in POS?
   - Where does Historial de Facturas fetch data and how can it strictly filter by the active session's caja?
4. R5 (Reimpresión de Tickets y Descarga de PDFs):
   - Ticket printing architecture: print template, data models, reprint triggers from invoice history.
   - PDF download/viewing architecture: API client, storage/cache, viewer intent, error handling.
5. R6 (Reenvío Manual de Facturas Electrónicas y Separación Comercial/Electrónica):
   - Invoice history UI: Status chips/indicators for incomplete/failed electronic transmission (missing CUFE, QR, error).
   - Resend action: UI trigger, repository/API call, idempotency check, updating local invoice state (CUFE, QR, status).
6. R7 (Quality Gates & Build Configuration):
   - Build system: Gradle version catalogs, Android SDK, flavors (`amaxonia`, `banescoVenezuela`, `listoerp`).
   - Static analysis tools: Detekt, ktlint, Android Lint, Kover configuration.
   - Existing test suite in POS.

Document all findings with precise file paths, class names, method signatures, line numbers, and architectural insights.
Write your complete structured handoff report to `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\explorer_pos\handoff.md`.
Send a completion message back when done.
