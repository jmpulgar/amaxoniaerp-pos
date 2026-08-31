# 03: Disparador reactivo de sincronización y encolado en WorkManager

**What to build:**
Garantizar que toda factura guardada offline se encole inmediatamente en WorkManager con restricción de red (NetworkType.CONNECTED). Corregir la detección de conectividad en el arranque de la app para que, si el dispositivo se conecta o se abre ya con internet, las facturas pendientes se reenvíen automáticamente al backend (donde se emitirán ante el PAC DGI/The Factory HKA) y se actualicen los registros locales como sincronizados.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] QueueOfflineInvoiceUseCase o processOffline encola SyncScheduler.enqueuePendingInvoices(context) al persistir una factura offline.
- [ ] AppNavigation.kt encola la sincronización de pendientes al iniciar la app si ya cuenta con conexión activa (sin descartar el estado inicial).
- [ ] SynchronizePendingInvoicesUseCase y PendingInvoiceSyncWorker actualizan tanto la tabla de pendientes como el registro de transacciones local al confirmar el envío.
- [ ] Pruebas unitarias e instrumentadas verificando el encolado y la ejecución del worker.
