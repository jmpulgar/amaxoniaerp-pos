# PLAN — Deepening del Payment Operation Module (Android POS)

> **Proyecto:** `jmpulgar/amaxoniaerp-pos`  
> **Subproyecto a modificar:** `amaxoniaerp-pos/`  
> **Tipo de trabajo:** **100% refactor arquitectónico**  
> **Cambio funcional permitido:** **NINGUNO**  
> **Baseline auditado:** `main` @ `8c69209764aa239587f5affb0c776fe984301c6c`  
> **Fuente arquitectónica:** enfoque `improve-codebase-architecture` / `codebase-design` de Matt Pocock  
> **Candidato elegido:** `Android payment operation pipeline`  
> **Objetivo:** convertir el pipeline de pago en un **deep module** con una **interface** pequeña y estable, manteniendo exactamente el comportamiento actual.

---

# 0. CONTRATO DE EJECUCIÓN PARA EL AGENTE

Este documento es la fuente de verdad del refactor.

El agente implementador debe:

- [x] Leer **este archivo completo** antes de modificar código.
- [x] Leer `amaxoniaerp-pos/AGENTS.md`.
- [x] Trabajar únicamente sobre `amaxoniaerp-pos/`.
- [x] Ejecutar las TASK en orden.
- [x] No rediseñar este plan.
- [x] No ampliar el scope.
- [x] No hacer refactors oportunistas.
- [x] No cambiar comportamiento funcional.
- [x] No modificar backend.
- [x] No modificar `.env`, `.env.development`, secretos, URLs de producción ni credenciales.
- [x] No modificar schema Room salvo que este plan lo indique expresamente. **Este plan no requiere migraciones.**
- [x] No modificar contratos HTTP/JSON.
- [x] No modificar reglas monetarias.
- [x] No modificar lógica fiscal.
- [x] No modificar la selección HKA-20 vs facturación digital.
- [x] No introducir nuevos `@Suppress`, `@file:Suppress`, baseline entries, ignores ni desactivar reglas de Detekt.
- [x] No usar una nueva abstracción sólo para satisfacer `LongParameterList`.
- [x] No crear una nueva `interface` si sólo existe una implementación real y no hay una seam útil.
- [x] Mantener los nombres de dominio existentes cuando sean correctos.
- [x] Modificar este Markdown **sólo para marcar los checks completados y registrar evidencia mínima de validación**.
- [x] Si una TASK descubre que una decisión de este plan contradice el código actual de forma material, detener esa TASK, documentar el `BLOCKER`, y no improvisar una arquitectura distinta.
- [x] No hacer push a `main` hasta completar todas las TASK y la validación final.
- [x] Si `main` avanzó desde el baseline, **no resetear, no revertir y no sobrescribir trabajo ajeno**. Integrar el refactor sobre el HEAD actual preservando cambios posteriores.

## Regla de oro

> Si una modificación puede alterar una venta, un monto, una forma de pago, la persistencia, la idempotencia, el flujo offline, el gateway, la impresión o la fiscalización, **no es un refactor puro** y debe rechazarse.

---

# 1. VOCABULARIO ARQUITECTÓNICO OBLIGATORIO

Usar estos términos en código/documentación del refactor cuando corresponda:

- **module:** agrupación con una interface y una implementation.
- **interface:** todo lo que un caller debe conocer para usar correctamente el module.
- **implementation:** lógica interna del module.
- **depth:** capacidad expuesta por unidad de interface.
- **deep module:** mucha conducta detrás de una interface pequeña.
- **shallow module:** interface casi tan compleja como su implementation.
- **seam:** lugar donde puede variar el comportamiento sin editar al caller.
- **adapter:** implementación concreta que satisface una interface en una seam.
- **leverage:** capacidad reutilizada por callers a través de una interface pequeña.
- **locality:** reglas, bugs y cambios concentrados en un lugar.

No inventar arquitectura basada en “más clases = mejor diseño”.

## Principios obligatorios

1. **La interface es el test surface.**
2. **Deletion test:** un module útil concentra complejidad; no debe ser un pass-through.
3. **One adapter = hypothetical seam; two adapters = real seam.**
4. **Replace, don’t layer:** cualquier estructura transitoria creada para migrar debe desaparecer al finalizar si sólo duplica la anterior.
5. **Internal seams are allowed:** los detalles internos pueden seguir divididos en clases/use cases sin formar parte de la interface pública.
6. **Pure functions útiles no deben envolverse sin necesidad.**

---

# 2. OBJETIVO DEL REFACTOR

El caller principal del pago —especialmente `PaymentViewModel`— no debe conocer la mecánica interna completa del pipeline.

## Estado conceptual actual

```text
PaymentViewModel
    |
    | construye/propaga gran cantidad de datos técnicos
    v
ExecutePaymentFlowInput
    |
    v
ExecutePaymentFlowUseCase
    |
    +--> PrepareSaleUseCase
    |      +--> ValidatePaymentUseCase
    |      +--> CalculateSaleTotalsUseCase
    |      +--> BuildSaleItemsUseCase
    |      +--> BuildSaleRequestUseCase
    |
    +--> StartTransactionUseCase
    +--> GatewayCallbackLedger
    +--> ExecuteGatewayPaymentUseCase
    |
    +--> CompletePaymentSaleUseCase
           +--> SalesRepository
           +--> QueueOfflineInvoiceUseCase
           +--> PrintInvoiceUseCase
           +--> ConfirmFiscalDocumentUseCase
           +--> PaymentFiscalConfirmationLedger
           +--> reconciliation
```

El comportamiento es valioso y debe conservarse.  
La fricción es que demasiado conocimiento interno llega hasta el caller, tests y composition root.

## Estado conceptual objetivo

```text
PaymentViewModel
    |
    | intención de pago
    v
+--------------------------------------+
|         PAYMENT OPERATION MODULE     |
|                                      |
|  external seam                       |
|  -------------------------------     |
|  execute(payment request)            |
|                                      |
|  implementation interna              |
|  -------------------------------     |
|  preparar venta                      |
|  validar                              |
|  resolver caja/cliente/sesión        |
|  calcular                             |
|  idempotencia                        |
|  gateway                              |
|  venta online/offline                |
|  persistencia local                  |
|  impresión                           |
|  fiscal                              |
|  retries/reconciliation              |
+------------------+-------------------+
                   |
                   v
          Payment operation result
```

## Resultado arquitectónico esperado

Al terminar:

1. `PaymentViewModel` consume **una única seam de ejecución de pago**.
2. El caller no conoce `PaymentFlowRepositories`, `PaymentStateRepositories`, `PaymentRuntimeServices`, `PaymentPreparationOperations`, `PaymentExecutionOperations`, `PreparedSale`, `SalePreparation` ni los detalles del ledger.
3. La orchestration de gateway, persistencia, impresión, fiscal y reconciliación sigue internamente igual.
4. La composición completa del payment module ocurre en un solo lugar.
5. Los tests de comportamiento prueban el pago principalmente a través de la nueva interface.
6. Los tests especializados pueden seguir probando internal seams cuando realmente agregan valor.
7. No queda una facade nueva encima de `ExecutePaymentFlowUseCase` mientras el caller siga usando ambas. Al final existe **una sola interface externa canónica**.

---

# 3. SCOPE

## Dentro del scope

Principalmente:

```text
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/ui/payment/
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/ui/common/DependencyContainer.kt
amaxoniaerp-pos/app/src/test/java/com/amaxonia/pos/ui/payment/
amaxoniaerp-pos/app/src/test/java/com/amaxonia/pos/domain/usecase/payment/
```

Dependencias directas sólo si son imprescindibles:

```text
domain/repository/*
domain/model/payment/*
domain/model/money/*
domain/model/sales/*
domain/model/tenant/*
data/local/db/TransactionLog*
data/sync/*Fiscal*
data/sync/*Gateway*
data/printer/*
MainActivity.kt
```

Estas dependencias se leen primero.  
Se modifican únicamente si una TASK lo autoriza explícitamente.

## Fuera del scope

**NO modificar:**

```text
amaxoniaerp-backend/**
doc/PLAN_TODO_POS.md
.env
.env.development
local.properties
configuración de producción
schemas Room
migraciones Room
endpoints
DTOs HTTP del backend
tablas SQL
PAC payloads
lógica de notas de crédito
UI visual
navegación
mesas fuera de la integración estricta con pago
caja fuera de la integración estricta con pago
sincronización de catálogos
```

---

# 4. COMPORTAMIENTO CONGELADO — NO PUEDE CAMBIAR

Estas son invariantes del refactor.

## 4.1 Venta online

- Debe seguir enviándose una sola venta por operación.
- Debe conservarse `idFactura`/correlation id.
- Debe conservarse el tratamiento HTTP 409/duplicado.
- Debe conservarse la reconciliación del duplicado.
- Debe conservarse el `TransactionStatus.PAID` local al completar.
- No se cambia el orden observable de los pasos.

## 4.2 Venta offline

- Debe conservarse la cola offline actual.
- Debe persistirse el pendiente exactamente con la misma semántica.
- No se introduce una segunda cola.
- No se cambia WorkManager.
- No se cambia la estrategia de reenvío.

## 4.3 Idempotencia

- Una operación no puede generar un segundo `idFactura` por un simple retry.
- `correlationCarryOver` conserva su semántica actual.
- `preferredCorrelationId` conserva su semántica actual.
- `StartTransactionUseCase` conserva los estados:
  - `SENDING`
  - `CONFIRMED`
  - `FAILED`
  - `DUPLICATE`
- No cambiar cuándo una fila se considera resumible.

## 4.4 Crédito / contado

- `PaymentCondition.CONTADO` conserva el wire value actual.
- `PaymentCondition.CREDITO` conserva el wire value actual.
- CXC conserva su semántica.
- Tarjeta de crédito no debe convertirse accidentalmente en CxC.
- Pago parcial + CXC debe conservar exactamente el saldo pendiente actual.
- No recalcular reglas de crédito.

## 4.5 Cálculos

- `Money` sigue siendo fuente para lógica monetaria que ya usa `Money`.
- No introducir comparaciones financieras críticas nuevas con `Double`.
- No modificar rounding.
- No modificar escala.
- No modificar total/subtotal/IVA/descuentos.
- Un `SaleFinancialSnapshot` autoritativo sigue siendo autoritativo.
- No recalcular un snapshot precargado si hoy no se recalcula.

## 4.6 Panamá

- El flujo PA permanece idéntico.
- `useHka20` sigue sin afectar PA.
- No cambiar PAC.
- No cambiar CUFE/QR.
- No cambiar impresión.
- No tocar payloads fiscales.

## 4.7 Venezuela — HKA-20

- `PrinterType.THE_FACTORY_HKA` conserva la selección actual.
- El POS sigue enviando `useHka20=true` cuando corresponde.
- El backend no se modifica.
- No introducir fallback HKA-20 -> digital.
- No introducir fallback digital -> HKA-20.
- No cambiar `RapidPayBridge`.
- No cambiar contrato de Intent.
- No cambiar confirmación fiscal.
- No cambiar callback reconciliation.

## 4.8 Venezuela — digital

- La selección digital permanece igual.
- No modificar PAC.
- No modificar numeración.
- No modificar campos fiscales.
- No modificar retry/resultado incierto.

## 4.9 Gateway

- La validación de configuración ocurre antes de mutar el estado de la venta, como hoy.
- Un rechazo de gateway impide enviar la venta.
- `GatewayCallbackLedger` mantiene su semántica.
- No cambiar leases/backoff/estados.

## 4.10 Confirmación fiscal

- Un fallo de confirmación posterior a una venta válida no duplica ni revierte la venta.
- Se conserva el enqueue de retry.
- `PaymentFiscalConfirmationLedger` conserva sus resultados.
- No cambiar backoff/lease/status.

## 4.11 Mesa

- `CuentaMesaVentaDto` conserva su contrato y comportamiento.
- Una cuenta de mesa no puede cambiar su requisito de conectividad.
- No cambiar cierre de sesión de mesa.
- No cambiar correlación de la operación de mesa.

## 4.12 Draft / preloaded sale

- `saleItemsOverride` conserva semántica durante la transición.
- `financialSnapshotOverride` conserva semántica durante la transición.
- El refactor debe terminar ocultando esos detalles al caller externo cuando puedan resolverse dentro del payment module sin modificar comportamiento.
- Si alguno no puede ocultarse sin introducir nuevo estado o cambiar semántica, mantenerlo dentro de un **domain concept cohesivo**, no como parámetro técnico suelto.

---

# 5. TARGET ARCHITECTURE — DECISIÓN CERRADA

La arquitectura final debe tener una única interface externa de payment operation.

## 5.1 Interface externa canónica

Crear un module canónico en:

```text
app/src/main/java/com/amaxonia/pos/domain/usecase/payment/PaymentOperation.kt
```

El nombre final obligatorio de la seam es:

```kotlin
fun interface PaymentOperation
```

Debe exponer una única operación:

```kotlin
suspend fun execute(
    request: PaymentOperationRequest,
    onEvent: suspend (PaymentOperationEvent) -> Unit,
): PaymentOperationResult
```

La sintaxis exacta puede adaptarse al style Kotlin del proyecto, pero no debe cambiar la semántica anterior.

### Restricción de profundidad

`PaymentOperationRequest` **NO** puede convertirse en un simple rename 1:1 de `ExecutePaymentFlowInput`.

Su interface final debe representar **conceptos de dominio**, no plumbing.

Objetivo final:

```kotlin
PaymentOperationRequest(
    payment = ...,
    source = ...,
)
```

Se permiten como máximo **3 conceptos top-level** si un tercero es realmente necesario por el código actual.

### Concepto 1 — `PaymentIntent`

Debe representar exclusivamente la intención del cajero/usuario respecto al pago:

- selección/asignación de formas de pago;
- condición `CONTADO` / `CREDITO`;
- monto entregado únicamente cuando sea una entrada real del usuario;
- cualquier dato que el usuario haya ingresado y que no pueda derivarse sin perder semántica.

**No debe contener:**

- repository;
- DAO;
- API client;
- caja;
- client repository;
- country config técnico;
- WorkManager;
- printer adapter;
- fiscal ledger;
- gateway ledger;
- callbacks;
- `PreparedSale`;
- `ProcessSaleRequestDto` completo.

### Concepto 2 — `PaymentSource`

Representa de dónde viene la venta, sin obligar al caller a conocer el pipeline.

Debe cubrir los casos existentes:

```text
CurrentCart
Preloaded/Draft (si el flujo actual realmente lo distingue al pagar)
TableAccount (si el flujo actual realmente lo distingue al pagar)
```

No inventar tipos de venta que no existan.

Durante la migración se permite que una variante interna cargue datos legacy necesarios; al final esos datos deben quedar encapsulados dentro del concepto, no dispersos como 5-10 parámetros en el caller.

### Concepto 3 — sólo si es imprescindible

Sólo se permite un tercer concepto top-level si TASK-00 demuestra que existe un dato:

- originado fuera del payment module;
- no derivable de repositories/holders/session existentes;
- que no pertenece a `PaymentIntent`;
- que no pertenece a `PaymentSource`.

Debe documentarse en este archivo antes de crearlo.

### Decisión documentada — `PaymentExecutionContext` (tercer concepto aplicado)

TASK-00/TASK-02 demostraron que `countryCode`, `exchangeRate`, `secondaryCurrency`,
`isMultiCurrency` y `availableMethods` se cargan hoy al inicializar la pantalla de pago
(`LoadPaymentContextUseCase` + `LoadPaymentCountryUseCase`) y viven en el estado del
`PaymentViewModel`. Re-leerlos dentro del módulo al ejecutar el pago cambiaría el
lifecycle/timing de la fuente de verdad (BLOCKER-ARCH-01: no es un refactor puro).
Por ello se encapsulan como **Concepto 3**: `PaymentExecutionContext`, un snapshot
congelado capturado por el caller con la misma fuente y el mismo timing actuales.
`printerType` sí se internalizó detrás de la seam (`DefaultPaymentOperation` lee
`posConfigurationRepository.selectedPrinterType`, la misma fuente que leía el ViewModel
en el baseline). Con esto `PaymentOperationRequest` queda en exactamente 3 conceptos
top-level cohesivos, sin repositories/DAOs/adapters.

## 5.2 Result y Events

Reutilizar la semántica actual de:

```text
PaymentFlowResult
PaymentFlowEvent
```

Durante la migración pueden mantenerse como aliases/nombres existentes.

Al finalizar:

```text
PaymentOperationResult
PaymentOperationEvent
```

deben ser los nombres canónicos **si el rename no obliga a modificar código fuera del scope**.

Si renombrarlos genera churn sin profundidad real, conservar `PaymentFlowResult` y `PaymentFlowEvent` está permitido.

**Prioridad:** profundidad de la interface, no renombrado cosmético.

## 5.3 Implementation canónica

Crear:

```text
app/src/main/java/com/amaxonia/pos/domain/usecase/payment/DefaultPaymentOperation.kt
```

`DefaultPaymentOperation` implementa `PaymentOperation`.

Debe absorber la orchestration externa que hoy obliga al caller/composition a conocer demasiadas piezas.

No significa mover todo a un archivo gigante.

Los modules internos existentes pueden permanecer:

```text
PrepareSaleUseCase
AssemblePreparedSaleUseCase
ValidatePaymentUseCase
CalculateSaleTotalsUseCase
BuildSaleItemsUseCase
BuildSaleRequestUseCase
StartTransactionUseCase
ExecuteGatewayPaymentUseCase
CompletePaymentSaleUseCase
QueueOfflineInvoiceUseCase
PrintInvoiceUseCase
ConfirmFiscalDocumentUseCase
GatewayCallbackLedger
PaymentFiscalConfirmationLedger
HandlePaymentFailureUseCase
```

pero pasan a ser **implementation/internal seams** del payment operation module.

## 5.4 Visibilidad

Cuando sea viable sin romper tests ni DI:

- usar `internal` para tipos que no deben ser consumidos fuera del module/package;
- no hacer `public` una clase sólo para facilitar tests;
- tests principales deben cruzar `PaymentOperation`;
- internal seams pueden tener tests directos cuando sea útil.

## 5.5 No duplicar seams

Al final NO deben coexistir como interfaces externas equivalentes:

```text
PaymentOperation
PaymentFlowExecutor
```

`PaymentFlowExecutor` puede existir transitoriamente.

Debe eliminarse al finalizar la migración si ya no tiene callers legítimos.

---

# 6. ARCHIVOS ACTUALES CLAVE

Leer antes de implementar:

## UI / caller

```text
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/ui/payment/PaymentViewModel.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/ui/payment/PaymentScreen.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/ui/common/DependencyContainer.kt
```

## Payment orchestration

```text
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/ExecutePaymentFlowUseCase.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/PrepareSaleUseCase.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/StartTransactionUseCase.kt
```

## Payment builders / validation

```text
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/BuildPaymentDetailsUseCase.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/BuildSaleItemsUseCase.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/BuildSaleRequestUseCase.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/CalculateSaleTotalsUseCase.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/ValidatePaymentUseCase.kt
```

## Gateway / fiscal / recovery

```text
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/ExecuteGatewayPaymentUseCase.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/ConfirmFiscalDocumentUseCase.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/GatewayCallbackLedger.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/QueueGatewayCallbackUseCase.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/QueueFiscalConfirmationUseCase.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/QueueOfflineInvoiceUseCase.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/HandlePaymentFailureUseCase.kt
```

Además, localizar el archivo actual de:

```text
PaymentFiscalConfirmationLedger
PrintInvoiceUseCase
CompletePaymentSaleUseCase
```

si alguno está declarado dentro de otro archivo.

## Tests

```text
amaxoniaerp-pos/app/src/test/java/com/amaxonia/pos/domain/usecase/payment/ExecutePaymentFlowUseCaseTest.kt
amaxoniaerp-pos/app/src/test/java/com/amaxonia/pos/ui/payment/PaymentViewModelTest.kt
```

Leer también cualquier test directo de los use cases anteriores encontrado mediante búsqueda.

## Read-only para comprender comportamiento

Sólo leer si hace falta:

```text
domain/repository/SalesRepository.kt
domain/repository/PaymentSessionReader.kt
domain/repository/CartRepository.kt
domain/repository/CajaRepository.kt
domain/repository/TransactionRepository.kt
domain/repository/TableAccountPaymentHolder.kt
domain/repository/ClientBranchRepository.kt
data/local/db/TransactionLogDao.kt
data/printer/RapidPayBridge.kt
MainActivity.kt
```

No modificarlos salvo autorización explícita en una TASK.

---

# 7. MÉTRICAS DE ÉXITO ARQUITECTÓNICO

Al finalizar, deben cumplirse todas:

- [x] Existe una única external seam `PaymentOperation`.
- [x] `PaymentViewModel` depende de `PaymentOperation`, no de la orchestration interna.
- [x] `PaymentViewModel` no construye `ExecutePaymentFlowInput`.
- [x] `ExecutePaymentFlowInput` fue eliminado o quedó totalmente interno sin callers UI; preferencia: eliminar. → quedó `internal`, cero callers UI.
- [x] `PaymentFlowExecutor` fue eliminado si sólo duplicaba la nueva seam. → eliminado.
- [x] La request externa contiene máximo 2-3 conceptos cohesivos. → 3: `payment`/`source`/`context`.
- [x] La request externa no contiene repositories/DAOs/adapters.
- [x] El caller no conoce `PaymentFlowRepositories`.
- [x] El caller no conoce `PaymentStateRepositories`.
- [x] El caller no conoce `PaymentRuntimeServices`.
- [x] El caller no conoce `PaymentPreparationOperations`.
- [x] El caller no conoce `PaymentExecutionOperations`.
- [x] El caller no conoce `PreparedSale`.
- [x] El caller no conoce `SalePreparation`.
- [x] `DependencyContainer` expone el payment module como una única capacidad canónica (`paymentOperation`).
- [x] No se agregó un framework DI.
- [x] No se agregó una segunda capa pass-through.
- [x] No se agregó una nueva interface para cada clase.
- [x] No hay comportamiento funcional nuevo.
- [x] Tests de pago pasan.
- [x] Detekt = 0 findings para la variante objetivo.
- [x] ktlint pasa. → ver nota precisa en 20.3/20.5: el comando de la variante es vacuamente exitoso (SKIPPED/NO-SOURCE); el refactor Payment no introdujo nuevas violaciones ktlint y las violaciones del gate global (`ktlintMain/TestSourceSetCheck`) son preexistentes al baseline.
- [x] assemble pasa.

---

# 8. TASK-00 — BASELINE Y CHARACTERIZATION LOCK

## Objetivo

Congelar el comportamiento actual antes de mover arquitectura.

## Archivos a leer

Todos los de la sección 6.

## Archivos a modificar

Sólo tests existentes y nuevos tests de payment si falta cobertura.

No modificar production code en esta TASK.

## Acciones

- [x] Confirmar HEAD de trabajo.
- [x] Si HEAD != baseline, revisar los cambios posteriores que toquen payment y adaptar el plan sin revertirlos.
- [x] Ejecutar tests actuales de payment.
- [x] Ejecutar build mínimo para confirmar baseline.
- [x] Crear una matriz de casos cubiertos.
- [x] Agregar characterization tests únicamente donde falte una invariante de la sección 4.

> Evidencia TASK-00: baseline `8c69209`; matriz cubierta por
> `ExecutePaymentFlowUseCaseTest` (online/offline/snapshot/crédito/gateway/fiscal/preparación/idempotencia),
> `StartTransactionIdempotencyTest` (carry-over + tenant scoping),
> `QueueGatewayCallbackUseCaseTest`, `AdvanceFiscalStateUseCaseTest`,
> `SynchronizePendingInvoicesUseCaseTest` y `PaymentViewModelTest`.

## Characterization tests obligatorios

Deben quedar cubiertos, reutilizando tests existentes cuando ya existan:

### Online

- [x] venta online exitosa;
- [x] request conserva total;
- [x] transaction local termina `PAID`;
- [x] error backend no guarda transaction pagada.

### Offline

- [x] venta offline se encola;
- [x] transaction local queda `PENDING`;
- [x] error al guardar transaction local mantiene semántica actual.

### Financial snapshot

- [x] snapshot precargado conserva:
  - subtotal gross;
  - item discounts;
  - subtotal net;
  - tax;
  - total;
- [x] items precargados no se recalculan indebidamente.

### Crédito

- [x] crédito 100% CXC;
- [x] crédito pago parcial + CXC;
- [x] tarjeta de crédito normal sigue contado;
- [x] saldo pendiente exacto;
- [x] agrupación `montosPorTipo` exacta.

### Gateway VE

- [x] configuración inválida corta antes de mutar;
- [x] rechazo gateway corta antes de enviar venta;
- [x] evento `LaunchGateway` se conserva;
- [x] no se modifica el flujo fuera de VE.

### Fiscal VE

- [x] fallo confirmación fiscal conserva venta exitosa;
- [x] emite `FiscalConfirmationFailed`;
- [x] conserva fiscal number / printer serial.

### Panamá

- [x] comportamiento PA existente con payment flow;
- [x] no existe dependencia accidental de HKA-20.

### Preparación

- [x] carrito vacío falla igual;
- [x] cliente faltante falla igual;
- [x] caja faltante falla igual;
- [x] secuencia faltante falla igual;
- [x] item manual/no sincronizado falla igual;
- [x] selección requerida de sucursal PA falla igual;
- [x] error de check de caja conserva mensaje/semántica.

### Idempotencia

Si ya existen tests específicos en otros archivos, referenciarlos; si no:

- [x] carry-over SENDING reutiliza correlation id;
- [x] confirmed/duplicate conserva semántica actual;
- [x] tenant incorrecto no reusa operación de otro tenant.

## Prohibiciones

- No cambiar production code para “hacer más testeable”.
- No introducir nueva seam todavía.
- No cambiar mensajes sólo para facilitar asserts.

## Validación TASK-00

Desde `amaxoniaerp-pos/`:

```bash
./gradlew :app:testAmaxoniaDebugUnitTest --no-daemon --console=plain
```

Si el nombre exacto de la task cambió, usar la task equivalente de la variante `AmaxoniaDebug` y registrar cuál se usó.

## Criterio de cierre

- [x] Baseline documentado.
- [x] Matriz de comportamiento cubierta.
- [x] No se modificó production code.
- [x] Tests de payment PASS.

---

# 9. TASK-01 — INTRODUCIR LA EXTERNAL SEAM `PaymentOperation`

## Objetivo

Crear la nueva interface canónica sin cambiar todavía la implementation.

## Crear

```text
app/src/main/java/com/amaxonia/pos/domain/usecase/payment/PaymentOperation.kt
app/src/main/java/com/amaxonia/pos/domain/usecase/payment/DefaultPaymentOperation.kt
```

## Modificar

```text
domain/usecase/payment/ExecutePaymentFlowUseCase.kt
tests de payment necesarios
```

## Implementación

- [x] Definir `PaymentOperation`.
- [x] Definir `PaymentOperationRequest`.
- [x] Definir `PaymentIntent`.
- [x] Definir `PaymentSource`.
- [x] No exponer plumbing.
- [x] Crear `DefaultPaymentOperation`.
- [x] En esta TASK, `DefaultPaymentOperation` puede delegar transitoriamente al executor actual para garantizar equivalencia.
- [x] Marcar esa delegación como transición del plan, no como arquitectura final.
- [x] No cambiar lógica existente.

## Regla de transición

La estructura transitoria puede ser:

```text
PaymentOperation
      |
DefaultPaymentOperation
      |
legacy ExecutePaymentFlowUseCase
```

pero sólo hasta TASK-05/TASK-07.

## Tests

Crear/ajustar tests para demostrar:

- [x] request equivalente produce result equivalente;
- [x] events mantienen orden/tipo observable;
- [x] failure mantiene mensaje;
- [x] success mantiene payload;
- [x] duplicate mantiene correlation id/reason.

## Criterio de cierre

- [x] Nueva seam compila.
- [x] Todavía no se eliminó legacy.
- [x] Tests de equivalencia PASS.
- [x] No existe cambio funcional.

---

# 10. TASK-02 — MOVER RESOLUCIÓN DE CONTEXTO DETRÁS DE LA SEAM

## Objetivo

Dejar de exigir al caller datos que el payment module ya puede obtener desde sus dependencies actuales.

## Leer primero

```text
PaymentViewModel.kt
DependencyContainer.kt
LocalStore / PaymentSessionReader
CartRepository
CajaRepository
FormaPagoRepository
LocalPosConfigurationRepository
TableAccountPaymentHolder
```

## Modificar

```text
PaymentOperation.kt
DefaultPaymentOperation.kt
PaymentViewModel.kt
DependencyContainer.kt
tests
```

Sólo dependencias directas adicionales si resultan imprescindibles.

## Datos candidatos a internalizar

Revisar uno por uno y mover detrás de la seam **sólo si la fuente actual es la misma**:

- `countryCode`;
- `exchangeRate`;
- `secondaryCurrency`;
- `isMultiCurrency`;
- `availableMethods`;
- `printerType`;
- caja activa;
- secuencia activa;
- cliente seleccionado;
- sucursal seleccionada;
- seller;
- warehouse;
- configuración de moneda.

## Regla estricta

No cambiar la fuente de verdad.

Ejemplos:

- Si `printerType` hoy sale de Settings/LocalStore, `DefaultPaymentOperation` debe leer la misma fuente.
- Si la tasa hoy sale de la caja activa, debe seguir saliendo de la misma caja activa.
- Si país hoy sale de la sesión/selección persistida, debe usar exactamente esa fuente.

## No hacer

- No volver a consultar red para un dato que hoy ya está en memoria/cache.
- No cambiar orden de lectura con efectos funcionales.
- No introducir singleton nuevo.
- No mover estado UI al dominio.
- No modificar repositories sólo para “hacerlo bonito”.

## Tests

Para cada dato internalizado:

- [x] test previo y posterior producen exactamente el mismo request de venta;
- [x] país exacto;
- [x] moneda exacta;
- [x] tasa exacta;
- [x] printer type exacto;
- [x] forma de pago exacta.

## Criterio de cierre

- [x] `PaymentOperationRequest` perdió datos técnicos derivables. → `printerType` internalizado detrás de la seam.
- [x] Caller conoce menos implementation.
- [x] Ninguna fuente de verdad cambió.

---

# 11. TASK-03 — ENCAPSULAR EL ORIGEN DE LA VENTA EN `PaymentSource`

## Objetivo

Eliminar del caller la dispersión de parámetros técnicos que describen cart/draft/table.

## Modificar

```text
PaymentOperation.kt
DefaultPaymentOperation.kt
PaymentViewModel.kt
PrepareSaleUseCase.kt (sólo si hace falta)
tests
```

Opcional, únicamente si ya es dependency directa:

```text
TableAccountPaymentHolder
CartRepository
```

## Casos que debe representar `PaymentSource`

Usar sólo los que el código actual necesita realmente.

### `CurrentCart`

La venta sale del estado actual del carrito/repositorios.

### Preloaded / Draft

Sólo si actualmente se usa una ruta distinta al pagar.

Debe preservar:

- authoritative items;
- `SaleFinancialSnapshot`;
- correlation carry-over si aplica.

### Table account

Sólo si actualmente el pago de mesa llega por este pipeline.

Debe preservar:

- `CuentaMesaVentaDto`;
- correlation preferida;
- cierre/confirmación actual.

## Restricción

El caller no debe volver a tener:

```text
saleItemsOverride = ...
financialSnapshotOverride = ...
cuentaMesa = ...
preferredCorrelationId = ...
```

como detalles técnicos sueltos.

Deben quedar:

- resueltos internamente desde holders/repositories existentes; o
- encapsulados cohesivamente en `PaymentSource`.

## Prohibición

No crear un nuevo repository/holder persistente sólo para esconder parámetros.

Si ocultar un dato exige nueva persistencia o cambia lifecycle, mantenerlo dentro de `PaymentSource` y registrar la razón.

## Tests

- [x] cart normal idéntico;
- [x] preloaded/draft idéntico;
- [x] snapshot autoritativo idéntico;
- [x] mesa idéntica;
- [x] correlation id idéntica;
- [x] sin doble consumo del holder;
- [x] error/retry no pierde source.

---

# 12. TASK-04 — INTERNALIZAR LA CONSTRUCCIÓN DEL LEGACY INPUT

## Objetivo

`PaymentViewModel` deja de construir `ExecutePaymentFlowInput`.

## Modificar

```text
PaymentViewModel.kt
DefaultPaymentOperation.kt
ExecutePaymentFlowUseCase.kt
PaymentOperation.kt
PaymentViewModelTest.kt
tests de domain/payment
```

## Implementación

- [x] Mover el mapping de `PaymentOperationRequest` hacia los datos legacy dentro del payment module.
- [x] `PaymentViewModel` sólo expresa intención.
- [x] Mantener el mismo cálculo/mapping usando funciones existentes.
- [x] No duplicar fórmulas.
- [x] Si un mapping existente está en ViewModel y es puro, moverlo o reutilizarlo sin cambiarlo.
- [x] No reescribir un cálculo que ya existe en `BuildPaymentDetailsUseCase`, `CalculateSaleTotalsUseCase`, etc.

## Criterio verificable

Debe dejar de existir en `PaymentViewModel.kt`:

```text
ExecutePaymentFlowInput(
```

y no debe importar `ExecutePaymentFlowInput`.

## Tests

- [x] `PaymentViewModelTest` usa fake `PaymentOperation`.
- [x] El fake captura sólo `PaymentOperationRequest`.
- [x] Tests UI/ViewModel no construyen el grafo completo de payment.
- [x] Domain tests siguen validando orchestration real.

## Criterio de cierre

- [x] Caller más pequeño.
- [x] Interface más profunda.
- [x] Sin cambio en outputs.

---

# 13. TASK-05 — ABSORBER LA ORCHESTRATION LEGACY EN `DefaultPaymentOperation`

## Objetivo

Eliminar la facade pass-through transitoria.

## Modificar

```text
DefaultPaymentOperation.kt
ExecutePaymentFlowUseCase.kt
PrepareSaleUseCase.kt
tests
```

Otros archivos payment internos únicamente si la extracción es necesaria.

## Implementación

`DefaultPaymentOperation` debe convertirse en la implementation canónica de la operación.

Hay dos opciones válidas **sólo internamente**:

### Opción A — renombrado/movimiento sin reescritura

Mover la orchestration de `ExecutePaymentFlowUseCase` a `DefaultPaymentOperation` manteniendo el código prácticamente igual.

Preferida cuando minimiza diff.

### Opción B — `ExecutePaymentFlowUseCase` queda como internal implementation

Permitida sólo si:

- no es external seam;
- no es consumido por UI;
- `PaymentOperation` es la única interface;
- no existe una segunda abstraction pass-through.

## Decisión requerida

Aplicar **deletion test**:

Si borrar `ExecutePaymentFlowUseCase` después de migrar sólo elimina una capa de forwarding, eliminarlo.

Si concentra orchestration interna real y su eliminación dispersaría lógica, mantenerlo `internal`.

## No hacer

- No dividir la orchestration en 10 nuevas clases.
- No fusionar gateway + fiscal + persistence en un método gigante si hoy están correctamente separados.
- No cambiar exception handling.
- No cambiar ordering.
- No cambiar telemetry.
- No cambiar `RapidPayBridge`.

## Tests

Reejecutar toda matriz de TASK-00.

---

# 14. TASK-06 — HACER INTERNOS LOS DETALLES DE IMPLEMENTATION

## Objetivo

Evitar que estructuras internas se conviertan en parte de la interface conocida por callers.

## Revisar

```text
PaymentFlowRepositories
PaymentStateRepositories
PaymentRuntimeServices
PaymentPreparationOperations
PaymentExecutionOperations
PreparedSale
PreparedSaleDetails
PreparedSaleFinancials
SalePreparation
```

## Acciones

Para cada tipo:

- [x] buscar todos los usages;
- [x] verificar si existe caller fuera de payment/composition/tests;
- [x] si no, reducir visibilidad a `internal` o `private` apropiadamente;
- [x] no mover de archivo sólo por estética;
- [x] no renombrar sin ganancia arquitectónica.

## Operation bags

`PaymentPreparationOperations` y `PaymentExecutionOperations` pueden permanecer **si son cohesivos dentro de la implementation**.

No crear más bags.

Si una de estas clases sólo existe para esconder un constructor largo y su eliminación no dispersa conocimiento, evaluar eliminación aplicando deletion test.

## Regla

El objetivo NO es tener menos clases.

El objetivo es que el caller aprenda menos.

---

# 15. TASK-07 — SIMPLIFICAR EL COMPOSITION ROOT DEL PAYMENT MODULE

## Objetivo

`DependencyContainer` debe construir y exponer una sola capacidad de pago.

## Modificar

```text
ui/common/DependencyContainer.kt
```

Tests/consumers necesarios.

## Resultado esperado

Externamente debe existir algo equivalente a:

```kotlin
val paymentOperation: PaymentOperation
```

Los detalles:

```text
PaymentFlowRepositories
PaymentPreparationOperations
PaymentExecutionOperations
PrepareSaleUseCase
AssemblePreparedSaleUseCase
CompletePaymentSaleUseCase
StartTransactionUseCase
GatewayCallbackLedger
PaymentFiscalConfirmationLedger
...
```

pueden seguir construyéndose dentro del composition root, pero no deben convertirse en dependencias que UI tenga que ensamblar.

## No hacer

- No introducir Hilt.
- No introducir Koin.
- No introducir Dagger.
- No introducir Service Locator nuevo.
- No mover todo `DependencyContainer`.
- No refactorizar repositories ajenos al payment module.
- No intentar arreglar todo el global composition root en esta iniciativa.

## Criterio de cierre

- [x] Payment UI obtiene `PaymentOperation`.
- [x] Wiring interno sigue una única ruta.
- [x] No existe payment graph duplicado.

---

# 16. TASK-08 — MIGRAR `PaymentViewModel` A LA NUEVA INTERFACE FINAL

## Objetivo

Completar la migración del caller principal.

## Modificar

```text
ui/payment/PaymentViewModel.kt
ui/payment/PaymentScreen.kt únicamente si la firma de construcción del ViewModel lo exige
ui/payment/PaymentViewModelTest.kt
```

## Resultado

`PaymentViewModel` debe:

- mantener UI state;
- validar entradas puramente de UI que ya valida hoy;
- construir `PaymentIntent`;
- seleccionar `PaymentSource` cuando sea una decisión del caller;
- llamar `PaymentOperation.execute(...)`;
- traducir events/result al UI state actual.

No debe:

- preparar una venta;
- construir request backend;
- elegir adapter fiscal;
- gestionar transaction log;
- gestionar retries;
- crear correlation ids;
- resolver tenant;
- gestionar gateway ledger;
- imprimir directamente;
- confirmar fiscal directamente.

## Events

Mantener exactamente las reacciones actuales:

```text
Progress
LaunchGateway
FiscalConfirmationFailed
```

o sus nombres canónicos si fueron renombrados sin churn.

## Results

Mantener exactamente:

```text
Success
Failure
DuplicateInvoice
```

y sus payloads observables.

## Tests

`PaymentViewModelTest` debe demostrar:

- [x] request correcto hacia `PaymentOperation`;
- [x] success actualiza UI igual;
- [x] failure actualiza UI igual;
- [x] duplicate igual;
- [x] gateway launch igual;
- [x] fiscal confirmation failed igual;
- [x] no necesita construir repositories del payment pipeline para cada test.

---

# 17. TASK-09 — REEMPLAZAR TEST SURFACE DEL PIPELINE

## Objetivo

Que la external interface sea también el test surface principal.

## Crear/renombrar

Preferencia:

```text
app/src/test/java/com/amaxonia/pos/domain/usecase/payment/PaymentOperationTest.kt
```

Migrar cobertura de:

```text
ExecutePaymentFlowUseCaseTest.kt
```

sin perder casos.

## Estrategia

### Tests de `PaymentOperation`

Deben cubrir el comportamiento end-to-end del module con adapters fake/in-memory existentes:

- preparación;
- online;
- offline;
- crédito;
- gateway;
- fiscal;
- duplicate/reconciliation;
- preloaded snapshot;
- table account si aplica.

### Tests internal seams

Conservar sólo donde exista una razón clara:

- cálculo;
- builder;
- ledger transitions;
- retry/backoff;
- validation.

## No hacer

- No borrar tests sólo porque “ya pasan por arriba”.
- No duplicar el mismo escenario en 3 niveles sin valor.
- No abrir production visibility para tests.
- No mockear cada función interna.

## Deletion test de tests

Si un test interno sólo verifica forwarding y la nueva interface ya lo cubre, eliminarlo.

Si verifica un invariant complejo de una internal seam, conservarlo.

---

# 18. TASK-10 — ELIMINAR PLUMBING LEGACY Y CÓDIGO MUERTO

## Objetivo

Terminar el reemplazo; no dejar dos arquitecturas.

## Buscar y resolver

```text
ExecutePaymentFlowInput
PaymentFlowExecutor
ExecutePaymentFlowUseCase
```

### `ExecutePaymentFlowInput`

- [x] eliminar si ya no tiene caller legítimo;
- [x] no dejar alias permanente.

### `PaymentFlowExecutor`

- [x] eliminar si `PaymentOperation` lo reemplazó;
- [x] no mantener dos interfaces iguales.

### `ExecutePaymentFlowUseCase`

- [x] aplicar deletion test;
- [x] eliminar si quedó pass-through;
- [x] mantener `internal` únicamente si concentra implementation real.

> Resultado del deletion test: `ExecutePaymentFlowUseCase` **permanece `internal`**
> porque concentra orchestration real (validación de configuración de gateway antes
> de mutar estado, dispatch de preparación, recuperación de correlation id vía
> `StartTransactionUseCase`, awaiting/resolution del `GatewayCallbackLedger`,
> secuenciación con `CompletePaymentSaleUseCase` y marcado del ledger según el
> resultado). Eliminarlo dispersaría esa lógica; no es una facade de forwarding.
> `DefaultPaymentOperation` es el adapter fino de la seam externa y dueño del
> mapping request→legacy (incluida la lectura de `printerType` desde la misma
> fuente de verdad del baseline).

## Limpiar

- imports muertos;
- factories/helpers de tests ya innecesarios;
- comentarios que describan arquitectura vieja;
- wiring duplicado.

## No hacer

- No limpiar código ajeno.
- No “modernizar” sintaxis no relacionada.
- No cambiar nombres de DTOs HTTP.
- No cambiar package structure fuera del payment module.

---

# 19. TASK-11 — AUDITORÍA DE REGRESIÓN MULTI-PAÍS

## Objetivo

Verificar explícitamente que el refactor no tocó negocio.

## Checklist estático

### Backend contract

- [x] No hay diff en `amaxoniaerp-backend/**`.
- [x] `ProcessSaleRequestDto` wire contract no cambió.
- [x] `ProcessSaleResponseDto` wire contract no cambió.
- [x] endpoint de venta no cambió.
- [x] endpoint de confirmación fiscal no cambió.

### VE HKA-20

- [x] `useHka20` se propaga igual.
- [x] `PrinterType.THE_FACTORY_HKA` se interpreta igual.
- [x] gateway ocurre antes del envío de venta como antes.
- [x] no existe fallback digital.
- [x] `RapidPayBridge` no cambió salvo que sólo haya sido necesario ajustar import/caller sin semántica.
- [x] callback ledger igual.
- [x] fiscal confirmation igual.

### VE digital

- [x] no cambió selección;
- [x] no cambió request;
- [x] no cambió result handling.

### Panamá

- [x] no cambió request;
- [x] no cambió multimoneda;
- [x] no cambió sucursal cliente;
- [x] no cambió FE.

### Crédito

- [x] `formaPago=credito` igual;
- [x] CXC igual;
- [x] pago parcial igual;
- [x] tarjeta normal igual.

### Offline

- [x] queue igual;
- [x] pending transaction igual.

### Mesa

- [x] request de cuenta igual;
- [x] reglas online igual.

## Diff audit

El agente debe revisar el diff completo y responder:

> ¿Alguna línea modificada cambia una decisión de negocio?

La respuesta exigida para finalizar es:

```text
NO
```

Si la respuesta es `sí` o `posiblemente`, corregir antes de continuar.

---

# 20. TASK-12 — VALIDACIÓN FINAL

Ejecutar desde:

```text
amaxoniaerp-pos/
```

## 20.1 Unit tests

```bash
./gradlew :app:testAmaxoniaDebugUnitTest --no-daemon --console=plain
```

Resultado:

- [x] PASS payment tests.
- [x] PASS PaymentViewModel tests.
- [x] PASS suite completa de la variante o, si existe un fallo preexistente ajeno, documentarlo con evidencia y demostrar que no fue introducido por el refactor.

## 20.2 Detekt

```bash
./gradlew :app:detektAmaxoniaDebug --no-daemon --console=plain
```

Resultado obligatorio:

- [x] `BUILD SUCCESSFUL`
- [x] 0 nuevos findings
- [x] no nuevos suppress
- [x] no baseline modificado para ocultar findings

## 20.3 ktlint

```bash
./gradlew :app:ktlintAmaxoniaDebugSourceSetCheck --no-daemon --console=plain
```

Resultado:

- [x] PASS, con precisión requerida: la task exacta del plan resulta `SKIPPED`
      (`runKtlintCheckOverAmaxoniaDebugSourceSet` = `NO-SOURCE`; el source set
      variante no tiene fuentes propias), por lo que el `BUILD SUCCESSFUL` es
      vacuo. La verificación real se hizo con los checks equivalentes existentes
      (`ktlintCheck`/`ktlintMainSourceSetCheck`/`ktlintTestSourceSetCheck`)
      sin cambiar configuración. Conclusiones exactas:
      1. el refactor Payment **no introdujo nuevas violaciones ktlint**
         (los archivos tocados por el refactor quedaron limpios; las 8 que el
         HEAD intermedio había añadido en `PaymentViewModelTest.kt` se
         corrigieron en el cierre);
      2. todas las violaciones restantes del gate global son **preexistentes
         al baseline** `8c69209` (verificado comparando reportes contra el
         baseline: main ≈334 repartidas por todo el app + 2 en
         `FormaPagoSerializationTest`, idénticas a las del baseline);
      3. el saneamiento global de ktlint **queda fuera de este plan y será
         tratado posteriormente** (no se tocaron para no mezclar formateo
         masivo ajeno al payment module).

Si el proyecto expone una task ligeramente distinta, usar la equivalente existente sin cambiar configuración.

## 20.4 Assemble

```bash
./gradlew :app:assembleAmaxoniaDebug --no-daemon --console=plain
```

Resultado:

- [x] `BUILD SUCCESSFUL`.

## 20.5 Validación opcional si está disponible y no exige hardware

```bash
./gradlew :app:lintAmaxoniaDebug --no-daemon --console=plain
```

No modificar reglas para hacerlo pasar.

> ### Evidencia de validación final (auditoría de cierre)
>
> Ejecutado desde `amaxoniaerp-pos/` sobre HEAD final:
>
> - `:app:testAmaxoniaDebugUnitTest` → **BUILD SUCCESSFUL** (suite completa;
>   `PaymentViewModelTest` 20/20, `PaymentOperationTest` 2/2,
>   `ExecutePaymentFlowUseCaseTest` 16/16).
> - `:app:detektAmaxoniaDebug` → **BUILD SUCCESSFUL**, 0 findings.
> - `:app:ktlintAmaxoniaDebugSourceSetCheck` → **BUILD SUCCESSFUL**
>   (task del plan; su source set variante no tiene fuentes propias y resulta
>   `SKIPPED/NO-SOURCE`, comportamiento idéntico al baseline).
> - `:app:assembleAmaxoniaDebug` → **BUILD SUCCESSFUL**.
>
> Nota de deuda ktlint preexistente (fuera de este refactor, documentada con
> evidencia): `ktlintMainSourceSetCheck` y `ktlintTestSourceSetCheck` completos
> ya fallaban en el baseline `8c69209` con violaciones repartidas por todo el
> app (caja, creditnotes, mesas, reports, products, previews y
> `FormaPagoSerializationTest`). Enunciados exactos de cierre:
>
> 1. **El refactor Payment no introdujo nuevas violaciones ktlint.** Los
>    archivos tocados por el refactor quedan limpios; las 8 que el HEAD
>    intermedio había añadido en `PaymentViewModelTest.kt` se corrigieron en el
>    cierre (verificado contra reportes del baseline y de HEAD).
> 2. **Las violaciones restantes del gate global son preexistentes al
>    baseline** `8c69209` (main ≈334 en archivos ajenos al payment module +
>    2 en `FormaPagoSerializationTest`; en los archivos tocados por el
>    refactor, las 6 de `PaymentScreen.kt` existen idénticas en el baseline).
> 3. **El saneamiento global de ktlint queda fuera de este plan y será
>    tratado posteriormente**; no se tocaron en este refactor para no mezclar
>    formateo masivo ajeno al payment module.

---

# 21. CRITERIO DE FINALIZACIÓN GLOBAL

El trabajo NO está terminado hasta que se cumpla todo:

## Arquitectura

- [x] `PaymentOperation` es la external seam única.
- [x] `PaymentViewModel` usa únicamente esa seam para ejecutar pago.
- [x] El request externo expresa intención/origen, no plumbing.
- [x] Implementation interna queda encapsulada.
- [x] No hay facade duplicada permanente.
- [x] No hay nueva abstracción sin razón.

## Funcionalidad

- [x] comportamiento observable idéntico;
- [x] online idéntico;
- [x] offline idéntico;
- [x] contado idéntico;
- [x] crédito/CXC idéntico;
- [x] PA idéntico;
- [x] VE digital idéntico;
- [x] VE HKA-20 idéntico;
- [x] mesa idéntica;
- [x] draft/preloaded idéntico;
- [x] idempotencia idéntica;
- [x] gateway idéntico;
- [x] fiscal confirmation idéntica.

## Calidad

- [x] unit tests PASS;
- [x] Detekt PASS;
- [x] ktlint PASS; → ver 20.3/20.5: comando del plan SKIPPED/vacuo; 0 nuevas violaciones introducidas por el refactor; deuda restante preexistente al baseline y fuera de este plan;
- [x] assemble PASS;
- [x] no nuevos suppress;
- [x] no reglas deshabilitadas;
- [x] no baseline usado para ocultar deuda;
- [x] no cambio backend;
- [x] no cambio schema;
- [x] no cambio env/secrets.

---

# 22. ARCHIVOS QUE SE ESPERA CREAR

Obligatorios:

```text
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/PaymentOperation.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/DefaultPaymentOperation.kt
amaxoniaerp-pos/app/src/test/java/com/amaxonia/pos/domain/usecase/payment/PaymentOperationTest.kt
```

El último puede reemplazar/absorber el test legacy cuando la migración esté completa.

No crear más archivos salvo necesidad demostrada.

---

# 23. ARCHIVOS QUE SE ESPERA MODIFICAR

Principalmente:

```text
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/ui/payment/PaymentViewModel.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/ui/common/DependencyContainer.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/ExecutePaymentFlowUseCase.kt
amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/usecase/payment/PrepareSaleUseCase.kt
amaxoniaerp-pos/app/src/test/java/com/amaxonia/pos/ui/payment/PaymentViewModelTest.kt
amaxoniaerp-pos/app/src/test/java/com/amaxonia/pos/domain/usecase/payment/ExecutePaymentFlowUseCaseTest.kt
```

Posibles modificaciones internas, sólo si son necesarias para encapsulación y sin cambio funcional:

```text
BuildPaymentDetailsUseCase.kt
BuildSaleItemsUseCase.kt
BuildSaleRequestUseCase.kt
CalculateSaleTotalsUseCase.kt
ValidatePaymentUseCase.kt
StartTransactionUseCase.kt
ExecuteGatewayPaymentUseCase.kt
ConfirmFiscalDocumentUseCase.kt
QueueOfflineInvoiceUseCase.kt
GatewayCallbackLedger.kt
```

No modificar todos por defecto.

---

# 24. ARCHIVOS EXPLÍCITAMENTE PROHIBIDOS

No modificar:

```text
amaxoniaerp-backend/**
amaxoniaerp-backend/.env*
amaxoniaerp-pos/local.properties
amaxoniaerp-pos/app/schemas/**
doc/PLAN_TODO_POS.md
```

Tampoco:

- payload builders PAC;
- repositories backend;
- tablas;
- migrations;
- reglas crediticias;
- notas de crédito;
- UI visual;
- assets;
- themes.

---

# 25. REGLAS DE DIFF

Cada commit/TASK debe cumplir:

1. El diff tiene una razón arquitectónica directa.
2. No mezcla formateo masivo.
3. No renombra archivos no relacionados.
4. No cambia strings funcionales salvo que sea estrictamente necesario para un rename interno y no sean visibles.
5. No cambia logs/telemetry salvo imports/nombre del caller.
6. No cambia códigos de error.
7. No cambia estados.
8. No cambia constantes financieras.
9. No cambia delays/retries/lease.
10. No cambia endpoints.

---

# 26. ESTRATEGIA DE COMMITS

Recomendado, un commit por milestone coherente:

```text
test(payment): lock payment operation behavior
refactor(payment): introduce payment operation seam
refactor(payment): internalize payment context
refactor(payment): encapsulate payment source
refactor(payment): migrate payment view model
refactor(payment): deepen payment implementation
refactor(payment): simplify payment composition
test(payment): move coverage to payment operation interface
refactor(payment): remove legacy payment plumbing
```

No es obligatorio usar exactamente esos mensajes.

No hacer squash hasta que todo pase, salvo instrucción expresa del usuario.

---

# 27. ESTRATEGIA DE ROLLBACK

Como es refactor puro:

- cada milestone debe ser revertible sin migración de datos;
- no hay migration DB;
- no hay cambio de wire contract;
- no hay feature flag nuevo;
- no hay cambio de backend.

Si aparece regresión durante una TASK:

1. revertir sólo los cambios de esa TASK;
2. mantener tests de characterization;
3. identificar qué invariant se alteró;
4. volver a implementar preservando comportamiento;
5. no “corregir” la regresión modificando el comportamiento esperado.

---

# 28. BLOCKERS

## BLOCKER-ARCH-01 — dato no derivable

Si al reducir `PaymentOperationRequest` aparece un dato técnico que hoy sólo existe en `PaymentViewModel` y moverlo exige:

- nueva persistencia;
- cambio de lifecycle;
- cambio de repository;
- nueva llamada de red;
- cambio de fuente de verdad;

**NO inventar solución**.

Encapsularlo temporalmente dentro de `PaymentIntent` o `PaymentSource`, documentar la razón y continuar si sigue siendo refactor puro.

## BLOCKER-ARCH-02 — caller externo desconocido

Si `ExecutePaymentFlowUseCase`, `PaymentFlowExecutor` o `ExecutePaymentFlowInput` tienen callers reales fuera del POS Android esperado:

- listar callers;
- no eliminar el tipo hasta migrarlos;
- si el caller está fuera del scope y no puede migrarse sin ampliar scope, detener la eliminación y documentar.

## BLOCKER-ARCH-03 — cambio funcional requerido

Si el refactor sólo puede continuar cambiando una regla de negocio:

- detener;
- no implementar;
- reportar cuál invariant lo impide.

## BLOCKER-ARCH-04 — HKA hardware

La ausencia de hardware HKA no bloquea el refactor si:

- tests existentes/fakes pasan;
- compile pasa;
- no se modificó el adapter/protocolo.

La validación física queda para QA del usuario.

---

# 29. NO-OBJETIVOS

Este trabajo NO pretende:

- arreglar bugs funcionales;
- agregar métodos de pago;
- cambiar UX;
- mejorar UI;
- cambiar moneda;
- cambiar crédito;
- cambiar PAC;
- cambiar HKA;
- cambiar impresoras;
- cambiar Room;
- cambiar backend;
- introducir arquitectura “clean” nueva;
- introducir MVI;
- introducir DI framework;
- mover todo el proyecto a nuevos packages;
- eliminar todas las clases pequeñas;
- resolver otras oportunidades del architecture review.

---

# 30. DEFINITION OF DONE PARA EL PR / PUSH

El agente debe entregar un resumen final con:

```text
1. External seam final:
   - PaymentOperation
   - request top-level concepts: ...
   - result/events usados: ...

2. Legacy eliminado:
   - ExecutePaymentFlowInput: eliminado / internal (explicar)
   - PaymentFlowExecutor: eliminado / internal (explicar)
   - ExecutePaymentFlowUseCase: eliminado / internal (explicar deletion test)

3. Callers migrados:
   - PaymentViewModel
   - otros, si existían

4. Behavior:
   - no functional changes

5. Validation:
   - testAmaxoniaDebugUnitTest: PASS
   - detektAmaxoniaDebug: PASS
   - ktlintAmaxoniaDebugSourceSetCheck: BUILD SUCCESSFUL (task SKIPPED/NO-SOURCE
     en esta variante; verificación con checks equivalentes — sin nuevas
     violaciones introducidas por el refactor; deuda restante preexistente al
     baseline; saneamiento global fuera de este plan)
   - assembleAmaxoniaDebug: PASS

6. Multi-country regression:
   - PA: preserved
   - VE digital: preserved
   - VE HKA-20: preserved

7. Prohibited changes:
   - backend: untouched
   - env/secrets: untouched
   - Room schema/migrations: untouched
```

Sólo después de cumplirlo todo se considera listo para integrar/push a `main`.

---

# 31. PROMPT MÍNIMO PARA EL AGENTE IMPLEMENTADOR

Usar este prompt junto con este archivo:

```text
Implementa COMPLETO `PLAN_REFACTOR_PAYMENT_ARCHITECTURE.md`.

Reglas:
- Lee primero el plan completo y `amaxoniaerp-pos/AGENTS.md`.
- Es 100% refactor arquitectónico: comportamiento observable idéntico.
- Ejecuta las TASK en orden y marca únicamente sus checks en el plan.
- No rediseñes el plan, no amplíes scope y no hagas refactors oportunistas.
- No modifiques backend, contratos HTTP, BD/Room schema, .env, secretos, reglas monetarias, crédito, PAC, HKA-20 ni UI visual.
- No agregues Suppress, baseline entries ni desactives reglas.
- Conserva estrictamente PA, VE digital y VE HKA-20.
- No dejes una facade nueva encima de la arquitectura vieja: al finalizar debe existir una sola external seam canónica `PaymentOperation`.
- Ejecuta las validaciones indicadas al terminar cada milestone y toda la validación final.
- Si aparece un BLOCKER del plan, documenta y detente sólo en la parte bloqueada; no improvises.
- No hagas push a main hasta completar TODO el plan y tener la validación final en verde.
```

---

# 32. CHECKLIST MAESTRO

- [x] TASK-00 — Baseline y characterization lock
- [x] TASK-01 — External seam `PaymentOperation`
- [x] TASK-02 — Contexto detrás de la seam
- [x] TASK-03 — `PaymentSource`
- [x] TASK-04 — Input legacy fuera del ViewModel
- [x] TASK-05 — Orchestration absorbida
- [x] TASK-06 — Detalles internalizados
- [x] TASK-07 — Composition root simplificado
- [x] TASK-08 — PaymentViewModel migrado
- [x] TASK-09 — Test surface migrado
- [x] TASK-10 — Legacy eliminado
- [x] TASK-11 — Auditoría multi-país
- [x] TASK-12 — Validación final
- [x] Definition of Done completa
- [x] Listo para push a `main`
