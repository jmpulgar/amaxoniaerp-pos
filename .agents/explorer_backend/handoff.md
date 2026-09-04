# Backend Codebase Investigation Report (R1 - R7)

## Executive Summary
This report provides a comprehensive technical investigation of the Ktor backend (`amaxoniaerp-backend`) for requirements R1 through R7 of the Amaxonia ERP-POS system. It covers invoice lifecycle ("En Espera" vs Cash Close), date and cash register query filtering, root cause analysis of the Panama electronic credit note SQL error (`caja.cod_almacen`), PDF generation/download mechanisms, idempotent electronic invoice resending, and quality gate configurations (Java 21, JaCoCo, Detekt, Ktlint).

---

## 1. Observation

### 1.1 R1: Facturas en Estado "En Espera" y Cierre de Caja
- **Invoice Table Definitions & Status**:
  - `BaseFacturasTable` (`src/main/kotlin/com/amaxoniaerp/features/facturas/data/FacturasTable.kt:6-28`):
    ```kotlin
    val idFactura = varchar("id_factura", S.VARCHAR_LENGTH_36)
    val codFactura = varchar("cod_factura", S.VARCHAR_LENGTH_32)
    val codEstatus = integer("cod_estatus").nullable()
    val idCaja = varchar("id_caja", S.VARCHAR_LENGTH_36)
    val formaPago = varchar("formapago", S.VARCHAR_LENGTH_20)
    ```
  - `EstatusTable` (`src/main/kotlin/com/amaxoniaerp/features/facturas/data/FacturasTable.kt:66-71`):
    ```kotlin
    object EstatusTable : Table("estatus") {
        val codEstatus = integer("cod_estatus")
        val descripcion = varchar("descripcion", S.VARCHAR_LENGTH_50)
        override val primaryKey = PrimaryKey(codEstatus)
    }
    ```
  - `FacturasSummaryMapper.kt:27-33`:
    ```kotlin
    val estatusFinal =
        if (codEstatus == 1 && formaPago.equals("contado", ignoreCase = true)) {
            "En Espera"
        } else {
            descripcionEstatus
        }
    ```
  - In normal completed sales (`ProcessSaleTransactionalRepository.kt:194`, `ProcessSaleInvoiceWrites.kt:45`), `cod_estatus` is written as `2` ("Procesada" / "Pagada"). `ANNULLED_INVOICE_STATUS` is `3`.
- **Cash Close ("Cierre de Caja") Blocking Mechanism**:
  - Route: `POST /api/cajas/close` (`src/main/kotlin/com/amaxoniaerp/features/caja/CajaRouting.kt:56`).
  - Workflow: `CajaSessionWorkflow.close` (`src/main/kotlin/com/amaxoniaerp/features/caja/application/CajaSessionWorkflow.kt:34-62`):
    ```kotlin
    // Lines 49-51
    if (store.countFacturasTemporalesPendientes(countryCode, request.id) > 0) {
        error("Existen facturas temporales pendientes por procesar")
    }
    ```
  - Exposed Store Adapter: `ExposedCajaSessionStore.kt:21-24`:
    ```kotlin
    override fun countFacturasTemporalesPendientes(countryCode: String, idSecuencia: String): Int =
        countFacturasTemporales(countryCode, idSecuencia)
    ```
  - Query in `CajaInventarioReader.kt:38-49`:
    ```kotlin
    internal fun countFacturasTemporales(countryCode: String, idSecuencia: String): Int {
        val facturaTable = SalesFacturaTableFactory.forCountry(countryCode)
        return facturaTable
            .select(facturaTable.formaPago)
            .where {
                (facturaTable.idCajaSecuencia eq idSecuencia) and
                    (facturaTable.codEstatus eq 1)
            }.count { row -> !row[facturaTable.formaPago].equals("credito", ignoreCase = true) }
    }
    ```

---

### 1.2 R2 & R3: Filtros de Fecha y Caja Activa

#### A. Credit Note Invoice Selection ("Seleccionar Factura")
- Route: `GET /api/pos/notas-credito/facturas` (`src/main/kotlin/com/amaxoniaerp/features/creditnotes/route/CreditNoteRoutes.kt:39`).
- Handler: `CreditNoteHandlers.listarFacturasElegibles` (`CreditNoteRoutes.kt:85-110`).
- Implementation: `CreditNoteQueries.kt:98-243` (`CreditNoteRepository.listEligibleInvoices`):
  ```kotlin
  if (fechaInicio != null && fechaFin != null) {
      query.andWhere {
          (CreditNoteFacturaTable.fechaFactura.between(fechaInicio, fechaFin)) or
              (CreditNoteFacturaTable.fechaFactura.isNull() and CreditNoteFacturaTable.fechaCreacion.between(
                  fechaInicio.atStartOfDay(),
                  fechaFin.plusDays(1).atStartOfDay().minusNanos(1),
              ))
      }
  }
  ```
- Current gap: `CreditNoteHandlers.listarFacturasElegibles` does not default `fecha_inicio` and `fecha_fin` to `LocalDate.now()`, nor does it enforce `fechaFin >= fechaInicio` or the 1-month maximum window at the route/validator level.

#### B. Invoice History ("Historial de Facturas")
- Route: `GET /facturas` and `GET /facturas/resumen` (`src/main/kotlin/com/amaxoniaerp/features/facturas/route/FacturasRoutes.kt:34-35`).
- Query Parser: `FacturasRoutes.kt:222-245` (`Parameters.toFacturasFilter()`):
  ```kotlin
  val sucursalValue = this["sucursal_id"]?.takeIf(String::isNotBlank)
  val sucursalId = sucursalValue?.let { value -> requireNotNull(value.toIntOrNull()) { "Invalid sucursal_id" } }
  val estatusList = this["estatus"]?.takeIf(String::isNotBlank)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
  ```
- Filter Object & SQL: `FacturasRepository.kt:34-41` and `FacturasRepository.kt:169-203` (`applyInvoiceFilters`):
  ```kotlin
  data class FacturasFilter(
      val search: String? = null,
      val usuario: String? = null,
      val sucursalId: Int? = null,
      val fechaInicio: LocalDate? = null,
      val fechaFin: LocalDate? = null,
      val estatusList: List<Int>? = null,
  )
  ```
- Current gaps:
  1. `FacturasFilter` retains `sucursalId` and `estatusList`.
  2. `FacturasFilter` lacks `cajaId` (or `idCaja`), so queries retrieve invoices from all cash registers across the company.
  3. `toFacturasFilter()` does not validate `fechaFin >= fechaInicio` or enforce the 1-month maximum span.

---

### 1.3 R4: Root Cause of `caja.cod_almacen` Error in Panama Electronic Credit Notes
- **Verbatim Error**: `Unknown column 'caja.cod_almacen' in 'field list'`.
- **Database Schema Difference**:
  - **Venezuela DB**: Table `caja` has column `cod_almacen INT NULL`.
  - **Panama DB**: Table `caja` **DOES NOT** have column `cod_almacen`. In Panama, warehouse assignment is resolved via `sucursal_almacen` (`id_sucursal`, `id_almacen`, `default_ventas`) or globally via `parametros_generales.cod_almacen`.
- **Backend Exposed Entity Definitions**:
  - `CajaTable` (`src/main/kotlin/com/amaxoniaerp/features/caja/data/CajaTable.kt:7-20`):
    ```kotlin
    object CajaTable : Table("caja") {
        val idCaja = varchar("id", S.VARCHAR_LENGTH_36)
        val codCaja = varchar("codigo", S.VARCHAR_LENGTH_50).nullable()
        val descripcion = varchar("descripcion", S.VARCHAR_LENGTH_100).nullable()
        val codEstatus = integer("activo").default(1)
        val idSucursal = integer("id_sucursal").nullable()
        val codAlmacen = integer("cod_almacen").nullable() // <--- Defined unconditionally
        val serieCaja = varchar("serie_caja", S.VARCHAR_LENGTH_10)
        val caja = varchar("caja", S.VARCHAR_LENGTH_50).nullable()
        val fondoApertura = decimal("fondo_apertura", S.DECIMAL_PRECISION_10, 2).nullable()
        val impresoraModelo = varchar("impresora_modelo", S.VARCHAR_LENGTH_50).nullable()
        override val primaryKey = PrimaryKey(idCaja)
    }
    ```
  - `SalesCajaTable` (`src/main/kotlin/com/amaxoniaerp/features/sales/data/SalesTables.kt:231-239`):
    ```kotlin
    object SalesCajaTable : Table("caja") {
        val id = varchar("id", S.VARCHAR_LENGTH_36)
        val idSucursal = integer("id_sucursal").nullable()
        val codAlmacen = integer("cod_almacen").nullable() // <--- Defined unconditionally
        val codigo = varchar("codigo", S.VARCHAR_LENGTH_50).nullable()
        val facturaCorrelativo = integer("factura_correlativo")
        override val primaryKey = PrimaryKey(id)
    }
    ```
- **Existing Workarounds in Codebase**:
  - `CajaCatalogReader.kt:228-232`:
    ```kotlin
    val isVE = countryCode.equals("VE", ignoreCase = true)
    val cajaColumns = CajaTable.columns.filter { isVE || it != CajaTable.codAlmacen }
    ```
  - `WarehouseContext.kt:22-28`:
    ```kotlin
    val isVE = countryCode.equals("VE", ignoreCase = true)
    val columns = if (isVE) listOf(SalesCajaTable.idSucursal, SalesCajaTable.codAlmacen) else listOf(SalesCajaTable.idSucursal)
    ```
  - `CreditNoteCajaTable` in `CreditNoteTables.kt:174-185` was already defined without `codAlmacen`.
- **Root Cause**: Any code path that invokes `CajaTable.selectAll()` or queries `caja` without country-aware column projections causes Exposed to generate `SELECT caja.id, caja.codigo, ..., caja.cod_almacen FROM caja`, which immediately fails on MySQL in Panama databases.

---

### 1.4 R5 & R6: PDF Retrieval & Idempotent Electronic Resending

#### A. PDF Retrieval and Ticket Reprinting
- **PAC PDF Retrieval**:
  - `TheFactoryHkaRestClient.kt:126-152`:
    ```kotlin
    override suspend fun downloadPdf(baseUrl: String, token: PacAuthToken, cufe: String): Result<ByteArray> =
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/api/DescargaPDF"
            val response: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${token.token}")
                setBody(mapOf("cufe" to cufe))
            }
            response.body<ByteArray>()
        }
    ```
  - `PanamaCreditNotePdfStorage.kt:18-82` (`FileSystemPanamaCreditNotePdfStorage`):
    Stores downloaded PDFs under `{dataBasePath}/{companyDb}/documentos_fiscales/notas_credito/{creditNoteId}/{numeroDocumentoFiscal}.pdf`.
- **Thermal Receipt Reprinting**:
  - Route: `GET /facturas/{id}/print-payload` (`src/main/kotlin/com/amaxoniaerp/features/facturas/route/FacturasRoutes.kt:38`).
  - Repository: `FacturasRepository.kt:243-292` (`getPrintPayload`): Pure read-only query mapping products, discounts, taxes, payments, and change. Generates no new database rows or state mutations.

#### B. Electronic Resending & Idempotency
- **Manual Resend Route**:
  - Route: `POST /api/facturacion-electronica/{invoiceId}/enviar` (`src/main/kotlin/com/amaxoniaerp/features/electronicinvoice/route/ElectronicInvoiceRoutes.kt:33`).
- **Commercial vs Electronic Separation**:
  - `ProcessSaleUseCase.kt:28-81`: Commercial sale commit occurs in step 1 (`ProcessSaleTransactionalRepository`). Electronic invoice transmission is step 2 (`processElectronicInvoiceSafely`), running strictly outside the commercial transaction.
  - If electronic submission fails, commercial state remains committed (`feError` returned to client).
- **Idempotency Guard**:
  - For **Venezuela** (`VenezuelaInvoiceStrategy.kt:253-298`):
    `repository.loadAlreadyIssued(database, invoiceId)` checks if `numeroDocumentoFiscal` is already present. If complete, it returns `ElectronicInvoiceResult.AlreadyIssued` without contacting HKA.
  - For **Panama** (`PanamaInvoiceProcessor.kt:246-288`):
    `updateInvoiceWithFEResponse` executes `FacturasTablePA.update({ FacturasTablePA.idFactura eq invoiceId })` updating `cufe`, `qr`, `fechaRecepcionDGI`, `nroProtocoloAutorizacion` on the existing row. No duplicate sales rows (`factura`, `factura_detalle`, `caja_nueva`) are created.

---

### 1.5 R7: Quality Gates & Backend Build
- **Toolchain & Version Catalog**:
  - Java Toolchain: JDK 21 (`jvmToolchain(21)` in `build.gradle.kts:14`).
  - Ktor 3.3.2, Kotlin 2.2.21, Exposed 0.61.0, H2 2.3.232, Detekt 1.23.8, Ktlint Gradle 14.2.0, JaCoCo 0.8.13 (`libs.versions.toml`).
- **Gradle Tasks**:
  - `./gradlew test`
  - `./gradlew detekt`
  - `./gradlew ktlintCheck`
  - `./gradlew jacocoTestCoverageVerification` (Coverage minimum: `0.46526415` in `build.gradle.kts:59`).
  - `./gradlew build`
- **Testing Architecture**:
  - Uses in-memory H2 with MySQL mode (`jdbc:h2:mem:...;MODE=MySQL;DB_CLOSE_DELAY=-1`).
  - Schema fixtures initialized via `SchemaUtils.create(...)` per test suite.
  - Test suites include unit, integration, multi-country contract matrices, and architecture isolation tests (`FeatureDependencyArchitectureTest`, `TenantSeamArchitectureTest`).

---

## 2. Logic Chain

```
[Requirement Analysis]
   │
   ├─► R1: Invoice Lifecycle & Cash Close
   │     ├─ Observation: `CajaSessionWorkflow.close` checks `countFacturasTemporales(countryCode, idSecuencia) > 0`
   │     ├─ Observation: `countFacturasTemporales` counts `cod_estatus == 1 and formaPago != 'credito'`
   │     ├─ Observation: `FacturasSummaryMapper` displays `cod_estatus == 1 && formaPago == 'contado'` as "En Espera"
   │     └─ Deduction: "En Espera" was treated as an uncommitted temporary invoice blocking cash close.
   │                   R1 requires that "En Espera" invoices do NOT block cash close, and that pending drafts
   │                   are managed and cleanable via the pending invoices workflow.
   │
   ├─► R2 & R3: Query Date & Active Cash Register Filtering
   │     ├─ Observation: `FacturasFilter` has `sucursalId`, `estatusList`, and lacks `cajaId`
   │     ├─ Observation: `CreditNoteQueries.listEligibleInvoices` does not default to `LocalDate.now()`
   │     ├─ Observation: Neither route validates `fechaFin >= fechaInicio` nor limits duration to 1 month
   │     └─ Deduction: Clean up `FacturasFilter` by removing `sucursalId`, `estatusList`, `campo`;
   │                   add `cajaId` to `FacturasFilter` and filter `tabla.idCaja eq filter.cajaId`;
   │                   enforce `fechaFin >= fechaInicio` and max 1-month range at query validation level.
   │
   ├─► R4: Panama Credit Note `caja.cod_almacen` Root Cause
   │     ├─ Observation: Table `caja` in Panama DB has no `cod_almacen` column (only Venezuela has it)
   │     ├─ Observation: Exposed `CajaTable` & `SalesCajaTable` declare `codAlmacen` unconditionally
   │     ├─ Observation: Workarounds exist in `CajaCatalogReader` and `WarehouseContext` via manual column filtering
   │     └─ Deduction: Any query executing unprojected SELECT on `CajaTable` fails on Panama DB.
   │                   The table model must separate country schemas (`CajaTableVE` vs `CajaTablePA`) or
   │                   strictly use projected column slices avoiding `caja.cod_almacen` on non-VE databases.
   │
   └─► R5 & R6: PDF Retrieval, Reprinting & Idempotent Resending
         ├─ Observation: `TheFactoryHkaRestClient.downloadPdf` calls `POST /api/DescargaPDF` with CUFE
         ├─ Observation: `FacturasRepository.getPrintPayload` is a non-mutating query
         ├─ Observation: `ProcessSaleUseCase` splits commercial write from PAC submission
         ├─ Observation: `ElectronicInvoiceRoutes.enviar` updates existing rows without inserting new commercial records
         └─ Deduction: The architecture already guarantees complete separation of commercial transactions
                       from electronic processing. Resending is idempotent and updates existing records.
```

---

## 3. Caveats
- **Multi-Tenant Database Differences**: The backend connects dynamically to different MySQL databases per tenant and country. Schema column discrepancies (such as `cod_almacen` in `caja`) must never be assumed to exist across all country schemas.
- **H2 Test Mode Limitations**: In-memory H2 tests create tables based on Exposed definitions. If an Exposed table includes `cod_almacen`, H2 creates the column in tests even if production Panama MySQL lacks it. Verification must include column-projection assertions.
- **Auto-Close Behavior**: `CajaSessionWorkflow.open` already executes auto-close with `verifyFacturasTemporales = false` (`CajaSessionWorkflow.kt:148`). The standard close workflow (`CajaSessionWorkflow.close`) is where the strict blocking check resided.

---

## 4. Conclusion
1. **R1**: In `CajaSessionWorkflow.kt`, cash close blocking on `countFacturasTemporalesPendientes > 0` must be aligned so that pending/en espera invoices do not prevent cashiers from closing their shifts.
2. **R2 & R3**: In `FacturasRoutes.kt`, `FacturasRepository.kt`, and `CreditNoteRoutes.kt`:
   - Implement date range validation (`fechaFin >= fechaInicio`, max 1 month) and default to current date (`LocalDate.now()`) when selecting invoices for credit notes.
   - Remove `sucursal_id`, `estatus`, and `campo` from `FacturasFilter` and the invoice history query pipeline.
   - Add `cajaId` to `FacturasFilter` and filter strictly on `tabla.idCaja eq filter.cajaId`.
3. **R4**: The `Unknown column 'caja.cod_almacen'` error is caused by querying the `caja` table with `codAlmacen` included in the Exposed table definition when connected to a Panama database. The clean fix is separating `CajaTable` by country or projecting only common columns (`id`, `codigo`, `descripcion`, `id_sucursal`, `serie_caja`, `impresora_modelo`).
4. **R5 & R6**: PDF downloads via `TheFactoryHkaRestClient.downloadPdf`, receipt reprints via `getPrintPayload`, and manual electronic resending via `POST /api/facturacion-electronica/{invoiceId}/enviar` are architecturally sound and idempotent.
5. **R7**: All backend quality gates (Java 21, JaCoCo threshold, Detekt, Ktlint, and unit/integration tests) are fully configured and verifiable via standard Gradle commands.

---

## 5. Verification Method

### 5.1 Gradle Quality Gate Commands
To verify the backend quality gates independently:
```powershell
cd D:\PROGRAMMING\Kotlin\Amaxonia\amaxoniaerp-backend

# 1. Run unit and integration test suite
.\gradlew test

# 2. Run Detekt static analysis
.\gradlew detekt

# 3. Run Ktlint style verification
.\gradlew ktlintCheck

# 4. Verify JaCoCo code coverage threshold
.\gradlew jacocoTestCoverageVerification

# 5. Full build assembly
.\gradlew build
```

### 5.2 Key Files for Inspection
| Requirement | Key Backend Files to Inspect |
|---|---|
| **R1** | `com/amaxoniaerp/features/caja/application/CajaSessionWorkflow.kt`<br>`com/amaxoniaerp/features/caja/data/CajaInventarioReader.kt`<br>`com/amaxoniaerp/features/facturas/data/FacturasSummaryMapper.kt` |
| **R2 & R3** | `com/amaxoniaerp/features/facturas/route/FacturasRoutes.kt`<br>`com/amaxoniaerp/features/facturas/data/FacturasRepository.kt`<br>`com/amaxoniaerp/features/creditnotes/route/CreditNoteRoutes.kt`<br>`com/amaxoniaerp/features/creditnotes/data/CreditNoteQueries.kt` |
| **R4** | `com/amaxoniaerp/features/caja/data/CajaTable.kt`<br>`com/amaxoniaerp/features/sales/data/SalesTables.kt`<br>`com/amaxoniaerp/features/sales/data/WarehouseContext.kt`<br>`com/amaxoniaerp/features/caja/data/CajaCatalogReader.kt` |
| **R5 & R6** | `com/amaxoniaerp/features/electronicinvoice/pac/thefactory/TheFactoryHkaRestClient.kt`<br>`com/amaxoniaerp/features/electronicinvoice/storage/PanamaCreditNotePdfStorage.kt`<br>`com/amaxoniaerp/features/electronicinvoice/route/ElectronicInvoiceRoutes.kt`<br>`com/amaxoniaerp/features/sales/application/ProcessSaleUseCase.kt` |
| **R7** | `build.gradle.kts`<br>`gradle/libs.versions.toml` |
