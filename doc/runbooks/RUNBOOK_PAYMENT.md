# RUNBOOK_PAYMENT — Flujo de pago POS

## Componentes

| Pieza | Archivo |
|---|---|
| Orquestador del flujo | `amaxoniaerp-pos/.../domain/usecase/payment/ExecutePaymentFlowUseCase.kt` |
| Idempotencia de arranque | `StartTransactionUseCase` + `TransactionLogDao` (fila durable por `clientCorrelationId`) |
| Totales/líneas/pagos | `BuildSaleItemsUseCase`, `CalculateSaleTotalsUseCase`, `BuildPaymentDetailsUseCase`, `PrepareSaleUseCase` (aritmética en `Money`/BigDecimal) |
| Gateway externo | `ExecuteGatewayPaymentUseCase` + callbacks durables (`QueueGatewayCallbackUseCase`) |

## Flujo normal

1. `PaymentOperation.execute` mapea el intent → `ExecutePaymentFlowInput`
   (total/tendered/change en Money, tasa Bs solo display).
2. Online: POST `/api/pos/ventas/procesar`. Offline: encola factura con id
   local estable (`QueueOfflineInvoiceUseCase`) y marca transacción PENDING.
3. Backend responde 201; la venta queda PAID con `remoteInvoiceId`.

## Fallas conocidas y manejo

- **Timeout post-commit**: el reintento REUSA el mismo `idFactura` (clave de
  idempotencia); test: `ExecutePaymentFlowAuditTest`.
- **HTTP 409** (factura ya procesada): se reconcilia contra el backend
  (`RECONCILED`) sin duplicar la venta — nunca auto-aprobar si el backend no
  expone datos; tests en `ExecutePaymentFlowAuditTest`.
- **Retry de transporte**: SOLO GET reintenta (502-504/conexión);
  `ApiClient.configureCajaRetry`. El POST de venta jamás se reintenta ciego.
- **Confirmación fiscal fallida**: tuplo fiscal persistido ANTES del replay;
  worker `FiscalConfirmationWorker` con escalera 15s→30s→60s→5m→15m y tope 1h
  (`QueueFiscalConfirmationUseCase.nextAttempt`). Terminal tras MAX_RETRIES.
- **Callback gateway tardío/duplicado**: aterriza en la fila durable aunque el
  proceso haya muerto; segundo `markResolved` es idempotente.

## Diagnóstico

- Estado durable: tabla Room `transaction_log` (`fiscalConfirmationStatus`,
  `gatewayCallbackStatus`, leases).
- Telemetría: `SaleTelemetry` eventos FISCAL_CONFIRMED / FISCAL_FAILED.
- Tests de referencia: `StartTransactionIdempotencyTest`,
  `TransactionLogDaoTest`, `QueueGatewayCallbackUseCaseTest`,
  `SynchronizePendingInvoicesUseCaseTest`.
