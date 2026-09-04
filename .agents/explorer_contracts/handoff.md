# Contracts and Test Infra Specification Mining Report

**Working Directory**: `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\explorer_contracts`  
**Date**: 2026-08-31T17:40:00Z  
**Author**: Specification Miner (Contracts & Test Infra Specialist)  
**Target Repositories**: `amaxoniaerp-pos` & `amaxoniaerp-backend`

---

## Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|----------|---------|-------------|--------|---------|----------------|----------------|
| F01 | Lifecycle (R1) | Non-blocking Draft & "En Espera" | Invoices saved as incomplete or interrupted drafts appear under "Facturas Pendientes" (Room `draft_invoices`) with clear incomplete status and deletion capability without blocking cash close | `DraftInvoice` / Cart state | Local draft list, delete action, restore to cart | Corrupted draft JSON throws and is logged via `SafeLog.e` | `DraftInvoicesViewModel.kt`, `CajaSessionWorkflow.kt:49-51` |
| F02 | Cash Close (R1) | Cierre de Caja Unblocked by Drafts | Cierre de caja proceeds without error when drafts or temporary invoices exist | `CierreCajaRequest` (id, cash breakdown) | `CierreCajaResponse` (message, success) | If blocked by unclosed active sequences, returns 400 Bad Request | `CierreCajaViewModel.kt`, `CajaSessionWorkflow.kt:45-55`, `CajaInventarioReader.kt:38-49` |
| F03 | Date Filtering (R2) | Date Filter in "Seleccionar Factura" | Loads current day invoices by default in Credit Note source invoice picker; allows Desde/Hasta datepicker selection up to 1 month max | `fechaInicio`, `fechaFin`, `search` | `CreditNoteSourceInvoiceListResponseDto` | Returns 400 Bad Request if `fechaFin < fechaInicio` or range > 1 month | `CreditNotesViewModel.kt:75-125`, `CreditNoteQueries.kt:98-150`, `CreditNoteRoutes.kt:85-105` |
| F04 | Date & Field Clean (R2) | Filter Cleanup in "Historial de Facturas" | Replaces legacy text inputs and removes Estatus, Campo, and Sucursal filters; enforces native datepicker, Desde/Hasta <= 1 month at database query level | `fechaInicio`, `fechaFin`, `cajaId`, `search` | `FacturasListResponse` & `FacturasResumen` | Returns 400 Bad Request if range > 1 month or invalid date format | `FacturasRoutes.kt:53-87`, `FacturasRepository.kt:34-41,169-203`, `HistoryScreen.kt:437-506` |
| F05 | Cash Session Filter (R3) | Active Cash Session Invoices in History | Historial de facturas strictly filters at the SQL query level by `id_caja` of the currently active caja opening session (`Apertura -> Caja -> Factura`) | `cajaId: String` | Filtered `List<FacturaSummary>` matching active caja | If no active caja session, defaults to active store caja or empty list | `FacturasRepository.kt:91-110`, `BaseFacturasTable.idCaja`, `ApiTransactionRepository.kt:90-120` |
| F06 | DB Mapping Fix (R4) | Root-Cause Fix for `caja.cod_almacen` in Panama NC | Panama MySQL DB does not contain `caja.cod_almacen` (only VE does). Table mappings and warehouse resolution must isolate column queries by country | `countryCode: "PA" \| "VE"` | Clean SQL query without `caja.cod_almacen` in field list for PA | Throws MySQL `Unknown column 'caja.cod_almacen' in 'field list'` if queried on PA database | `WarehouseContext.kt:23-42`, `CajaCatalogReader.kt:230-234`, `CajaTable.kt:13`, `CreditNoteCajaTable` |
| F07 | Ticket & PDF (R5) | History Ticket Reprint & PDF Download | Enables reprint action using existing print payload and Sunmi/HKA formatters without altering invoice; enables PDF retrieval from PAC | `facturaId: String` | Print ticket dispatch / Raw PDF bytes (`application/pdf`) | 404 if invoice/CUFE missing, 502 if PAC communication fails | `FacturasRoutes.kt:129-157`, `DefaultInvoicePrintGateway.kt:46-91`, `PanamaElectronicInvoiceClient.downloadPdf` |
| F08 | Electronic Resend (R6) | Idempotent Manual Resend of Electronic Invoices | Visual indicator for invoices with incomplete/failed electronic emission (no CUFE/QR); manual resend triggers PAC submission and updates CUFE/QR on existing local invoice without duplicate sales | `invoiceId: String` | Updated `cufe`, `qr`, `fechaRecepcionDGI`, `nroProtocoloAutorizacion` | Returns `AlreadyIssued` if CUFE already present; returns `BadGateway` on PAC rejection | `ElectronicInvoiceRoutes.kt:26-65`, `PanamaInvoiceProcessor.kt:47-117`, `FacturasTablePA.cufe` |
| F09 | Multi-Flavor (R7) | Multi-Flavor Build Compatibility | POS supports 3 brand flavors (`amaxonia`, `banescoVenezuela`, `listoerp`) across debug and release build variants with custom colors, strings, and icons | `./gradlew assemble*Debug` | 3 distinct debug APKs with appropriate `DEFAULT_COUNTRY_CODE` and application IDs | Compilation failure if flavor source sets or buildConfig fields mismatch | `amaxoniaerp-pos/app/build.gradle.kts:61-83`, source sets `amaxonia/`, `banescoVenezuela/`, `listoerp/` |
| F10 | Quality Gates (R7) | Zero-Debt Quality Verification & Ratchets | Enforces code formatting, static analysis, Android lint, and coverage ratchets in both repos | `./gradlew detekt`, `ktlintCheck`, `lint`, `koverVerify*`, `jacoco*` | Quality gates PASS with zero warnings/violations | Fails CI build on any quality regression or coverage drop | `backend/build.gradle.kts:24-63`, `pos/app/build.gradle.kts:153-187`, `.github/workflows/` |

---

## Edge Cases

| # | Feature | Input | Observed / Specified Behavior |
|---|---------|-------|-------------------------------|
| E01 | F01/F02 (Lifecycle & Close) | 1 draft in `draft_invoices` when executing cash close | Cash close succeeds completely; draft invoice remains in `draft_invoices` for later deletion or cart restoration. |
| E02 | F03/F04 (Date Filters) | `fechaInicio = 2026-08-01`, `fechaFin = 2026-07-31` (Inverted) | Backend rejects with HTTP 400 Bad Request ("fecha_fin cannot be before fecha_inicio"); UI disables submit button or warns user. |
| E03 | F03/F04 (Date Filters) | `fechaInicio = 2026-07-01`, `fechaFin = 2026-08-02` (> 31 days) | Backend rejects with HTTP 400 Bad Request ("Consultation period cannot exceed 1 month"); UI restricts selection. |
| E04 | F03/F04 (Date Filters) | `fechaInicio = null`, `fechaFin = null` in "Seleccionar Factura" | Defaults strictly to today's date (`LocalDate.now()` to `LocalDate.now()`). |
| E05 | F05 (Active Caja Filter) | Cashier accesses invoice history without active opening session | Displays empty list or prompts cashier that no active opening session is selected; prevents cross-caja leakage. |
| E06 | F06 (Panama Credit Note) | Generating electronic credit note on Panama DB | `resolveWarehouseContext` and Exposed table schemas query only `SalesCajaTable.idSucursal` without `caja.cod_almacen`, preventing MySQL 1054 error. |
| E07 | F07 (Ticket Reprint) | Cashier taps "Reimprimir Ticket" for an invoice with CUFE | Fetches `FacturaPrintPayloadDto` via `GET /facturas/{id}/print-payload`, formats using `PanamaInvoiceTicketFormatter` / `VenezuelaInvoiceTicketFormatter`, prints on Sunmi without inserting any new transactions. |
| E08 | F07 (PDF Download) | Factura exists locally but has no CUFE or PAC returned error | Returns HTTP 404 / 400 with descriptive error ("El PDF no está disponible porque la factura no posee CUFE autorizado"). |
| E09 | F08 (FE Resend) | Factura already has valid CUFE (`cod_estatus=2`, `cufe != null`) | `POST /api/facturacion-electronica/{id}/enviar` returns `AlreadyIssued` (HTTP 200) with existing fiscal number, rejecting duplicate transmission. |
| E10 | F08 (FE Resend) | Factura with failed PAC communication on initial sale | `POST /api/facturacion-electronica/{id}/enviar` authenticates with PAC, builds payload, transmits to The Factory HKA, and updates `cufe`, `qr`, `fechaRecepcionDGI` on existing `factura` row. |
| E11 | F09 (Multi-Flavor) | Compiling `banescoVenezuelaDebug` | Uses `com.amaxonia.pos.banesco`, `DEFAULT_COUNTRY_CODE = "VE"`, Banesco green/blue/red color palette from `banescoVenezuela/res/values/brand_colors.xml`. |
| E12 | F10 (Quality Gates) | Line coverage drops below baseline in POS or Backend | POS `koverVerifyAmaxoniaDebug` fails if lines < 4383 or percentage < 15%; Backend `jacocoTestCoverageVerification` fails if covered ratio < 0.46526415. |

---

# 1. Observation

### 1.1 Cross-Repo Contracts & Wire Serialization
- **Contract Fixtures Directory**: `contracts/` contains 25 fixture files documenting canonical wire representations:
  - `contracts/auth/`: `login-request.json`, `login-response.json`, `company-select-request.json`, `company-select-response.json`
  - `contracts/sale/`: `process-sale-request.json`, `process-sale-response.json`, `ve-digital-process-sale-response.json`, `ve-hka20-process-sale-response.json`, `credit-sale-request.json`, `partial-credit-collection-request.json`
  - `contracts/caja/`: `caja-apertura-request.json`, `caja-cierre-request.json`
  - `contracts/mesas/`: `mesa-abrir-sesion-request.json`, `mesa-crear-pedido-request.json`, `mesa-crear-cuenta-request.json`
  - `contracts/creditnote/`: `create-credit-note-request.json`, `confirm-credit-note-fiscal-request.json`
  - `contracts/fiscal/`: `confirm-factura-fiscal-request.json`
- **Canonical Serialization Configuration**:
  ```kotlin
  Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
  ```
- **Known Cross-System Wire Asymmetries**:
  1. Login/Company-Select responses: Backend sends `countryCode` and `schemaType`; POS ignores via `ignoreUnknownKeys = true`.
  2. `caja-cierre-request`: POS declares snake_case property names directly (`monto_efectivo_ventas`); Backend binds via `@SerialName`. Wire bytes match.
  3. `useHka20`: `Boolean = false` in POS DTO default vs `Boolean? = null` in Backend. Omitted in both when false/null.
  4. `SaleInvoiceInput.codEstatus`: POS always sends `= 2`; Backend defaults `= 2` and omits on re-encode.

### 1.2 Multi-Flavor Setup in POS (`amaxoniaerp-pos`)
- **File**: `amaxoniaerp-pos/app/build.gradle.kts:61-83`
- **Flavor Dimension**: `brand`
- **Flavors Configured**:
  1. `amaxonia`:
     - `applicationId`: `com.amaxonia.pos`
     - `DEFAULT_COUNTRY_CODE`: `"PA"`
     - Source set: `app/src/amaxonia/` (Blue branding `#1B5FCC`, `brand_strings.xml`, `brand_colors.xml`, icons)
  2. `banescoVenezuela`:
     - `applicationId`: `com.amaxonia.pos.banesco`
     - `DEFAULT_COUNTRY_CODE`: `"VE"`
     - Source set: `app/src/banescoVenezuela/` (Banesco Blue `#003C71`, Red `#E31D19`, Green `#007953`, `brand_strings.xml`, `brand_colors.xml`)
  3. `listoerp`:
     - `applicationId`: `com.amaxonia.pos.listoerp`
     - `DEFAULT_COUNTRY_CODE`: `"VE"`
     - Source set: `app/src/listoerp/` (Listo Orange `#FF613F`, `brand_strings.xml`, `brand_colors.xml`)
- **Build Variants**:
  - `amaxoniaDebug`, `amaxoniaRelease`
  - `banescoVenezuelaDebug`, `banescoVenezuelaRelease`
  - `listoerpDebug`, `listoerpRelease`

### 1.3 Quality Gates & CI Configurations

#### POS (`amaxoniaerp-pos`)
- **Java / SDK Target**: Java 17, Android compileSdk 36, targetSdk 36, minSdk 29 (`app/build.gradle.kts:38-59`).
- **Detekt**:
  - Config: `config/detekt/detekt.yml` (validation=true, warningsAsErrors=true, maxLineLength=140, Composable ignore in function naming).
  - Baseline: `config/detekt/detekt-baseline.xml` (0 debts recorded, clean).
- **ktlint**: Version 1.5.0, Android mode enabled, `.editorconfig` max line length 140 (`app/build.gradle.kts:161-166`).
- **Android Lint**: `unitTests.isReturnDefaultValues = true` (`app/build.gradle.kts:135`).
- **Kover Verification**:
  - Task: `:app:koverVerifyAmaxoniaDebug` (`app/build.gradle.kts:168-187`).
  - Rules: `COVERED_PERCENTAGE >= 15%`, `COVERED_COUNT >= 4383` lines.
- **CI Workflow**: `.github/workflows/android-ci.yml` runs 6 jobs: `android-unit-test`, `android-coverage`, `android-detekt`, `android-ktlint`, `android-build-amaxonia`, `android-build-banesco-debug`, `android-build-listoerp-debug`.

#### Backend (`amaxoniaerp-backend`)
- **Java Target**: Java 21 toolchain (`build.gradle.kts:13-15`).
- **Detekt**: `buildUponDefaultConfig = true`, `allRules = false`, `ignoreFailures = false` (`build.gradle.kts:24-28`).
- **ktlint**: `verbose = true`, `outputToConsole = true`, `ignoreFailures = false`, `.editorconfig` max line length 120 (`build.gradle.kts:30-34`).
- **JaCoCo Coverage Ratchet**:
  - Task: `jacocoTestCoverageVerification` (`build.gradle.kts:52-63`).
  - Rule: `LINE` `COVEREDRATIO >= 0.46526415` (fails if line coverage drops below 46.526415%).
- **CI Workflow**: `.github/workflows/backend-ci.yml` runs 5 jobs: `backend-test`, `backend-coverage`, `backend-detekt`, `backend-ktlint`, `backend-build`.

### 1.4 Detailed Mapping of Affected DTOs and Endpoints (R1 to R6)

#### R1: Facturas "En Espera" & Cierre de Caja
- **POS Models/Entities**:
  - `DraftInvoice` (`domain/model/DraftInvoice.kt:14-29`): `id`, `clientId`, `itemsJson`, `total`, `itemCount`, `createdAt`, `subtotalGross`, `itemDiscounts`, `subtotalNet`, `tax`.
  - `DraftInvoiceEntity` (`data/local/db/DraftInvoiceEntity.kt:20-36`): Room entity with minor-unit conversions (`totalMinor`, `subtotalGrossMinor`, etc.).
  - `DraftInvoiceDao` (`data/local/db/DraftInvoiceEntity.kt:39-54`): `getAll()`, `insert()`, `deleteById()`, `deleteAll()`, `count()`.
  - `PendingInvoiceEntity` (`data/local/db/PendingInvoiceEntity.kt:19-43`): Offline retry queue with lease lock for WorkManager.
- **Backend Handlers**:
  - `CajaSessionWorkflow.kt:49-51`: Validates `countFacturasTemporalesPendientes` before closing sequence.
  - `CajaInventarioReader.kt:38-49`: `countFacturasTemporales` counts `cod_estatus == 1` and `formaPago != "credito"`.
  - Endpoint: `POST /api/pos/cajas/cierre` (closes cash sequence).

#### R2: Date Filters in "Seleccionar Factura" & "Historial de Facturas"
- **Backend Endpoints & DTOs**:
  - `GET /api/pos/notas-credito/facturas` (`CreditNoteRoutes.kt:85-105`): Parameters `limit`, `offset`, `search`, `fecha_inicio`, `fecha_fin`.
    - Query logic in `CreditNoteQueries.kt:98-150` (`listEligibleInvoices`).
    - DTO: `CreditNoteSourceInvoiceSummary` (`id`, `codigo`, `codigoFiscal`, `numeroDocumentoFiscal`, `fecha`, `clienteNombre`, `total`, `remainingAmount`, `items`, `moneda`).
  - `GET /facturas` (`FacturasRoutes.kt:34,53-87`): Parameters `limit`, `offset`, `search`, `usuario`, `fecha_inicio`, `fecha_fin`, `caja_id`.
    - Filter Model: `FacturasFilter` (`FacturasRepository.kt:34-41`): Remove legacy `sucursalId` and `estatusList`; add `cajaId`.
    - DTO: `FacturaSummary` (`FacturasTable.kt`, `FacturasSummaryMapper.kt:42-59`).
  - `GET /facturas/resumen` (`FacturasRoutes.kt:35,89-107`): Uses `FacturasFilter`.
- **POS Models & APIs**:
  - `InvoiceHistoryFilter` (`domain/repository/InvoiceHistoryRepository.kt:5-12`): Remove `sucursalId`, `estatus`; add `cajaId`, native date range validation (Hasta >= Desde, max 1 month).
  - `InvoiceDateFilter` (`ui/creditnotes/CreditNotesState.kt:33-82`): Default to current day (`LocalDate.now()`), enforce range <= 1 month.

#### R3: Filter by Active Cash Register in Invoice History
- **Backend**:
  - `BaseFacturasTable.idCaja` (`FacturasTable.kt:17`): Table column `id_caja`.
  - `FacturasRepository.kt:169-203`: Apply `where { tabla.idCaja eq filter.cajaId }`.
- **POS**:
  - `CajaRepository.activeCaja` (`domain/repository/CajaRepository.kt`): Provides active `idCaja`.
  - `SalesApiImpl.applyInvoiceHistoryFilter` (`data/remote/api/SalesApiImpl.kt:289-296`): Inject `parameter("id_caja", activeCajaId)`.

#### R4: Root Cause Fix for Panama Electronic Credit Note (`caja.cod_almacen`)
- **Backend Entities**:
  - `CajaTable.kt:13`: `val codAlmacen = integer("cod_almacen").nullable()` exists only in VE database schema.
  - `WarehouseContext.kt:23-42`: Isolated column lookup for sales warehouse:
    ```kotlin
    val isVE = countryCode.equals("VE", ignoreCase = true)
    val columns = if (isVE) listOf(SalesCajaTable.idSucursal, SalesCajaTable.codAlmacen) else listOf(SalesCajaTable.idSucursal)
    ```
  - `CreditNoteTables.kt:174-185`: `CreditNoteCajaTable` does not declare `codAlmacen`, but credit note queries and warehouse restores must ensure no raw SQL or Exposed tables attempt to select `caja.cod_almacen` when `countryCode == "PA"`.

#### R5: Ticket Reprint & PDF Download
- **Backend Endpoints & DTOs**:
  - `GET /facturas/{id}/print-payload` (`FacturasRoutes.kt:38,129-157`): Returns `FacturaPrintPayloadResponse` (`PrintPayloadDtos.kt:6-67`).
  - Proposed PDF Endpoint: `GET /api/facturacion-electronica/{invoiceId}/pdf` or `GET /facturas/{id}/pdf` (`PanamaElectronicInvoiceClient.downloadPdf:52`).
- **POS Handlers**:
  - `DefaultInvoicePrintGateway.kt:46-91`: `printSunmi` / `printFiscal` triggers print using `salesRepository.getPrintPayload(remoteInvoiceId)`.
  - `HistoryViewModel.kt`: Add `reprintTicket(transaction: Transaction)` and `downloadPdf(transaction: Transaction)`.

#### R6: Electronic Invoice Manual Resend
- **Backend Endpoint**:
  - `POST /api/facturacion-electronica/{invoiceId}/enviar` (`ElectronicInvoiceRoutes.kt:28-34`): Calls `PanamaInvoiceProcessor.processElectronicInvoice(database, invoiceId)`.
  - Returns `Success` (with `cufe`, `qr`, `fechaRecepcionDGI`, `nroProtocoloAutorizacion`), `AlreadyIssued`, `Failure`, or `Uncertain`.
- **POS UI/API**:
  - `FacturaSummaryDto` (`domain/model/sales/FacturaSummaryDto.kt:10-28`): Exposes `cufe`, `codigoFiscal`, `numeroDocumentoFiscal`, `fechaDgi`.
  - `TransactionCard.kt:120-137`: Visual badge for electronic state (e.g., "FE PENDIENTE" / "FE ERROR" when `cufe.isNullOrBlank()`).
  - `FacturaDetalleSheet.kt`: Add "Reenviar Factura Electrónica" action button calling `POST /api/facturacion-electronica/{id}/enviar`.

---

# 2. Logic Chain

1. **Wire Invariance**: Cross-system serialization in `contracts/README.md` requires strict adherence to `ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false`. Any changes to DTOs for R1-R6 must maintain backwards-compatible defaults to avoid breaking wire contracts.
2. **Draft & Cash Close Decoupling (R1)**:
   - Observation: POS stores incomplete carts locally in `draft_invoices` via `RoomDraftInvoiceRepository`. Backend `CajaSessionWorkflow.kt:49` checks `countFacturasTemporalesPendientes` which inspects `cod_estatus == 1` in `factura`.
   - Incomplete local sales should remain in Room `draft_invoices` without inserting unfinalized rows in backend `factura`.
   - Backend cash close must not block on local POS drafts; any backend checks for temporary invoices must treat draft status appropriately without freezing closing operations.
3. **Filter Pipeline Cleansing (R2 & R3)**:
   - In backend `FacturasRepository.kt:169-203`, `applyInvoiceFilters` currently parses `usuario`, `sucursal_id`, `fecha_inicio`, `fecha_fin`, and `estatus`.
   - Removing `sucursal_id` and `estatus` eliminates dead filtering paths in UI, DTOs, and queries.
   - Adding `caja_id` to `FacturasFilter` and applying `andWhere { tabla.idCaja eq cajaId }` guarantees database-level isolation of invoices to the active cash register session.
   - In "Seleccionar Factura" (Credit Notes), `CreditNotesState.kt:34` defaults to `InvoiceDateFilterType.MES_ACTUAL`. Changing the default to current day (`LocalDate.now()`) and enforcing the 1-month maximum window at both ViewModel and Route levels prevents excessive database load and guarantees compliance.
4. **Panama Database Isolation for Credit Notes (R4)**:
   - In MySQL for Panama, `caja` has no `cod_almacen`. Any Exposed query or join on `caja` that queries all columns generates `Unknown column 'caja.cod_almacen' in 'field list'`.
   - The pattern established in `WarehouseContext.kt` (only selecting `SalesCajaTable.codAlmacen` when `countryCode == "VE"`) must be universally followed across all credit note, cash, and electronic invoice queries.
5. **Idempotency in Reprint, PDF & Resend (R5 & R6)**:
   - Reprinting tickets must reuse `GET /facturas/{id}/print-payload` without issuing new transaction UUIDs or mutating invoice state.
   - Resending electronic invoices via `POST /api/facturacion-electronica/{invoiceId}/enviar` validates whether a CUFE already exists (`AlreadyIssued`). If absent, it submits to The Factory HKA PAC and updates `cufe`, `qr`, and `fechaRecepcionDGI` on the existing `factura` record without creating duplicate sales.

---

# 3. Caveats

1. **Multi-Country Database Schema Differences**:
   - Panama and Venezuela databases have divergent column names (e.g., `factura.cufe`, `factura.qr` in PA vs `factura.abr_moneda_base`, `factura.tasa`, `factura.impresora_serial` in VE).
   - Any query or DTO touching `BaseFacturasTable` or `CajaTable` must strictly check `countryCode` or use factory methods (`FacturasTableFactory`, `SalesFacturaTableFactory`, `CreditNoteHeaderTableFactory`).
2. **Quality Gate Thresholds**:
   - POS Kover coverage line minimum is 4383 lines (15%).
   - Backend JaCoCo coverage minimum is 46.526415%.
   - No suppressions, `@file:Suppress`, or baseline additions are permitted. All tests written must add to coverage without relaxing thresholds.
3. **Android SDK Version**:
   - `amaxoniaerp-pos` targets compileSdk 36, minSdk 29, Java 17.
   - Backend targets Java 21 toolchain.

---

# 4. Conclusion & Test Infrastructure Strategy

### 4.1 Feature Inventory (R1-R7)
- **R1**: Incomplete/Draft invoices live in "Facturas Pendientes", allow deletion/restoration, and never block Cash Close.
- **R2**: Date filters in "Seleccionar Factura" default to today; date range selector with <= 1 month constraint; cleanup of Estatus/Sucursal fields.
- **R3**: Strict active caja opening session (`id_caja`) filtering for Invoice History at the database query level.
- **R4**: Elimination of `caja.cod_almacen` query on Panama database during credit note generation.
- **R5**: Ticket reprint and PDF download/viewer from Invoice History.
- **R6**: Visual electronic status indicators and idempotent manual electronic invoice resend.
- **R7**: Multi-flavor build compatibility (`amaxonia`, `banescoVenezuela`, `listoerp`) and all quality gates green (`test`, `detekt`, `ktlintCheck`, `lint`, `koverVerifyAmaxoniaDebug`, `jacocoTestCoverageVerification`).

### 4.2 4-Tier E2E Testing Framework Strategy

```text
+-------------------------------------------------------------------------+
|                  4-TIER E2E TESTING FRAMEWORK ARCHITECTURE               |
+-------------------------------------------------------------------------+
| Tier 1: Category-Partition Method (TSL Equivalence Classes)             |
|   - Invoices: [Draft, Paid, Annulled, Credit, Mixed, FE-Failed]        |
|   - Dates: [Today, Past <1m, Past >1m, Inverted, Month Boundary]        |
|   - Caja: [Open Active, Closed, No Sequence, Multi-Caja]                |
|   - Countries: [Panama (PA), Venezuela (VE)]                            |
+-------------------------------------------------------------------------+
| Tier 2: Boundary Value Analysis (BVA)                                   |
|   - Dates: 0 days, 30 days, 31 days, 32 days (REJECT), Leap Year Feb 29 |
|   - Financials: $0.00, $0.01, Exact remaining amount, > Remaining +0.01 |
|   - Pagination: limit=0, 1, 100, 1000, 1001 (REJECT), offset=0         |
|   - Text bounds: 0 chars, 50 chars, 300 chars, 301 chars (truncate)     |
+-------------------------------------------------------------------------+
| Tier 3: Pairwise Combinatorial Matrix (2-Way Interaction Coverage)      |
|   - [Country: PA, VE] x [Flavor: Amaxonia, Banesco, ListoERP] x        |
|     [Payment: Cash, Credit, Mixed] x [Printer: SUNMI, HKA, NONE] x     |
|     [Action: Sale, NC-Full, NC-Part, Resend-FE, Reprint, Close-Caja]   |
+-------------------------------------------------------------------------+
| Tier 4: Real-World Workloads & Multi-Step Persona Scenarios             |
|   - Scenario A: Panama Cashier Daily Workflow (Open -> Sales -> Draft   |
|                 -> Panama Electronic NC -> Unblocked Close -> Ticket)   |
|   - Scenario B: Venezuela Multi-Currency Dual-Tender (Bs / USD / HKA)   |
|   - Scenario C: Offline Network Interruption Recovery (Lease & Dedup)   |
|   - Scenario D: Electronic Resend & PDF Export Workflow                 |
+-------------------------------------------------------------------------+
```

---

# 5. Verification Method

### 5.1 Verification Commands

#### Android POS (`amaxoniaerp-pos`)
```bash
# 1. Local JVM Unit Tests
./gradlew test --no-daemon

# 2. Static Analysis & Lint Gates
./gradlew detekt --no-daemon
./gradlew ktlintCheck --no-daemon
./gradlew lint --no-daemon

# 3. Kover Coverage Ratchet Gate
./gradlew :app:koverVerifyAmaxoniaDebug --no-daemon

# 4. Multi-Flavor Build Verification
./gradlew assembleAmaxoniaDebug --no-daemon
./gradlew assembleBanescoVenezuelaDebug --no-daemon
./gradlew assembleListoerpDebug --no-daemon
```

#### Backend Ktor (`amaxoniaerp-backend`)
```bash
# 1. Backend Test Suite
./gradlew test --no-daemon

# 2. Static Analysis & Formatting Gates
./gradlew detekt --no-daemon
./gradlew ktlintCheck --no-daemon

# 3. JaCoCo Coverage Ratchet Gate (CoveredRatio >= 0.46526415)
./gradlew test jacocoTestCoverageVerification --no-daemon

# 4. Full Build Packaging
./gradlew build --no-daemon
```

### 5.2 Key Inspection Files
- Contracts: `D:\PROGRAMMING\Kotlin\Amaxonia\contracts\README.md`
- Backend Routing & Exposure:
  - `D:\PROGRAMMING\Kotlin\Amaxonia\amaxoniaerp-backend\src\main\kotlin\com\amaxoniaerp\features\facturas\route\FacturasRoutes.kt`
  - `D:\PROGRAMMING\Kotlin\Amaxonia\amaxoniaerp-backend\src\main\kotlin\com\amaxoniaerp\features\creditnotes\route\CreditNoteRoutes.kt`
  - `D:\PROGRAMMING\Kotlin\Amaxonia\amaxoniaerp-backend\src\main\kotlin\com\amaxoniaerp\features\electronicinvoice\route\ElectronicInvoiceRoutes.kt`
- POS UI & Architecture:
  - `D:\PROGRAMMING\Kotlin\Amaxonia\amaxoniaerp-pos\app\src\main\java\com\amaxonia\pos\ui\history\HistoryViewModel.kt`
  - `D:\PROGRAMMING\Kotlin\Amaxonia\amaxoniaerp-pos\app\src\main\java\com\amaxonia\pos\ui\creditnotes\CreditNotesViewModel.kt`
  - `D:\PROGRAMMING\Kotlin\Amaxonia\amaxoniaerp-pos\app\src\main\java\com\amaxonia\pos\ui\caja\CierreCajaViewModel.kt`
