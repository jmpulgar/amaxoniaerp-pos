# RUNBOOK_OFFLINE_SYNC — Cola offline POS→backend

## Componentes

| Pieza | Archivo |
|---|---|
| Encolado | `domain/usecase/payment/QueueOfflineInvoiceUseCase.kt` |
| Sincronizador | `domain/usecase/sync/SynchronizePendingInvoicesUseCase.kt` |
| DAO durable | `data/local/db/TransactionLogEntity.kt` (leases, estados, tenant) |
| Confirmación fiscal diferida | `data/sync/FiscalConfirmationWorker.kt` (WorkManager) |

## Semántica garantizada

1. **Clave de idempotencia estable**: el id local (`clientCorrelationId`) se
   persiste ANTES de salir de la pantalla y viaja como `idFactura`;
   reenvíos no duplican la venta (409 del backend → reconciliación).
2. **Leases**: `leasedUntil = now + LEASE_DURATION_MS`; un worker concurrente
   salta la fila; lease vencida vuelve a elegirse. Tests:
   `SynchronizePendingInvoicesUseCaseTest` (lease interrumpida se recupera,
   ya-sincronizadas no se reenvían, fallo de red es recuperable).
3. **Estados**: PENDING → (envío) → PAID/SYNCED; fallos fiscales por
   `fiscalConfirmationStatus`: RETRYABLE_PENDING → CONFIRMED /
   TERMINAL_FAILED con backoff acotado a 1h.
4. **Multi-tenant**: las colas filtran por `tenantId`
   (`findFiscalConfirmableForTenant`, `findGatewayReconcilableForTenant`).

## Operación

- Reconexión: `NetworkMonitor` dispara reintentos vía WorkManager; el worker
  devuelve `Result.retry()` si alguna fila quedó RETRYABLE.
- Diagnóstico: inspeccionar Room `transaction_log` (status, nextAttemptAt,
  leasedUntil, lastError, remoteInvoiceId).
- Si una fila queda TERMINAL_FAILED: verificar causa en `lastError`,
  corregir (impresora/red/backend) y reencolar desde la pantalla de historial
  de la transacción — no editar la DB a mano en producción.

## Invariantes (tests)

- `StartTransactionIdempotencyTest` — retry reusa fila SENDING y correlationId.
- `TransactionLogDaoTest` — CAS `tryClaimFiscal`/`transitionFiscalState`,
  backoff, reconciliación gateway respetando tenant/lease.
- `QueueFiscalConfirmationUseCaseTest` — tuplo persistido pre-replay,
  confirmación duplicada jamás re-elige la fila.
