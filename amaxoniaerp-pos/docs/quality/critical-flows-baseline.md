# Fase 0 — Registro de flujos críticos existentes

Este documento describe comportamiento observado, no comportamiento deseado. Será la guía para
crear pruebas de caracterización antes de extraer casos de uso o modificar dependencias.

## 1. Autenticación y selección de empresa

```text
LoginScreen
  -> LoginViewModel.onLoginClick
  -> AuthRepository.login
  -> AuthRepositoryImpl.login
  -> ApiService.login
  -> LocalStore.saveAuthSnapshot
  -> navegación select_company
  -> CompanySelectionViewModel.selectCompany
  -> AuthRepositoryImpl.selectCompany
  -> ApiService.selectCompany
  -> LocalStore.saveCompanySession
  -> sincronización inicial / dashboard
```

Puntos de entrada: `LoginViewModel.kt:64`, `CompanySelectionViewModel.kt:54`,
`AuthRepositoryImpl.kt:19` y `AuthRepositoryImpl.kt:33`.

Riesgo a caracterizar: restauración de sesión, selección de país/base URL, tokens offline y
estado cuando existe autenticación pero no empresa seleccionada.

## 2. Carrito y venta online

```text
DashboardViewModel / CartViewModel
  -> CartRepository (items, cliente, vendedor, sucursal)
  -> CartScreen.onCheckout
  -> AppNavigation payment/{total}
  -> PaymentScreen
  -> PaymentViewModel.processPayment
  -> validaciones de cliente, caja, stock, moneda y formas de pago
  -> construcción de ProcessSaleRequestDto
  -> SalesRepository.processSale
  -> SalesRepositoryImpl.processSale
  -> SalesApiImpl.processSale
  -> respuesta, transacción, impresión y pantalla de éxito
```

Punto central actual: `PaymentViewModel.kt:220`. El orden, DTO, redondeos y mensajes deben
capturarse mediante golden tests antes de extraer lógica.

## 3. Venta offline y reenvío

```text
PaymentViewModel.processPayment (NetworkMonitor offline)
  -> ProcessSaleRequestDto serializado con AppJson
  -> PendingInvoiceDao.insert
  -> transacción local PENDING
  -> SyncScheduler.enqueuePendingInvoices
  -> PendingInvoiceSyncWorker.doWork
  -> PendingInvoiceDao.markSending
  -> SalesRepositoryImpl.processSale
  -> markSent o markFailed + WorkManager Result.retry
```

Puntos de entrada: `SyncScheduler.kt:49` y `PendingInvoiceSyncWorker.kt:19`.

Riesgo a caracterizar: idempotencia ante caída después de que backend confirma pero antes de
`markSent`, reintentos ilimitados, recuperación de estado `SENDING` y cambio de país/empresa.

## 4. Pasarela HKA / RapidPay

```text
PaymentViewModel.processGatewayPaymentsIfNeeded
  -> TheFactoryRapidPayClient.buildGatewayIntent
  -> PaymentViewModel.gatewayIntentEvent
  -> PaymentScreen inicia aplicación HKA externa
  -> MainActivity.onNewIntent
  -> TheFactoryRapidPayClient.parseResultIntent
  -> RapidPayBridge.deliverResult
  -> coroutine suspendida continúa o cancela la venta
```

Punto central actual: `PaymentViewModel.kt:938`.

Riesgo a caracterizar: timeout, recreación de Activity, resultado duplicado, rechazo de pasarela,
montos multi-moneda y no generación de factura cuando el cobro falla.

## 5. Impresión de factura

```text
PaymentViewModel.printReceiptIfConfigured
  -> PrinterFactory selecciona tipo activo
  -> SUNMI: PanamaInvoiceTicketFormatter -> TicketPrinter.printTicket
  -> HKA fiscal: TheFactoryPrinterImpl.printReceipt
  -> número fiscal
  -> confirmación en backend cuando corresponde
```

Puntos de entrada: `PaymentViewModel.kt:791` y `TheFactoryPrinterImpl.kt:36`.

Riesgo a caracterizar: una venta ya confirmada no debe duplicarse si la impresión falla; comandos,
orden, impuestos, QR, número fiscal y fallback de cierre deben conservarse byte a byte donde aplique.

## 6. Caja

```text
DashboardViewModel.fetchAvailableCajas
  -> CajaRepository
  -> CajaRepositoryImpl / CajaApiImpl
  -> selección y apertura de caja
  -> LocalStore activeCaja

CierreCajaViewModel
  -> resumen y pendientes
  -> reportes X/Z e impresión
  -> CajaRepository.closeCaja
  -> limpieza de caja activa
```

Puntos de entrada: `DashboardViewModel.kt:128`, `CajaRepositoryImpl.kt:85` y
`CajaRepositoryImpl.kt:100`.

Riesgo a caracterizar: caja abierta restaurada por fecha/empresa, secuencia fiscal, pendientes
offline, diferencia de efectivo y orden entre reporte fiscal y cierre remoto.

## 7. Notas de crédito

```text
CreditNotesViewModel.submitCreditNote
  -> validación de caja/secuencia
  -> CreditNoteRepository.createCreditNote
  -> processFiscalIfNeeded
  -> PrinterFactory / TheFactoryPrinterImpl.printCreditNote
  -> confirmación fiscal en backend
  -> refresco y detalle final
```

Puntos de entrada: `CreditNotesViewModel.kt:165`, `CreditNotesViewModel.kt:232` y
`TheFactoryPrinterImpl.kt:90`.

Riesgo a caracterizar: devolución total, reintegro/abono, devolución de stock, reimpresión,
confirmación fiscal parcial y fallo después de crear la nota en backend.

## 8. Sincronización de catálogos

```text
selección de empresa / acción manual / trabajo periódico
  -> SyncScheduler
  -> CatalogSyncWorker.doWork
  -> CatalogSyncer.syncAll
  -> API de catálogos
  -> DAOs Room de clientes, sucursales, productos, direcciones, tipos y promociones
```

Punto de entrada: `CatalogSyncWorker.kt:16`.

Riesgo a caracterizar: actualización atómica, datos parciales, cambio de empresa, retry y uso de
caché existente cuando la aplicación se cierra durante la sincronización.

## Cobertura actual de estos flujos

Solo el formato de ticket Panamá y la política de impresoras tienen pruebas relevantes. Login,
empresa, carrito, pago online/offline, RapidPay, WorkManager, caja, Room migrations, impresión HKA,
notas de crédito y sincronización no tienen pruebas automatizadas de caracterización en Fase 0.
