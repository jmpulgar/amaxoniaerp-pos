# Fases 0–3 — Resumen de objetivos y soluciones

## FASE 0 — Network hardening

**Problema**: `AndroidManifest.xml` declaraba `android:usesCleartextTraffic="true"` global, permitiendo tráfico HTTP en cualquier host.

**Solución**:
- `app/src/main/AndroidManifest.xml`: cambiado a `android:usesCleartextTraffic="false"` + `android:networkSecurityConfig="@xml/network_security_config"`.
- `app/src/main/res/xml/network_security_config.xml`: `base-config cleartextTrafficPermitted="false"` (postura segura) + `domain-config cleartextTrafficPermitted="true"` con excepciones quirúrgicas para hosts de dev (`192.168.2.10`, `10.0.2.2`, `localhost`, `127.0.0.1`).
- `app/src/debug/res/xml/network_security_config.xml`: variante debug equivalente.

## FASE 0b — Detekt baseline

**Problema**: CI rojo por 296 hallazgos de Detekt, `warningsAsErrors=true` bloquea el flujo.

**Solución**:
- `config/detekt/detekt-baseline.xml`: snapshot de hallazgos preexistentes. Detekt ahora solo falla en hallazgos NUEVOS.

## FASE 1 — Idempotencia + duplicate invoice

**Problema**: `BuildSaleRequestUseCase` no seteaba `idFactura`. Reintento del usuario tras timeout generaba **factura duplicada** en el backend.

**Solución**:
- New `StartTransactionUseCase.kt`: `recoverOrStart(command)` mints un UUID `clientCorrelationId` por operación o resume el mismo si llega `correlationCarryOver`.
- New `TransactionLogEntity.kt`: entidad Room con el `clientCorrelationId` como PK, estado (SENDING/CONFIRMED/FAILED/DUPLICATE), y `idCaja`/`idCajaSecuencia` para auditoría.
- New `DuplicateInvoiceException.kt`: propagado desde `SalesApiImpl.processSale` al recibir HTTP 409 (detectado vía `response.status.value == HTTP_CONFLICT`).
- `BuildSaleRequestUseCase.kt`: `BuildSaleRequestInput` extiende con `idFactura: String?`.
- `PrepareSaleUseCase.kt`: `PreparedSale` extiende con `correlationCarryOver` + `withCorrelationId(clientCorrelationId)`.
- `ExecutePaymentFlowUseCase.kt`: `processPreparedSale` recupera o mints el correlationId, lo stamp en el payload, mapea el resultado deDuplicate a `PaymentFlowResult.DuplicateInvoice`.
- UI (`PaymentScreen.kt`, `PaymentState.kt`, `PaymentViewModel.kt`): diálogo "Reconciliar" con secondary explanation; nunca anular automáticamente.
- `DependencyContainer.kt`: wiring nuevo `transactionLogDao`, `startTransactionUseCase`.

`MIGRATION_10_11`: `CREATE TABLE transaction_log` (12 columnas iniciales).

## FASE 2 — Cola de confirmación fiscal

**Problema**: PATCH `/facturas/{id}/confirmacion-fiscal` fallado traga el error silenciosamente → factura emitida pero `fiscalNumber` nunca reportado al backend.

**Solución**:
- 6 nuevas columnas en `transaction_log`: `fiscalNumber`, `printerSerial`, `fiscalConfirmationStatus` (default `'PENDING'`), `fiscalConfirmationRetryCount` (default `0`), `fiscalConfirmationNextAttemptAt` (default `0`), `fiscalConfirmationLeasedUntil` (default `0`).
- New `QueueFiscalConfirmationUseCase.kt`: backoff ladder `[15s, 30s, 1m, 5m, 15m]` + exponencial capado a `1h`, `MAX_RETRIES=10`, `LEASE_DURATION_MS=60s`, `OVERSHOOT_BITSHIFT_CAP=10`.
- New `PaymentFiscalConfirmationLedger.kt`: `fun interface` port + `sealed interface FiscalConfirmationOutcome { Confirmed, Retryable }`.
- New `FiscalConfirmationWorker.kt`: itera `findFiscalConfirmable(now, batchSize=25)`, leases por entrada, PATCH, triaje success/`RETRYABLE_PENDING`/`TERMINAL_FAILED`. Reintenta el worker entero si alguna fila fue retryable.
- `SyncScheduler.kt`: `FISCAL_CONFIRMATION_WORK_NAME`, `enqueueFiscalConfirmations(context)`, `fiscalConfirmationRequest(constraints)` con `BackoffPolicy.EXPONENTIAL`.
- `ExecutePaymentFlowUseCase.kt`: `finishOnlineSale` retorna `Pair<Success, FiscalConfirmationOutcome?>`; el caller hace `outcome?.let { fiscalConfirmationLedger?.recordOutcome(it) }`.

`MIGRATION_11_12`: 6 `ALTER TABLE transaction_log ADD COLUMN`.

## FASE 3 — RapidPay gateway lease durable

**Problema**: `RapidPayBridge.pendingResult` es `@Volatile CompletableDeferred`. Si el proceso moría esperando callback HKA, `MainActivity.handleRapidPayResult` recibía el Intent pero `hasPendingRequest()` era false → **drop silencioso** → venta perdida o duplicada.

**Solución**:
- 5 nuevas columnas en `transaction_log`: `gatewayCallbackStatus` (default `'IGNORED'`), `gatewayCallbackRetryCount`, `gatewayCallbackNextAttemptAt`, `gatewayCallbackLeasedUntil`, `gatewayRawResponse` (opcional).
- New `QueueGatewayCallbackUseCase.kt`: backoff corto `[30s, 1m, 2m, 5m]` + exp capado a 10 min, `MAX_RETRIES=4`, `LEASE_DURATION_MS=90s`.
- New `GatewayCallbackLedger.kt`: `fun interface` port + `sealed interface GatewayCallbackOutcome { Awaiting, Resolved }`.
- New `GatewayCallbackWorker.kt`: watchdog que escalona filas `AWAITING`/`RETRYABLE_AWAITING` con lease expirado a `RETRYABLE_AWAITING` (siguiente ciclo) o `TERMINAL_AWAITING` (notificar cajero). Re-lee tras el lease para skippear si MainActivity resolvió concurrentemente.
- `SyncScheduler.kt`: `GATEWAY_CALLBACK_WORK_NAME`, `enqueueGatewayCallbacks(context)`, `gatewayCallbackRequest()` **sin constraint de red** (el watchdog solo inspecciona filas locales).
- `RapidPayBridge.kt`: `setPendingCorrelationId`/`pendingCorrelationId()` para enlazar callback Intent con la fila transaccional.
- `MainActivity.kt`: `handleRapidPayResult` ahora persiste `markResolved(correlationId, responseCode)` **incondicionalmente** cuando llega callback — incluso en el path "no pending request" que antes se drop-eaba.
- `ExecutePaymentFlowUseCase.kt`: `executeGatewayIfRequired` persiste `AWAITING` antes del launch, marca `RESOLVED` si el gateway falla localmente antes del Intent.

`MIGRATION_12_13`: 5 `ALTER TABLE transaction_log ADD COLUMN`.

## Sin datos sensibles persistidos

FASE 3 no persiste ni el comando cifrado HKA, ni PAN, ni track data. Solo `gatewayCallbackStatus`, contadores, y el short `codeRapidPay` del Intent (típicamente `"00"` para approved).
