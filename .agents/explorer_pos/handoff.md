# Handoff Report — POS Codebase Exploration (Android App)

## 1. Observation

### R1. Facturas en estado "En Espera" y Cierre de Caja
- **Creación y Almacenamiento Local de Facturas**:
  - `data/local/db/DraftInvoiceEntity.kt:19-36`: Tabla `draft_invoices` con `@PrimaryKey val id: String`, `clientId`, `itemsJson`, `totalMinor: Long`, `itemCount: Int`, montos minor-unit (MONEY-001) y `currencyCode`.
  - `data/local/db/DraftInvoiceEntity.kt:38-54`: `DraftInvoiceDao` con métodos `getAll()`, `insert()`, `deleteById()`, `deleteAll()`, `count()`.
  - `data/local/db/PendingInvoiceEntity.kt:18-43`: Tabla `pending_invoices` con `id`, `countryCode`, `payloadJson`, `localInvoiceNumber`, `clientName`, `status` (`PENDING`, `SENDING`, `SENT`, `FAILED`, `INVALID`), `tenantId`, `leasedUntil`, etc.
  - `data/repository/RoomDraftInvoiceRepository.kt:16-63`: Implementación de `DraftInvoiceRepository` que mapea `DraftInvoiceEntity` ↔ `DraftInvoice` (dominio) usando `MinorUnitMoney`.
  - `domain/usecase/cart/SaveDraftInvoiceUseCase.kt:21-47`: Persiste borradores desde el carrito generando un `DraftInvoice` con snapshot financiero y serialización JSON de items.
- **Creación y Almacenamiento Remoto de Facturas**:
  - `domain/usecase/payment/PrepareSaleUseCase.kt:308-340`: `buildInvoice(...)` asigna por defecto `codEstatus = PROCESSED_STATUS` (constante `PROCESSED_STATUS = 2` en línea 451).
  - `data/repository/ApiTransactionRepository.kt:221-254`: Mapeo de `FacturaSummaryDto` a `Transaction`. Líneas 226-227: `estatus.equals("En Espera", ignoreCase = true) || estatus.equals("Pendiente", ignoreCase = true) -> TransactionStatus.PENDING`.
  - En Backend `features/facturas/data/FacturasSummaryMapper.kt:27-33`: Asigna `estatus = "En Espera"` cuando `codEstatus == 1 && formaPago.equals("contado", ignoreCase = true)`.
- **Pantalla "Facturas Pendientes"**:
  - `ui/drafts/DraftInvoicesScreen.kt:61-110`: Título TopAppBar `"Facturas pendientes"` (línea 118). Muestra lista reactiva de `DraftInvoiceCard`, con acciones `onLoad` (`viewModel.loadDraftIntoCart`) y `onDelete` (`viewModel.deleteDraft`).
  - `ui/drafts/DraftInvoicesViewModel.kt:14-58`: Expone `drafts: StateFlow<List<DraftInvoice>>`, `loadDrafts()`, `deleteDraft(id)`, y `loadDraftIntoCart(draft)` que delega a `RestoreDraftInvoiceUseCase` y elimina el borrador tras cargarlo exitosamente.
- **Validación y Bloqueo en "Cierre de Caja"**:
  - `ui/caja/CierreCajaViewModel.kt:44-59, 106-143`: Carga resumen con `cajaRepository.getCierreSummary()` y ejecuta cierre con `cajaRepository.closeCaja(buildCierreRequest(summary))`.
  - `data/repository/CajaRepositoryImpl.kt:112-120`: `getCierreSummary()` invoca `loadCierreSummary(caja, sequenceId, verifyPendingInvoices = true)`.
  - `data/repository/CajaRepositoryImpl.kt:137-143`: `loadCierreSummary(...)` llama `cajaApi.getCajaSecuencia(idSecuencia = sequenceId, verifyFacturasTemporales = verifyPendingInvoices, ...)`.
  - `data/remote/api/CajaApiImpl.kt:60-75`: Envía `parameter("by.verificar_facturas_temporales", "1")` al endpoint `api/cajas/secuencia`.
  - En Backend `features/caja/application/CajaSessionWorkflow.kt:49-51`: **Punto crítico de bloqueo**:
    ```kotlin
    if (store.countFacturasTemporalesPendientes(countryCode, request.id) > 0) {
        error("Existen facturas temporales pendientes por procesar")
    }
    ```
  - En Backend `features/caja/data/CajaInventarioReader.kt:38-49`: `countFacturasTemporales` cuenta facturas de la secuencia con `codEstatus == 1` y `formaPago != 'credito'`.

---

### R2. Filtros de Fecha en "Seleccionar Factura" (Nota de Crédito) e "Historial de Facturas"
- **"Seleccionar Factura" (Credit Note flow)**:
  - `ui/creditnotes/CreditNotesViewModel.kt:75-126`: Gestiona `InvoiceDateFilterType` y `InvoiceDateFilter`. En `loadSourceInvoices()` (líneas 289-309) llama a `creditNoteRepository.getSourceInvoices(search, fechaInicio, fechaFin)`.
  - `ui/creditnotes/CreditNotesState.kt:26-37`: `InvoiceDateFilterType` contiene `MES_ACTUAL` (default), `MES_ANTERIOR`, `TODAS`, `PERSONALIZADO`. En `resolveDateRange()` (líneas 38-56) `MES_ACTUAL` calcula el primer y último día del mes; `TODAS` pasa `null to null`.
  - `ui/creditnotes/CreditNotesScreen.kt:501-611`: `InvoiceDateFilterSection` presenta chips para los tipos y un acordeón `ElevatedCard` con dos campos de texto planos `OutlinedTextField` ("Desde", "Hasta") con placeholder `"AAAA-MM-DD"`.
  - **Deficiencia observada**: No usa selector de fechas (DatePicker) nativo/Material 3, no valida que `Hasta >= Desde`, ni valida el límite de 1 mes (31 días), y por defecto carga el mes actual en lugar del día de hoy.
- **"Historial de Facturas" — Identificación de Campos Muertos**:
  - `ui/history/HistoryScreen.kt:453-461`: `OutlinedTextField` de `"Sucursal"`.
  - `ui/history/HistoryScreen.kt:494-506`: `HistoryStatusFilter` con `OutlinedTextField` de `"Estatus"`.
  - `ui/history/HistoryViewModel.kt:57-59`: `fun onSucursalChanged(value: String)`.
  - `ui/history/HistoryViewModel.kt:69-72`: `fun onEstatusChanged(value: String)`.
  - `domain/repository/InvoiceHistoryRepository.kt:5-12`: `data class InvoiceHistoryFilter(search, usuario, sucursalId, fechaInicio, fechaFin, estatus)`.
  - `data/remote/api/SalesApiImpl.kt:289-296`: `applyInvoiceHistoryFilter` incluye `parameter("sucursal_id", it)` y `parameter("estatus", it)`.
  - `app/src/test/java/com/amaxonia/pos/data/remote/api/SalesApiFilterTest.kt:18, 21, 27, 30, 41, 44`: Pruebas que validan serialización de `sucursal_id` y `estatus`.
  - `ui/history/HistoryScreenPreviews.kt:53`: Parámetros de preview con `estatus`.

---

### R3. Filtrado por Caja de la Apertura Activa en Historial de Facturas
- **Seguimiento de la Sesión / Caja Activa**:
  - `domain/repository/CajaRepository.kt:10-38` & `data/repository/CajaRepositoryImpl.kt:25-37`: `activeCaja: StateFlow<Caja?>`, `activeCajaSecuencia: StateFlow<CajaSecuencia?>`.
  - `CajaRepositoryImpl.restoreActiveCajaIfValid()` lee la caja activa persistida en `LocalStore.readActiveCajaForToday()`.
  - `CajaRepositoryImpl.checkCajaStatus(cajaId)` consulta `api/cajas/$cajaId/status` y actualiza `activeSecuencia` y `_activeCajaSecuencia`.
- **Fuga actual en Historial de Facturas**:
  - `composition/HistoryGraph.kt:7`: `HistoryViewModel(DependencyContainer.invoiceHistoryRepository)` no recibe `CajaRepository` ni `activeCaja`.
  - `domain/repository/InvoiceHistoryFilter`: No tiene campo `cajaId`.
  - `data/remote/api/SalesApiImpl.kt:applyInvoiceHistoryFilter`: No envía `parameter("caja_id", ...)` ni `parameter("id_caja", ...)`.
  - Consecuencia: `GET /facturas` en backend no recibe filtro de caja y retorna facturas de todas las cajas si el usuario tiene acceso.

---

### R5. Reimpresión de Tickets y Descarga de PDFs desde Historial
- **Arquitectura de Reimpresión de Tickets**:
  - `domain/usecase/payment/PrintInvoiceUseCase.kt:11-27`: Interface `InvoicePrintGateway` y caso de uso `PrintInvoiceUseCase(gateway)`.
  - `data/printer/DefaultInvoicePrintGateway.kt:17-97`:
    - Para Panamá (`PANAMA_CODE`): Obtiene el payload con `salesRepository.getPrintPayload(remoteInvoiceId)` (o fallback `LocalInvoicePrintPayloadMapper.fromTransaction`), formatea el ticket vía `PanamaInvoiceTicketFormatter().format(payload, countryCode)` y lo envía a `printer.printTicket(ticket)`.
    - Para Venezuela (`VENEZUELA_CODE`): Si es fiscal (`THE_FACTORY_HKA`), ejecuta `printer.printReceipt(transaction)`; si es ticket Sunmi (`SUNMI_V2`), formatea vía `VenezuelaInvoiceTicketFormatter().format(payload)` y lo envía a `printer.printTicket(ticket)`.
  - `composition/DependencyContainer.kt:287-289`: `printInvoiceUseCase` está instanciado y disponible en el contenedor de dependencias.
- **Detalle de Factura en Historial**:
  - `ui/history/FacturaDetalleSheet.kt:52-91`: Actualmente muestra cabecera, cliente, productos y total, pero **no incluye botón de acción para reimprimir ticket** ni para **descargar/ver PDF**.
- **Arquitectura de Descarga/Visualización de PDF**:
  - Backend PAC: `TheFactoryHkaRestClient.downloadPdf(baseUrl, token, cufe)` (endpoint `/api/DescargaPDF`) ya implementado y probado en backend.
  - En POS: Se requiere método en `SalesApi` (`getInvoicePdf(invoiceId): Result<ByteArray>`), almacenamiento en cache temporal (`context.cacheDir`), e invocación de visor mediante `Intent(Intent.ACTION_VIEW)` con `FileProvider` (`application/pdf`).

---

### R6. Reenvío Manual de Facturas Electrónicas y Separación Comercial/Electrónica
- **Estado Electrónico en Modelo y UI**:
  - `domain/model/sales/FacturaSummaryDto.kt:10-28`: Contiene `codigoFiscal`, `numeroDocumentoFiscal`, `fechaDgi`.
  - `domain/model/Transaction.kt:12-28`: Modelo de dominio en UI **carece** de los campos fiscales electrónicos (`codigoFiscal`, `numeroDocumentoFiscal`, `fechaDgi`, `hasElectronicError`).
  - `ui/history/TransactionCard.kt:119-137`: Solo renderiza `StatusBadge(status = transaction.status)` (`PAGADO`, `PENDIENTE`, `ANULADO`). No tiene indicadores para facturación electrónica (e.g., "Sin CUFE", "FE Exitosa", "Error FE").
- **Endpoint y Flujo de Reenvío en Backend**:
  - Backend `features/electronicinvoice/route/ElectronicInvoiceRoutes.kt:26-35`: Endpoint `POST /api/facturacion-electronica/{invoiceId}/enviar`.
  - Backend `PanamaInvoiceProcessor.kt` y `ElectronicInvoiceRepository.kt`: Ejecuta el envío al PAC sin recrear la factura en la base de datos comercial (`updateInvoiceWithFEResponse` actualiza `numeroDocumentoFiscal`, `cufe`, `qr`, `fechaRecepcionDGI` in-place).
  - Idempotencia: Si la factura ya fue autorizada o si no aplica FE, retorna `ElectronicInvoiceResult.AlreadyIssued` o `NotApplicable`.
- **Integración Requerida en POS**:
  - Agregar método `resendElectronicInvoice(invoiceId)` en `SalesApi`, `SalesRepository`, `InvoiceHistoryRepository` y `HistoryViewModel`.
  - En `FacturaDetalleSheet.kt` y/o `TransactionCard.kt`, agregar botón "Reenviar Factura Electrónica" visible cuando la factura pertenezca a un régimen electrónico y esté pendiente de emisión fiscal.

---

### R7. Quality Gates & Build Configuration
- **Sistema de Compilación**:
  - `settings.gradle.kts`: Gradle con Version Catalog `gradle/libs.versions.toml`.
  - `app/build.gradle.kts:35-146`: Android SDK compileSdk = 36, minSdk = 29, targetSdk = 36, Java 11/17 compatibility.
  - Product Flavors (dimension "brand"):
    1. `amaxonia` (`applicationId = "com.amaxonia.pos"`, `DEFAULT_COUNTRY_CODE = "PA"`)
    2. `banescoVenezuela` (`applicationId = "com.amaxonia.pos.banesco"`, `DEFAULT_COUNTRY_CODE = "VE"`)
    3. `listoerp` (`applicationId = "com.amaxonia.pos.listoerp"`, `DEFAULT_COUNTRY_CODE = "VE"`)
- **Herramientas de Análisis Estático & Cobertura**:
  - Detekt: `config/detekt/detekt.yml`, `config/detekt/detekt-baseline.xml` (plugins `io.gitlab.arturbosch.detekt 1.23.8`).
  - ktlint: `org.jlleitschuh.gradle.ktlint 12.1.2`, ktlint version 1.5.0, `android = true`, `ignoreFailures = false`.
  - Android Lint: Tarea `./gradlew lint`.
  - Kover: `koverVerifyAmaxoniaDebug` con regla de trinquete (ratchet) `minValue = 15%` y `minValue = 4383` líneas cubiertas.
- **Suite de Pruebas Unitarias**:
  - 63+ archivos de pruebas en `app/src/test/java/com/amaxonia/pos/...` usando JUnit 4, Robolectric 4.14.1, Turbine 1.2.0, Kotlinx Coroutines Test 1.9.0.

---

## 2. Logic Chain

1. **R1 (Facturas en Espera y Cierre de Caja)**:
   - *De observación*: `PrepareSaleUseCase` asigna `codEstatus = 2` a ventas regulares, mientras que `FacturasSummaryMapper` etiqueta con "En Espera" facturas con `codEstatus = 1` y pago de contado. `CajaSessionWorkflow.kt:49` bloquea el cierre si `countFacturasTemporalesPendientes > 0`.
   - *Deducción*: Las facturas en espera no son el estado final de una venta regular completada. Para que no impidan el cierre de caja, el backend no debe abortar en `countFacturasTemporalesPendientes > 0` cuando se trata de borradores/espera controlados, y el POS debe permitir visualizarlas y eliminarlas explícitamente desde "Facturas Pendientes" (`DraftInvoicesScreen.kt`).

2. **R2 (Filtros de Fecha e Historial Limpio)**:
   - *De observación*: `CreditNotesState` inicializa `InvoiceDateFilter` con `MES_ACTUAL`, y `CreditNotesScreen` usa `OutlinedTextField` de texto libre sin validaciones de rango. En `HistoryScreen`, `HistoryViewModel`, `InvoiceHistoryFilter` y `SalesApiImpl`, persisten campos `estatus` y `sucursal_id` sin propósito funcional actual.
   - *Deducción*: Remover `estatus` y `sucursal_id` de toda la cadena (UI -> ViewModel -> Repository -> DTO -> API) elimina código muerto y previene inconsistencias. Reemplazar los textfields por un selector con DatePicker Compose y validaciones (Hasta >= Desde y rango <= 31 días) garantiza consultas acotadas y válidas hacia el backend.

3. **R3 (Aislamiento por Caja Activa)**:
   - *De observación*: `CajaRepository` dispone en todo momento de `activeCaja`, pero `HistoryViewModel` y `InvoiceHistoryFilter` no lo consumen. `BaseFacturasTable` en backend contiene `idCaja`.
   - *Deducción*: Inyectar `CajaRepository` a `HistoryViewModel`, propagar `cajaId = activeCaja?.idCaja` en `InvoiceHistoryFilter`, y enviar `parameter("caja_id", cajaId)` a `api/facturas` filtra a nivel de base de datos (`andWhere { tabla.idCaja eq filter.cajaId }`), previniendo filtración visual de otras cajas.

4. **R5 (Reimpresión y PDF)**:
   - *De observación*: `DefaultInvoicePrintGateway` ya cuenta con formateadores de tickets para Panamá y Venezuela que consultan `salesRepository.getPrintPayload(facturaId)`.
   - *Deducción*: Añadir un trigger en `FacturaDetalleSheet` que invoque `printInvoiceUseCase` permite reimprimir sin alterar ni duplicar registros. Para PDF, exponer la descarga de bytes y abrir el archivo vía `FileProvider` con `Intent.ACTION_VIEW` completa el flujo.

5. **R6 (Reenvío Manual Idempotente)**:
   - *De observación*: El backend expone `POST /api/facturacion-electronica/{invoiceId}/enviar` que procesa idempotentemente la factura existente actualizando CUFE/QR en `FacturasTablePA`.
   - *Deducción*: Conectar este endpoint en `SalesApi` y `HistoryViewModel`, y enriquecer `Transaction` con estado electrónico, permite al cajero reintentar la transmisión fiscal desde el historial sin duplicar transacciones comerciales.

---

## 3. Caveats

- **Impresoras Físicas en Entorno Local**: En ejecución local JVM / pruebas unitarias, las pruebas de impresión fiscal HKA y Sunmi deben ejecutarse con mocks/fakes de `PrinterProvider`, ya que los dispositivos físicos o librerías `.aar` de hardware solo están disponibles en hardware real (Sunmi POS o The Factory HKA serial/bluetooth).
- **Flujo Offline**: Las facturas en cola local `pending_invoices` (offline) no poseen `idFactura` remoto hasta sincronizarse con el backend, por lo que el reenvío electrónico y descarga de PDF PAC aplican únicamente a facturas que ya cuentan con identificador remoto.
- **Régimen por País**: El reenvío electrónico al PAC vía `TheFactoryHkaRestClient` aplica principalmente a Panamá (`PA`), mientras que para Venezuela (`VE`) el procesamiento digital o fiscal se rige por su respectivo driver/impresora o facturación fiscal.

---

## 4. Conclusion

El módulo POS en `amaxoniaerp-pos` cuenta con una arquitectura limpia (`ui -> domain <- data`, `composition`) lista para recibir los ajustes requeridos:
1. **R1**: La pantalla `DraftInvoicesScreen` ("Facturas pendientes") ya soporta ciclo de vida y eliminación de borradores; se debe coordinar con backend la eliminación de la restricción bloqueante en `CajaSessionWorkflow.kt`.
2. **R2**: Los campos `estatus` y `sucursal_id` están claramente localizados en 8 archivos del POS y listos para ser removidos. Los selectores de fechas deben migrarse a DatePickers con validación de rango <= 30/31 días y default en fecha de hoy.
3. **R3**: Conectar `CajaRepository.activeCaja` con `HistoryViewModel` y `InvoiceHistoryFilter` para enviar `caja_id` en `GET /facturas`.
4. **R5**: Conectar `PrintInvoiceUseCase` en `FacturaDetalleSheet` y añadir handler para descarga/visualización de PDF.
5. **R6**: Conectar `POST /api/facturacion-electronica/{invoiceId}/enviar` en `SalesApi` y enriquecer `Transaction` y `FacturaDetalleSheet` con estado y acción de reenvío.
6. **R7**: Toda la infraestructura de calidad (Detekt, ktlint, Android Lint, Kover con ratchet de 4383 líneas y 3 flavors de compilación) está configurada y lista para validar cualquier modificación futura.

---

## 5. Verification Method

Para verificar independientemente el estado actual del repositorio POS:
```bash
# Navegar al directorio del POS
cd D:\PROGRAMMING\Kotlin\Amaxonia\amaxoniaerp-pos

# Ejecutar tests unitarios en JVM
./gradlew test

# Ejecutar análisis estático
./gradlew detekt
./gradlew ktlintCheck
./gradlew lint

# Validar cobertura con Kover
./gradlew :app:koverVerifyAmaxoniaDebug

# Compilar todos los flavors debug
./gradlew assembleAmaxoniaDebug
./gradlew assembleBanescoVenezuelaDebug
./gradlew assembleListoerpDebug
```
Archivos clave a inspeccionar:
- `app/src/main/java/com/amaxonia/pos/ui/history/HistoryViewModel.kt`
- `app/src/main/java/com/amaxonia/pos/ui/history/HistoryScreen.kt`
- `app/src/main/java/com/amaxonia/pos/ui/history/FacturaDetalleSheet.kt`
- `app/src/main/java/com/amaxonia/pos/ui/creditnotes/CreditNotesViewModel.kt`
- `app/src/main/java/com/amaxonia/pos/ui/creditnotes/CreditNotesScreen.kt`
- `app/src/main/java/com/amaxonia/pos/data/repository/ApiTransactionRepository.kt`
- `app/src/main/java/com/amaxonia/pos/data/remote/api/SalesApiImpl.kt`
- `app/src/main/java/com/amaxonia/pos/data/printer/DefaultInvoicePrintGateway.kt`
