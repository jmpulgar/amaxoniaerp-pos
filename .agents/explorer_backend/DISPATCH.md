## 2026-08-31T17:31:47Z

You are the Backend Codebase Explorer for the Amaxonia project.
Your working directory is `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\explorer_backend`.
Create your BRIEFING.md and progress.md in your working directory.

Read the user requirements at `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\ORIGINAL_REQUEST.md`.

Investigate the `amaxoniaerp-backend` codebase (Ktor backend) thoroughly for requirements R1, R2, R3, R4, R5, R6, R7:
1. R1 (En Espera & Cierre de Caja):
   - Database schema and status definitions for invoices.
   - Cash close ("Cierre de Caja") endpoints, services, and queries. Why does it block on "En Espera" and how should it behave?
   - Endpoints for pending invoices, delete pending invoice endpoint/service.
2. R2 & R3 (Filtros de Fecha y Caja Activa):
   - Endpoints and SQL/Exposed queries for Invoice Selection (Credit Notes) and Invoice History.
   - Date range parameters (desde, hasta), 1-month limit validation, default to current day for Credit Note selection.
   - Active cash register filter (`Apertura de caja -> Caja -> Factura`): How the backend validates and filters invoices by active caja.
   - Removal of status/campo/sucursal parameters and columns across DTOs, routes, and services without dead code.
3. R4 (Root Cause in Panama Electronic Credit Note `caja.cod_almacen`):
   - Trace the exact query, Exposed tables, entity definitions, SQL joins, and repository methods involved when generating Panama electronic credit notes.
   - Why does `caja.cod_almacen` fail? What is the actual column name / relation in the database (e.g. `caja` vs `almacen` vs `sucursal` vs `punto_emision`)?
   - How to fix the mapping cleanly without breaking other countries (Venezuela, Colombia, etc.).
4. R5 & R6 (PDF & Electronic Invoice Resending):
   - PDF generation/retrieval endpoints and services.
   - Electronic invoice transmission and resend endpoints/services.
   - Idempotency guarantees: How to ensure no duplicate commercial invoices/sales are created, and how CUFE/QR/electronic status are updated on the existing record.
5. R7 (Quality Gates & Backend Build):
   - Gradle configuration (Java 21), tasks (`test`, `detekt`, `ktlintCheck`, `jacocoTestCoverageVerification`, `build`).
   - Database migrations / testing setup (H2 / Testcontainers / MockK).
   - Existing test suite.

Document all findings with precise file paths, class names, SQL queries, table definitions, and architectural insights.
Write your complete structured handoff report to `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\explorer_backend\handoff.md`.
Send a completion message back when done.
