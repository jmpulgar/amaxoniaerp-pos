# QA Hardware — Matriz de evidencias pendientes

Estado: **bloqueante para production-ready**. Las unidades de QA/equipo de campo
deben ejecutar estos escenarios en device físico y adjuntar evidencias (screenshot,
logcat o grabación) a este documento.

Device sugerido: POS físico con HKA Rapid Pay instalado y configurado (modo `HKA20`,
`PrinterType.THE_FACTORY_HKA`).

Pre-requisitos previos:
- Caja abierta y sesión de compañía activa (Venezuela).
- Impresora HKA conectada y test de fiscal print OK.
- `apiConfigManager` apuntando al backend correcto (dev o staging).

---

## Escenario QA-1 — Confirmación fiscal reaches `TERMINAL_FAILED` (FASE 2)

**Objetivo**: verificar que tras agotar los `MAX_RETRIES=10` reintentos, la fila
termina con `fiscalConfirmationStatus = 'TERMINAL_FAILED'` y `lastError` poblado.

### Setup

1. Apagar la impresora HKA / desconectar de red.
2. Realizar una venta online completa (HKA no invoilcrate, payment flow llega a
   finalización con `fiscalNumber` vacío).

> Nota: si la impresora está apagada no se generará `fiscalNumber`, así que
> para forzar `RETRYABLE_PENDING` real puede requerirse un escenario alternativo:
> bloquear la URL `/facturas/{id}/confirmacion-fiscal` en el backend (503) o
> ejecutar con el backend caído tras el POST.

### Pasos

1. Realizar una venta que complete el POST pero falle el PATCH confirmación-fiscal.
2. Observar logcat filtro `FiscalConfirmationWorker`.
3. Esperar ~75 min (suma de `BACKOFF_LADDER_MS = 15s+30s+1m+5m+15m = ~21.5min` más
   los reintentos exponenciales siguientes hasta `MAX_RETRIES=10`).

### Resultado esperado

Logcat con la secuencia:
```
[FiscalConfirmationWorker] Fiscal confirmation retryable for <id>: ... (next in 15000ms)
[FiscalConfirmationWorker] Fiscal confirmation retryable for <id>: ... (next in 30000ms)
...  (10 ciclos total)
[FiscalConfirmationWorker] Fiscal confirmation terminal failure for <id>: ...
```

DB (vía adb shell + sqlite o App Inspector):
```sql
SELECT clientCorrelationId, fiscalConfirmationStatus, fiscalConfirmationRetryCount, lastError
FROM transaction_log
WHERE fiscalConfirmationStatus = 'TERMINAL_FAILED';
-- fiscalConfirmationRetryCount == 10
-- lastError contiene el mensaje legible
```

### Evidencia a adjuntar

- [ ] Screenshot del logcat con la secuencia de 10 reintentos.
- [ ] Screenshot del App Inspector / sqlite mostrando la fila `TERMINAL_FAILED`.
- [ ] Confirmación de que el worker deja de reintentar (no más logs tras el terminal).

---

## Escenario QA-2a — RapidPay callback path vivo (FASE 3)

**Objetivo**: verificar el happy path — HKA retorna, el Intent es procesado, la fila
pasa a `gatewayCallbackStatus = 'RESOLVED'`.

### Pasos

1. Iniciar una venta con método de pago Tarjeta (Punto de Venta) en VE.
2. HKA abre, se procesa el pago del cliente, se aprueba.
3. HKA retorna el Intent → `MainActivity.onNewIntent`.
4. Observar el flujo normal: `RapidPayBridge.deliverResult` → venta completa.

### Resultado esperado

Logcat:
```
[MainActivity] Rapid Pay result received from onNewIntent
[RapidPayBridge] Payment gateway result delivered; approved=true
```

DB:
```sql
SELECT clientCorrelationId, gatewayCallbackStatus, gatewayRawResponse
FROM transaction_log
ORDER BY createdAt DESC LIMIT 1;
-- gatewayCallbackStatus == 'RESOLVED'
-- gatewayRawResponse == '00' (o el código que HKA retornó)
```

### Evidencia a adjuntar

- [ ] Screenshot del pago aprobado en pantalla.
- [ ] Screenshot logcat con `delivered; approved=true`.
- [ ] Screenshot DB con `gatewayCallbackStatus = 'RESOLVED'`.

---

## Escenario QA-2b — RapidPay callback tras muerte de proceso (FASE 3) ⚠️ CRÍTICO

**Objetivo**: verificar el fix core de FASE 3 — si el proceso muere esperando el
callback, la llegada del Intent ya no se drop-ea silenciosamente.

### Pasos

1. Iniciar una venta con Tarjeta en VE (disparar HKA).
2. **Mientras HKA está esperando la tarjeta**, forzar la muerte del proceso POS:
   - Opción A: `adb shell am force-stop com.amaxonia.pos`
   - Opción B: en device, recientes → swipe-up para cerrar la app.
   - Opción C:_limit de memoria del sistema killing el proceso en background.
3. Aprobar el pago en HKA normalmente (HKA no sabe que el proceso murió).
4. HKA retorna el Intent → `MainActivity.onCreate` (cold start) o `onNewIntent`.
5. Observar el logcat del relanzamiento.

### Resultado esperado (ANTES de FASE 3, el bug)

```
[MainActivity] Rapid Pay result received from onCreate
[MainActivity] Rapid Pay result ignored because no request is pending
```

→ Fila `transaction_log` queda `gatewayCallbackStatus = 'AWAITING'` por siempre,
venta se reporta como no realizada, HKA sí cobró → **duplicación segura** si el
cajero reintenta.

### Resultado esperado (DESPUÉS de FASE 3)

```
[MainActivity] Rapid Pay result received from onCreate
[MainActivity] Rapid Pay result ignored because no request is pending
```
(Pero el siguiente log ya no es el fin — FASE 3 persiste el resolved:)
- `markGatewayResolved(correlationId, responseCode)` se ejecuta vía `runBlocking`.
- Fila `transaction_log` pasa a `gatewayCallbackStatus = 'RESOLVED'` con el
  `gatewayRawResponse` poblado aunque el bridge estuviera vacío.

DB:
```sql
SELECT clientCorrelationId, gatewayCallbackStatus, gatewayRawResponse
FROM transaction_log
ORDER BY createdAt DESC LIMIT 1;
-- gatewayCallbackStatus == 'RESOLVED' (no AWAITING forever)
-- gatewayRawResponse contains the HKA result code
```

### Comportamiento UI post-fix

El cajero abre la app, va al histórico, y debería ver un banner/prompt indicando
"Reconciliación pendiente: callback Rapid Pay recibido pero venta no completada".
Esto depende del wiring UI que FASE 3 **deja hooks** (la fila está marcada) pero
**no** implementa el prompt (sería FASE 4). Para QA: validar que al menos la DB
refleja `RESOLVED` y NO hay duplicación si el cajero reintenta.

### Evidencia a adjuntar

- [ ] Grabación de pantalla mostrando force-stop + reapertura via callback HKA.
- [ ] Screenshot logcat con `result ignored because no request is pending` (válido)
      seguido por la actualización DB `RESOLVED`.
- [ ] Screenshot DB mostrando `gatewayCallbackStatus = 'RESOLVED'`.
- [ ] Confirmación de que reintentar la venta NO genera duplicación (backend
      rechaza con HTTP 409 por `idFactura` collision — feature de FASE 1).

---

## Riesgo conocido: `runBlocking` en `MainActivity.markGatewayResolved`

FASE 3 introdujo un `runBlocking` en el main thread para persistir el resolved.
La lambda solo hace un `UPDATE` local (Room lo ejecuta en IO dispatcher), pero en
el peor caso podría blockear brevemente el UI. Si QA observa jank visible tras
recibir el callback, anotarlo — considerarse para refinar en FASE 4.
