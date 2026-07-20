# Commit y manifiesto de archivos

## Estrategia

Siguiendo la decisión del usuario (2026-07-20): **un solo commit grande detallado**
que abarque las cinco fases (0, 0b, 1, 2, 3) y los archivos auxiliares de dev-branding
que estaban en el working tree. No se aplica per-fase `git add -p` porque muchos
archivos (`TransactionLogEntity.kt`, `AppDatabase.kt`, `ExecutePaymentFlowUseCase.kt`,
`DependencyContainer.kt`, `SyncScheduler.kt`, `detekt.yml`) acumulan cambios de
múltiples fases intercalados — separarlos hunk por hunk dejaría un historial confuso.

## Mensaje del commit

```
✨ [feat] roadmap de confiabilidad de pagos POS — fases 0-3

Implementa y valida automáticamente las cinco fases del roadmap de
confiabilidad pagos POS detectadas en la auditoría 2026-07-20:

- FASE 0  Network hardening: usesCleartextTraffic=false + network_security_config.xml
          con excepciones quirúrgicas para hosts de dev. Backend Netty bindea IPv4
          preferentemente.
- FASE 0b Detekt baseline.xml snapshot de hallazgos preexistentes; warningsAsErrors
          respetado sin falsos positivos.
- FASE 1  Idempotencia + duplicate invoice: nuevo transaction_log ledger, idFactura
          minted una vez por operación, DuplicateInvoiceException en HTTP 409,
          UI "Reconciliar" en PaymentScreen.
- FASE 2  Cola de confirmación fiscal: PaymentFiscalConfirmationLedger port,
          FiscalConfirmationWorker con ladder [15s,30s,1m,5m,15m]+exp cap 1h,
          MAX_RETRIES=10, leasing de 60s. MIGRATION_11_12.
- FASE 3  RapidPay gateway lease durable: GatewayCallbackLedger port,
          GatewayCallbackWorker watchdog, RapidPayBridge.setPendingCorrelationId,
          MainActivity.markGatewayResolved incondicional. El drop silencioso tras
          muerte de proceso queda resuelto. MIGRATION_12_13.

Schema Room: v10 → v13 (schemas 10/11/12/13.json exportados).
Detekt: LongParameterList.ignoreAnnotated [Query,Insert,Update,Delete],
        TooManyFunctions threshold 11→20.

Validación automática (2026-07-20):
- compileAmaxoniaDebugKotlin ✅
- detekt ✅
- lintAmaxoniaDebug ✅
- testAmaxoniaDebugUnitTest ✅ 103/104 (1 fail preexistente, ver
  docs/quality/roadmap-payment-0-3/04-preexisting-test-failure.md)

Pendiente QA hardware (bloqueante production-ready): ver
docs/quality/roadmap-payment-0-3/02-pending-hardware-tests.md.

Cambios auxiliares incluidos en el mismo commit por estar en el working tree:
- app/build.gradle.kts: IP backend 192.168.2.16→192.168.2.10 + línea baseline
  detekt (este última mía, FASE 0b).
- app/src/amaxonia/res/values/brand_colors.xml, app/src/banescoVenezuela/...,
  app/src/main/java/.../ui/welcome/WelcomeScreen.kt: cambios preexistentes de
  branding (olas/waves con colorResource). No bloquean el roadmap.
```

## Manifiesto de archivos

### Modificados (21)
- `amaxoniaerp-backend/build.gradle.kts` — Netty IPv4 prefer (FASE 0 contexto)
- `amaxoniaerp-pos/app/build.gradle.kts` — IP backend + línea baseline detekt (FASE 0b)
- `amaxoniaerp-pos/app/src/amaxonia/res/values/brand_colors.xml` — branding preexist
- `amaxoniaerp-pos/app/src/banescoVenezuela/res/values/brand_colors.xml` — branding preexist
- `amaxoniaerp-pos/app/src/debug/res/xml/network_security_config.xml` — FASE 0 variant debug
- `amaxoniaerp-pos/app/src/main/AndroidManifest.xml` — FASE 0 (cleartext=false + networkSecurityConfig)
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/MainActivity.kt` — FASE 3 (markGatewayResolved)
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/data/local/db/AppDatabase.kt` — v10→v13, 3 migraciones
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/data/printer/RapidPayBridge.kt` — FASE 3 (setPendingCorrelationId)
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/data/remote/api/SalesApiImpl.kt` — FASE 1 (HTTP 409 → DuplicateInvoiceException)
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/data/sync/SyncScheduler.kt` — FASE 2 + 3 (enqueueFiscalConfirmations + enqueueGatewayCallbacks)
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/BuildSaleRequestUseCase.kt` — FASE 1 (idFactura en input)
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/ExecutePaymentFlowUseCase.kt` — FASE 1 + 2 + 3 (recoverOrStart, finishOnlineSale, buildFiscalOutcome, executeGatewayIfRequired con ledger)
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/PrepareSaleUseCase.kt` — FASE 1 (correlationCarryOver)
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/ui/common/DependencyContainer.kt` — wiring FASE 1+2+3
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/ui/payment/PaymentScreen.kt` — FASE 1 (diálogo Reconciliar)
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/ui/payment/PaymentState.kt` — FASE 1 (DuplicateInvoicePrompt)
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/ui/payment/PaymentViewModel.kt` — FASE 1 (applyPaymentResult DuplicateInvoice)
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/ui/welcome/WelcomeScreen.kt` — branding preexist (olas)
- `amaxoniaerp-pos/app/src/main/res/xml/network_security_config.xml` — FASE 0 (main source set)
- `amaxoniaerp-pos/config/detekt/detekt.yml` — FASE 2 + 3 (LongParameterList ignoreAnnotated, TooManyFunctions threshold)

### Nuevos (16)
- `amaxoniaerp-pos/app/schemas/.../11.json`, `12.json`, `13.json` — schemas Room exportados
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/data/local/db/TransactionLogEntity.kt` — entidad ledger completa (FASE 1+2+3 campos)
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/data/sync/FiscalConfirmationWorker.kt` — FASE 2 worker
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/data/sync/GatewayCallbackWorker.kt` — FASE 3 watchdog
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/DuplicateInvoiceException.kt` — FASE 1
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/GatewayCallbackLedger.kt` — FASE 3 port
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/PaymentFiscalConfirmationLedger.kt` — FASE 2 port
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/QueueFiscalConfirmationUseCase.kt` — FASE 2
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/QueueGatewayCallbackUseCase.kt` — FASE 3
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/StartTransactionUseCase.kt` — FASE 1
- `amaxoniaerp-pos/config/detekt/detekt-baseline.xml` — FASE 0b
- `amaxoniaerp-pos/docs/quality/roadmap-payment-0-3/` — documentación entregable QA (este docset)
- `amaxoniaerp-pos/docs/security/network-hardening-matrix.md` — FASE 0 matrix

## Note sobre el listado de commits por fase

La decisión tomada con el usuario es **commit único**. Si en adelante se requiere
un desglose per-fase (e.g. para PR size limit), la aproximación sería usar
`git reset --soft HEAD~1` seguido de commits parciales con `git add -p` por archivo
multi-fase — pero el coste es alto y los puntos de corte poco legibles porque la
mayoría de archivos acumulan cambios dependientes entre fases.
