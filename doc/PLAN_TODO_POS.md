# PLAN TODO POS — Amaxonia / ListoERP

> Plan de ejecución para agente de implementación de bajo costo.
> 
> Estado auditado sobre el código fuente entregado en `amaxonia-pos-main`.
> No es un plan genérico: las decisiones y tareas se basan en la implementación actual del backend Ktor/Exposed y del POS Android/Compose.

## 0. Reglas de ejecución

1. Ejecutar **una TASK por vez** y detenerse al terminarla.
2. Leer `amaxoniaerp-backend/AGENTS.md` o `amaxoniaerp-pos/AGENTS.md` antes de modificar cada proyecto.
3. No reescribir features existentes. Reutilizar repositorios, DTOs, tablas, factories, strategies, ports/adapters, Room, WorkManager y use cases ya presentes.
4. No modificar `.env`, `.env.development`, secretos ni configuración de producción.
5. No tocar el flujo HKA-20 Venezuela salvo que una TASK lo indique expresamente. Cuando una TASK afecte código compartido debe incluir regresión VE.
6. No insertar directamente en `cxc_edocuenta`.
7. No abrir/mantener una transacción SQL mientras se hace una llamada HTTP al PAC.
8. Nueva lógica monetaria: `BigDecimal`/`Money`, escala y rounding explícitos. No agregar comparaciones financieras críticas con `Double`.
9. No crear tablas/columnas para resolver un supuesto. Los únicos cambios de esquema permitidos son los descritos explícitamente en este plan.
10. Si una TASK llega a un `BLOCKER-*`, detener esa TASK y reportarlo; continuar con otras TASK independientes sí está permitido.
11. Al terminar cada TASK ejecutar únicamente sus tests mínimos y reportar `PASS/FAIL`.
12. No avanzar automáticamente a la siguiente TASK.

---

# 1. Resumen ejecutivo

La mayor parte del esqueleto transaccional ya existe. `POST /api/pos/ventas/procesar` ya persiste factura, detalle, inventario y caja dentro de una transacción, controla duplicados y usa decrementos condicionales de stock. La facturación electrónica Panamá ya usa Strategy + Factory + Port/Adapter + Builder y Venezuela ya tiene separación HKA-20/digital.

Los principales gaps reales son: el POS y el backend fuerzan ventas `contado`; `CXC` se transforma erróneamente en `OT`; `caja_nueva` siempre queda `Pagada`; la fecha de vencimiento usa días globales y no los del cliente; falta validar `permitecredito`; las NC Panamá reutilizan buena base existente pero no prorratean descuento global, sólo anulan la factura origen cuando la devolución es total, el reverso parcial usa monto positivo + ID 30 hardcodeado y no existe todavía el envío PAC de NC; la UI de historial no expone los filtros ya parcialmente disponibles; no hay retry para `Connection reset`; y hay dos requerimientos que no pueden implementarse correctamente sin contrato adicional: combos e ítems manuales/no sincronizados.

La sincronización de catálogos **ya usa `CoroutineWorker` + WorkManager y no presenta un overlay bloqueante**; no debe refactorizarse sin un repro adicional.

---

# 2. Arquitectura actual que se debe conservar

## Backend

- Ktor + kotlinx.serialization.
- Exposed sobre la BD operativa de cada empresa.
- `DatabaseManager.connectToCompanyDb(...)` + `dbQuery`.
- Features separadas por dominio (`sales`, `creditnotes`, `electronicinvoice`, `facturas`, `caja`, etc.).
- Tablas multi-país mediante factories (`*TableFactory`).
- `BusinessClock` para fechas operativas.
- `ProcessSaleUseCase` orquesta venta y FE después de persistir la venta.
- FE Panamá existente:
  - `ElectronicInvoiceStrategy`
  - `ElectronicInvoiceProcessorFactory`
  - `PanamaInvoiceProcessor`
  - `PanamaElectronicInvoiceClient`
  - `TheFactoryHkaRestClient`
  - `TheFactoryHkaPayloadBuilder`

## Android

- Kotlin + Compose.
- ViewModel + Use Cases + Repository.
- Room como cache/local persistence.
- WorkManager/CoroutineWorker para sincronización.
- `Money` para parte del pipeline monetario.
- `PrepareSaleUseCase` / `ExecutePaymentFlowUseCase` como pipeline de venta.

---

# 3. Matriz de auditoría del TODO

| ID | Requerimiento | Estado | Evidencia actual | Gap / acción |
|---|---|---|---|---|
| CRED-01 | Endpoint/controlador de guardado venta | DONE | `sales/route/SalesRoutes.kt:27,54`; `POST /procesar` recibe `ProcessSaleRequest` | No crear endpoint nuevo. |
| CRED-02 | Evitar doble procesamiento | DONE | `ProcessSaleTransactionalRepository.kt:360-376` valida `idFactura` y rechaza documento existente | Mantener 409/idempotencia actual. |
| CRED-03 | Validación stock transaccional | PARTIAL | `:379-413` pre-valida; `:855+` decrementa con condición de disponibilidad | Endurecer lotes; stock principal ya evita oversell al actualizar. |
| CRED-04 | Sumar pagos vs gran total | PARTIAL | Android `PaymentState.isPaymentEnough`; backend recibe resumen/pagos | Backend debe validar coherencia y saldo CxC, no confiar en frontend. |
| CRED-05 | Detectar crédito por CXC/formaPago/saldo | BUG | Android fuerza `formaPago="contado"` (`PrepareSaleUseCase.kt:315`) y saldo 0 (`:350`); backend `normalizeTipoMovimiento()` convierte `CXC -> OT` (`ProcessSaleTransactionalRepository.kt:1085-1099`) | TASK-03/TASK-04. |
| CRED-06 | Validar `clientes.permitecredito` | MISSING | Tabla existe `ClientsTable.kt:24`; no se consulta al procesar venta | TASK-02/TASK-03. |
| CRED-07 | Vencimiento por `clientes.dias` | BUG | Tabla existe `ClientsTable.kt:26`; venta usa `parametros_generales.dias_vencimiento` (`ProcessSaleTransactionalRepository.kt:329,552`) | TASK-03. |
| CRED-08 | Cabecera + correlativos | DONE | `ProcessSaleTransactionalRepository` ya resuelve código/correlativo e inserta cabecera | Reutilizar. |
| CRED-09 | Detalle + inventario + lotes | PARTIAL | Factura detalle, stock, kardex y lotes ya existen | TASK-05 sólo endurece atomicidad de lotes. |
| CRED-10 | Combos: línea comercial + subitems inventario | BLOCKED | No existe `combo`, tabla de composición ni `factura_detalle_producto_combo` en el repo | `BLOCKER-COMBO-01`. No inventar esquema. |
| CRED-11 | Prohibido insertar `cxc_edocuenta` | DONE | No hay escritura a `cxc_edocuenta` en el flujo auditado | Mantener. |
| CRED-12 | `caja_nueva` Pendiente/Pagada según saldo | BUG | `ProcessSaleTransactionalRepository.kt:987` fija `CajaStatus.Pagada` | TASK-03. |
| NC-01 | Aislamiento VE/PA | DONE | `countryCode`, `useHka20`, `ElectronicInvoiceProcessorFactory.kt:37-41`; `ProcessSaleUseCase` ya separa HKA20/digital | No crear otro feature flag paralelo. |
| NC-02 | Consultar devoluciones previas | DONE | `CreditNoteRepository.kt:505-520` suma detalle previamente devuelto por `idDetalleFactura` | Endurecer concurrencia en TASK-10. |
| NC-03 | Bloquear sobre-acreditación | PARTIAL | `:588-591` rechaza cantidad > disponible | Existe race entre dos NC simultáneas; TASK-10. |
| NC-04 | `factura_detalle.anulado=1` al agotar línea | DONE | `CreditNoteRepository.create()` actualiza cuando disponible llega a 0 | Mantener. |
| NC-05 | Prorratear descuento global | BUG | Cabecera NC fija descuento global a 0 (`CreditNoteRepository.kt:244-245`) | TASK-11. |
| NC-06 | Recalcular reintegro/impuestos | PARTIAL | `calculateTotals()` usa total - subtotal (`:607-611`) | Debe incorporar prorrateo global y conservar invariantes; TASK-11. |
| NC-07 | PA: factura origen `cod_estatus=3` aun parcial | BUG | Sólo `cancelInvoiceAndOriginalCash()` lo hace para devolución total (`:658-661`) | TASK-12; VE no cambia. |
| NC-08 | Total: anular `caja_nueva` + recibos AN | DONE | `CreditNoteRepository.kt:663-689` | Reutilizar en PA finalizada. |
| NC-09 | Parcial: reverso negativo + forma pago paramétrica | BUG | `:721-753` inserta monto positivo y `CREDIT_NOTE_PAYMENT_FORM_ID=30` hardcodeado | TASK-12; resolver forma por catálogo `caja_forma_pago` (`siglas=NC`) con fallback sólo documentado si es necesario. |
| NC-10 | Devolver inventario opcional + Kardex 14 | DONE | `devolverStock`, `restoreInventory()`, movimiento 14 (`:756-855`) y lotes (`:858-886`) | Mantener; ejecutar sólo al finalizar PA aceptada. |
| NC-11 | Generar abono | DONE | `registerAbono()` inserta `abono`, `tipo="nota_credito"` (`:982-1031`) | Mantener. |
| NC-12 | Reintegro físico | DONE | `registerRefundCashEgress()` crea egreso en `caja_nueva` (`:889-979`) | Mantener. |
| NC-13 | Payload PAC NC Panamá | MISSING | FE de factura existe; DTO PAC ya tiene `listaDocsFiscalReferenciados` + `TheFactoryHkaDocFiscalRef` | TASK-13, sujeto a `BLOCKER-PAC-01` para contrato exacto no representado. |
| NC-14 | PAC síncrono sin romper consistencia | MISSING | `CreditNoteService.create()` hoy es una sola `dbQuery`; no llama PAC | TASK-14: staged workflow, sin HTTP dentro de SQL transaction. |
| NC-15 | Persistir CUFE/QR/número fiscal | PARTIAL | `CreditNoteHeaderTablePA` ya tiene `cufe`, `qr`, `numeroDocumentoFiscal`, etc.; create los deja vacíos | TASK-14. |
| NC-16 | Guardar PDF/XML | PARTIAL/BLOCKED | PAC port ya soporta PDF; no existe método/endpoint XML en código | PDF TASK-15; XML `BLOCKER-PAC-XML-01`. |
| BUG-01 | Crash `tipoMoneda` null | BUG | Backend y Android lo modelan non-null; backend mapea `row[...]` directo | TASK-01. |
| BUG-02 | Contado permite CxC | MISSING | No existe condición de pago explícita en `PaymentState`; lista agrupa todo no-CASH | TASK-04. |
| BUG-03 | Monto de orden/factura precargada se recalcula | BUG | `AssemblePreparedSaleUseCase.kt:230-231` recalcula `saleItemsOverride`; `JsonDraftInvoiceRestorer` ignora `DraftInvoice.total` | TASK-07. |
| BUG-04 | `Connection reset` cargando cajas | MISSING | `ApiClient.kt:65-68` tiene timeouts pero no retry; caja usa GET | TASK-06. |
| BUG-05 | Ítems manuales/no sincronizados | BLOCKED | Mensaje es validación intencional; manual item usa ID `manual_*`, backend exige `idItem:Int` | `BLOCKER-MANUAL-01`. No quitar validación. |
| OPT-01 | Sincronización no bloqueante | DONE | `CatalogSyncWorker` es `CoroutineWorker`; WorkManager; UI muestra banner pequeño `DashboardScreen.kt:747` | No tocar salvo repro distinto. |
| FIL-01 | Filtro Factura | PARTIAL | Backend `search` ya incluye `codFactura`; Android no expone filtro | TASK-08/TASK-09. |
| FIL-02 | Filtro Usuario | MISSING | `factura.usuario_creacion` y `usuarios` existen | TASK-08/TASK-09. |
| FIL-03 | Filtro Sucursal | MISSING | `factura.id_sucursal` y `sucursal` existen | TASK-08/TASK-09. |
| FIL-04 | Fecha creación desde/hasta | PARTIAL | Backend recibe rango pero filtra `fechaFactura` sólo cuando vienen ambas fechas (`FacturasRepository.kt:88-92`); Android no lo expone | TASK-08/TASK-09. |
| FIL-05 | Sumatorios dinámicos por filtro | BUG/PARTIAL | `/facturas/resumen` ignora filtros; Android suma sólo lista cargada (`HistoryScreen.kt:210`) y repo limita a 200 (`ApiTransactionRepository.kt:23`) | TASK-08/TASK-09. |

---

# 4. Hallazgos críticos

## H1 — CXC se pierde antes de persistir

`prepareRequestWithWarehouses()` normaliza los pagos. `normalizeTipoMovimiento()` no incluye `CXC` entre los códigos permitidos y lo convierte a `OT`. Más adelante `insertFacturaDetalleFormaPago()` intenta leer `CXC`, por lo que esa rama queda neutralizada por el propio pipeline.

**Decisión:** `CXC` pasa a ser un código canónico permitido. No confundir con `CR`/`CREDITO`, que en el proyecto también pueden representar tarjeta/crédito bancario.

## H2 — `formapago` afecta cierre de caja

`CajaRepository.kt:497-502` y `:591-596` excluyen de ciertas validaciones las facturas cuyo `formapago == "credito"`. Por lo tanto, persistir correctamente `contado`/`credito` no es cosmético.

## H3 — Cliente ya tiene configuración de crédito, pero no sale por API

`clientes.permitecredito`, `clientes.limite` y `clientes.dias` existen. El TODO sólo obliga a usar `permitecredito` y `dias`; **no implementar límite de crédito** hasta que exista una regla funcional explícita.

## H4 — NC parcial PA + `cod_estatus=3` entra en conflicto con la lista actual

`listEligibleInvoices()` filtra `factura.cod_estatus != 3`. Si Panamá obliga a poner `3` desde una devolución parcial de $0.01, una segunda devolución legítima dejaría de aparecer aunque queden unidades disponibles.

**Decisión:** en PA, elegibilidad se determina por saldo/cantidad devolvible, no por `cod_estatus != 3`. VE conserva el comportamiento actual.

## H5 — El flujo PAC de NC no puede ser una única transacción SQL

Hoy `CreditNoteService.create()` envuelve todo `repository.create()` en `dbQuery`. Llamar al PAC dentro de esa transacción mantendría locks/conexión durante I/O externo y es incorrecto.

**Decisión:** PA usa preparación/reserva corta -> PAC fuera de transacción -> finalización corta. VE conserva create + confirmación HKA legacy.

## H6 — Se puede reutilizar `cod_devolucion_fiscal` como estado fiscal durable sin migración inmediata

El repositorio ya usa `00000000` como pendiente. Para evitar una tabla nueva se puede extender el dominio fiscal a estados internos `PENDIENTE`, `INCIERTA`, `RECHAZADA`, `CONFIRMADA`, manteniendo `cod_devolucion_fiscal` como valor durable de transición hasta que exista número fiscal real. Las devoluciones `RECHAZADA` no consumen cantidad; `PENDIENTE/INCIERTA/CONFIRMADA` sí reservan/consumen para evitar doble NC.

No se agrega columna sólo para esto.

## H7 — Sync ya está en background

No convertir WorkManager en otra abstracción. El TODO está satisfecho según el código actual. Si el usuario aún ve congelamiento, se necesita un trace/repro que identifique otro camino síncrono.

## H8 — Ítems manuales no tienen identidad persistible

La UI crea IDs `manual_<timestamp>`, mientras `SaleItemInput.idItem` es `Int` y la persistencia espera un `items.id_item` real. Quitar el bloqueo generaría FK/datos inconsistentes.

## H9 — Combos no existen en el modelo entregado

No hay tabla de composición, flag de item combo ni tabla auxiliar. No hay forma segura de saber qué sub-items descontar.

---

# 5. Decisiones arquitectónicas

## ADR-01 — Una sola fuente de verdad para crédito

El backend decide si la operación es a crédito con la siguiente regla canónica:

`isCredit = factura.formaPago == "credito" OR pago CXC explícito OR saldoPendiente > 0`

El frontend define la intención y restringe UI, pero el backend vuelve a validar todo.

Si `isCredit`:

- cliente debe tener `permitecredito = true`;
- `fecha_vencimiento = BusinessClock.todayForCountry(countryCode) + clientes.dias`;
- `factura.formapago = "credito"`;
- `caja_nueva.status = Pendiente` cuando `saldoCxc > 0`, si no `Pagada`;
- `CXC` se persiste como `CXC`, no `OT`.

Si no es crédito:

- `factura.formapago = "contado"`;
- saldo debe ser cero;
- no se acepta un pago `CXC` oculto.

## ADR-02 — Coherencia monetaria de venta

Backend calcula con `BigDecimal`:

- `totalPagos = sum(pagos.monto)`
- `totalCxc = sum(pagos tipo CXC)`
- `totalPagadoReal = totalPagos - totalCxc`
- `saldoEsperado = max(granTotal - totalPagadoReal, 0)`

Invariantes:

- contado: `totalCxc == 0 && saldoEsperado == 0`;
- crédito: el saldo declarado y/o CXC debe cuadrar con `saldoEsperado` a escala 2;
- el backend nunca acepta saldo negativo ni pagos reales por encima del total salvo el caso de efectivo donde el exceso corresponde al cambio ya modelado.

## ADR-03 — NC Panamá por estados

PA:

1. preparar/reservar NC en DB;
2. commit;
3. construir/enviar PAC fuera de transacción;
4. si PAC confirma: finalizar efectos comerciales en una segunda transacción;
5. si PAC rechaza: marcar rechazada y liberar reserva lógica;
6. si timeout/estado ambiguo: marcar `INCIERTA`, no reenviar automáticamente sin reconciliación.

VE mantiene el flujo HKA actual y `ProcessCreditNoteFiscalUseCase`.

## ADR-04 — Forma de pago para reverso NC

Resolver el ID desde `caja_forma_pago` por `siglas = "NC"` y `activo = 1`. No usar `30` como fuente de verdad. Si no existe, rechazar con error de configuración explícito. El fallback 30 sólo puede mantenerse en lectura legacy donde ya existe; no usarlo para nuevas escrituras.

## ADR-05 — Totales precargados

Cuando una orden/cuenta/draft ya trae snapshot financiero, ese snapshot es fuente de verdad. `CalculateSaleTotalsUseCase` sólo se usa para carritos construidos localmente desde cero.

## ADR-06 — Retry de red

Retry sólo para requests idempotentes (`GET`, y sólo excepciones transitorias/5xx seleccionados). No reintentar automáticamente apertura/cierre de caja ni ventas POST.

---

# 6. Plan de implementación

# FASE A — Bugs urgentes y soporte de crédito

## TASK-01 — Blindar `tipoMoneda` contra NULL legacy

**Objetivo**

Eliminar el crash `Parameter specified as non-null is null` sin propagar nulls inseguros.

**Archivos a leer**

- `amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/pos/data/CajaFormaPagoTable.kt`
- `amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/pos/data/FormasPagoRepository.kt`
- `amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/pos/domain/FormasPagoModels.kt`
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/domain/model/payment/FormaPago.kt`
- `amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos/data/remote/AppJson.kt` o configuración JSON equivalente

**Archivos a modificar**

- `FormasPagoRepository.kt`
- `FormasPagoModels.kt`
- `FormaPago.kt`
- tests nuevos/actuales de serialización/formas de pago.

**Cambio exacto**

1. En el borde DB, leer `tipo_moneda` tolerando fila legacy NULL aunque el mapping Exposed esté declarado non-null; normalizar a `""`.
2. Backend DTO: `tipoMoneda: String = ""`.
3. Android DTO/domain serializable: `tipoMoneda: String = ""`, de forma que campo ausente no falle.
4. No usar `!!` ni un valor de moneda inventado (`USD`, `BS`, etc.). Vacío significa “sin clasificación”.
5. Agregar logging seguro sólo si el proyecto ya tiene logger en esa capa; no loggear payload completo.

**Tests requeridos**

- Backend mapping con `tipo_moneda` válido.
- DTO Android deserializa JSON sin `tipo_moneda`.
- DTO Android deserializa valor vacío.
- Si el test harness permite simular fila nullable, cubrir NULL DB.

**Criterios de aceptación**

- No puede producirse NPE en constructor `FormaPagoItem` por `tipoMoneda`.
- Métodos válidos mantienen su valor original.
- Build backend + unit test Android verdes.

**Riesgo:** LOW  
**Complejidad:** S

---

## TASK-02 — Exponer y cachear configuración de crédito del cliente

**Objetivo**

Hacer disponibles `permitecredito` y `dias` desde backend hasta el cliente Android, manteniendo soporte offline.

**Archivos a modificar — backend**

- `features/clients/domain/Client.kt`
- `features/clients/data/ClientsRepository.kt`

**Archivos a modificar — Android**

- `data/remote/dto/ClientDtos.kt`
- `domain/model/Client.kt`
- `data/local/db/Entities.kt`
- `data/local/db/Mappers.kt`
- `data/repository/MappingExtensions.kt`
- `data/local/db/AppDatabase.kt`
- tests de mappings/migración.

**Cambio exacto**

1. Añadir a contrato backend:
   - `permiteCredito: Boolean = false`
   - `diasCredito: Int = 0`
2. Mapear desde `ClientsTable.permiteCredito` y `ClientsTable.dias` en `mapRowToClient` y respuestas de create/get/list.
3. Android `ClientDto`, `Client`, `ClientEntity` con los mismos campos/defaults.
4. Room: incrementar DB `15 -> 16` y crear `MIGRATION_15_16`:
   - `ALTER TABLE clients ADD COLUMN permiteCredito INTEGER NOT NULL DEFAULT 0`
   - `ALTER TABLE clients ADD COLUMN diasCredito INTEGER NOT NULL DEFAULT 0`
5. Registrar la migración en el builder existente, siguiendo el patrón de `AppDatabase`.
6. No exponer/usar `limite` en esta TASK.

**Tests requeridos**

- backend Client mapping crédito true/false + días;
- ClientDto -> Entity -> Domain conserva ambos campos;
- Room migration 15->16 si existe infraestructura de migration tests.

**Criterios de aceptación**

- Cliente online y cacheado devuelve misma configuración.
- Clientes legacy quedan `false/0`.

**Riesgo:** MEDIUM  
**Complejidad:** M

**Dependencias:** TASK-01 no estricta; ejecutar antes de TASK-04.

---

## TASK-03 — Implementar semántica CxC en backend de venta

**Objetivo**

Convertir el flujo existente de venta en un flujo que soporte contado y crédito sin duplicar endpoint/repositorio.

**Archivos a leer/modificar**

- `features/sales/domain/ProcessSaleModels.kt`
- `features/sales/data/ProcessSaleTransactionalRepository.kt`
- `features/sales/data/SalesTables.kt`
- `features/clients/data/ClientsTable.kt`
- `features/caja/domain/CajaModels.kt`
- tests nuevos bajo `features/sales`.

**Cambio exacto**

1. Preservar `CXC` en `normalizeTipoMovimiento()`.
2. Antes de persistir, construir `CreditDecision` interno (puede ser data class privada; no crear feature nueva):
   - formaPago solicitada normalizada;
   - total general BigDecimal;
   - pagos CXC;
   - pagos reales;
   - saldo esperado;
   - `isCredit` según ADR-01.
3. Backend revalida coherencia de pagos según ADR-02.
4. Si `isCredit`, cargar `ClientsTable` por `request.factura.idCliente` dentro de la misma transacción:
   - inexistente -> 400;
   - `permiteCredito=false` -> 400 con mensaje funcional;
   - `dias < 0` -> tratar como configuración inválida; `0` vence hoy.
5. `insertFactura()` deja de hardcodear `"contado"` y usa resultado canónico.
6. `fechaVencimientoFactura`:
   - crédito: fecha operativa + `clientes.dias`;
   - contado: conservar comportamiento legacy actual o fecha que el esquema requiera, pero **no** usar días cliente.
7. `insertFacturaDetalleFormaPago()`:
   - persiste `totalizarMontoCxc` real;
   - persiste `totalizarSaldoPendiente` real;
   - para crédito usa `fechaVencimiento` con días cliente;
   - no permite que CXC termine en `totalizarMontoOtros`.
8. `insertCajaEntries()`:
   - `status=Pendiente` si saldo CxC > 0;
   - `status=Pagada` si saldo == 0;
   - mantener `idFactura` y demás vínculos existentes.
9. No escribir `cxc_edocuenta`.
10. No cambiar HKA/FE ni inventario en esta TASK.

**Tests requeridos**

- contado completo -> `formapago=contado`, caja Pagada;
- CXC explícito -> crédito + caja Pendiente;
- `formaPago=credito` + CXC -> crédito;
- saldo positivo -> crédito;
- cliente no permite -> rechazo;
- días=0 -> vence hoy;
- días=30 -> fecha +30;
- CXC ya no se convierte en OT;
- incoherencia saldo/pagos -> 400;
- retry mismo `idFactura` mantiene 409 existente;
- regresión VE contado.

**Criterios de aceptación**

- Los tres disparadores de crédito producen el mismo resultado canónico.
- `CXC` aparece en detalle forma pago/caja como CXC.
- `caja_nueva` refleja Pendiente/Pagada correctamente.

**Riesgo:** HIGH  
**Complejidad:** L

**Dependencias:** ninguna de Android; TASK-02 sólo si se pretende probar end-to-end cliente.

---

## TASK-04 — Condición de pago Contado/Crédito en Android

**Objetivo**

Evitar CxC en contado y permitir crédito únicamente cuando el cliente lo soporte.

**Archivos a modificar**

- `ui/payment/PaymentState.kt`
- `ui/payment/PaymentViewModel.kt`
- `ui/payment/PaymentScreen.kt`
- `domain/usecase/payment/ValidatePaymentUseCase.kt`
- `domain/usecase/payment/PrepareSaleUseCase.kt`
- `domain/usecase/payment/ExecutePaymentFlowUseCase.kt`
- tests `PaymentViewModelTest`, `ValidatePaymentUseCaseTest`, `BuildPaymentDetailsUseCaseTest`, `ExecutePaymentFlowUseCaseTest`.

**Cambio exacto**

1. Crear enum sencillo `PaymentCondition { CONTADO, CREDITO }` en la capa payment/domain apropiada.
2. Default `CONTADO`.
3. Mostrar selector Contado/Crédito en la pantalla de pago.
4. `CXC` se identifica **sólo** por `siglas == "CXC"`; no tratar cualquier `CR/CREDITO` como CxC porque pueden ser tarjetas.
5. Contado:
   - ocultar/deshabilitar `CXC`;
   - limpiar monto CXC al cambiar desde Crédito a Contado;
   - exigir pago suficiente actual.
6. Crédito:
   - sólo habilitar si cliente seleccionado tiene `permiteCredito=true`;
   - si no, mostrar error y mantener Contado;
   - CXC puede tomar exactamente el monto pendiente mediante “exacto”; el total de asignaciones (pagos reales + CXC) debe cubrir el total.
7. `PrepareSaleUseCase.buildInvoice()` usa condición para `formaPago`.
8. `buildPaymentSummary()` calcula `totalizarSaldoPendiente` desde CXC/pendiente y deja de fijar cero.
9. Backend sigue siendo autoridad final; no eliminar sus validaciones.

**Tests requeridos**

- Contado no lista CXC.
- Cambiar Crédito -> Contado limpia CXC.
- Cliente sin permiso no puede seleccionar Crédito.
- Cliente con permiso sí.
- crédito 100% CXC.
- abono parcial + CXC resto.
- tarjeta de crédito normal no activa condición CxC.

**Criterios de aceptación**

- UI nunca envía CXC desde condición Contado.
- Payload refleja `formaPago=credito` y saldo correcto.

**Riesgo:** HIGH  
**Complejidad:** L

**Dependencias:** TASK-02 y TASK-03.

---

## TASK-05 — Atomicidad de lotes en venta

**Objetivo**

Evitar que dos cajas consuman el mismo lote simultáneamente.

**Archivos a modificar**

- `features/sales/data/ProcessSaleTransactionalRepository.kt`
- tests repository sales.

**Cambio exacto**

En `processLotTracking()` reemplazar read-modify-write vulnerable por update condicional:

- condición `id_lote_item = X AND disponibilidad >= cantidad`;
- decremento y acumuladores en la misma sentencia/transacción cuando Exposed lo permita;
- si `updated != 1`, lanzar `InsufficientStockException` y rollback completo.

No cambiar el modelo `factura_detalle_producto_lote.cantidad` (actualmente integer) en esta TASK.

**Tests**

- lote suficiente;
- lote insuficiente -> rollback;
- dos intentos sobre disponibilidad límite -> uno solo triunfa.

**Riesgo:** MEDIUM  
**Complejidad:** M

---

# FASE B — Bugs de app y red

## TASK-06 — Retry seguro para lecturas de caja

**Objetivo**

Recuperar `Connection reset` transitorio sin duplicar operaciones de caja.

**Archivos a leer/modificar**

- `data/remote/ApiClient.kt`
- `data/remote/api/CajaApi.kt` y su implementación
- `ui/dashboard/DashboardCajaCoordinator.kt`
- tests del API/retry.

**Cambio exacto**

1. Usar `HttpRequestRetry` de Ktor si la versión instalada lo soporta; si no, helper pequeño en capa API.
2. Máximo 3 intentos totales para **GET** idempotentes.
3. Backoff exponencial corto con jitter.
4. Retry para excepciones de conexión/reset/socket timeout y HTTP 502/503/504.
5. No retry automático para POST abrir/cerrar caja, venta, nota de crédito o mutaciones.
6. Mantener timeouts actuales inicialmente (`60s/15s/60s`); `Connection reset` no se arregla aumentando ciegamente timeout.
7. Error final conserva mensaje útil para UI.

**Tests**

- GET falla una vez por conexión y luego éxito -> 2 llamadas.
- GET agota 3 -> failure.
- POST no se reintenta.

**Riesgo:** MEDIUM  
**Complejidad:** M

---

## TASK-07 — Respetar snapshots financieros de órdenes precargadas

**Objetivo**

Evitar que cuentas de mesa/pedidos/drafts cambien el monto al entrar al checkout.

**Archivos a modificar**

- `domain/repository/TableAccountPaymentHolder.kt`
- `domain/usecase/payment/ExecutePaymentFlowUseCase.kt`
- `domain/usecase/payment/PrepareSaleUseCase.kt`
- `domain/model/DraftInvoice.kt`
- `domain/usecase/cart/SaveDraftInvoiceUseCase.kt`
- `data/local/db/DraftInvoiceEntity.kt`
- `data/repository/RoomDraftInvoiceRepository.kt`
- `data/repository/JsonDraftInvoiceRestorer.kt`
- `data/local/db/AppDatabase.kt`
- tests de table account + drafts + payment preparation.

**Cambio exacto**

1. Introducir `SaleFinancialSnapshot` (nombre equivalente permitido) con:
   - subtotal bruto/neto según contrato actual;
   - descuento;
   - impuesto;
   - total;
   - opcional tax lines si ya vienen de origen.
2. `TableAccountPayment` construye el snapshot desde `CuentaMesaResponse.subtotal/descuento/impuesto/total` y mantiene los `SaleItemDto` ya snapshotados.
3. `ExecutePaymentFlowInput` puede transportar `financialSnapshotOverride` junto con `saleItemsOverride`.
4. `AssemblePreparedSaleUseCase`:
   - carrito normal -> `calculateSaleTotals(items)`;
   - fuente precargada -> usa snapshot autorizado y sólo valida que las líneas existan; no vuelve a convertirlo en fuente financiera.
5. Drafts:
   - persistir subtotal/descuento/impuesto/total al momento de guardar;
   - Room `16 -> 17` si TASK-02 ya fue aplicada;
   - migración con defaults seguros.
6. `JsonDraftInvoiceRestorer` no debe descartar el snapshot. Guardarlo en un holder/contexto de checkout equivalente al de mesa o devolverlo mediante el use case de restore.
7. No resolver discrepancias ocultándolas sólo en UI: el `ProcessSaleRequest` debe usar el mismo snapshot.

**Tests**

- Cuenta con total 10.01 y líneas susceptibles a rounding -> request sigue 10.01.
- Draft guardado/restaurado conserva exactamente snapshot.
- Carrito nuevo sigue recalculando normalmente.
- No cambia impuestos/total entre pantalla y payload.

**Riesgo:** HIGH  
**Complejidad:** L

**Dependencias:** si TASK-02 agregó Room v16, usar migración 16->17. Si el orden cambia, renumerar migración sin saltos.

---

# FASE C — Historial y filtros

## TASK-08 — Backend de filtros de facturas y resumen filtrado

**Objetivo**

Tener un contrato backend único para lista y sumatorios filtrados.

**Archivos a modificar**

- `features/facturas/route/FacturasRoutes.kt`
- `features/facturas/data/FacturasRepository.kt`
- `features/facturas/data/FacturasTable.kt`
- puede reutilizar `auth/data/UsersTable.kt` y `caja/data/CajaTable.kt::SucursalTable` sin duplicarlas
- DTOs de facturas si hace falta respuesta de opciones/resumen.

**Contrato**

`GET /facturas` y `GET /facturas/resumen` aceptan los mismos parámetros:

- `search`
- `usuario`
- `sucursal_id`
- `fecha_inicio`
- `fecha_fin`
- `estatus`

Agregar `GET /facturas/filtros` sólo si la UI necesita catálogo remoto. Respuesta mínima:

- usuarios activos relevantes (`usuario`);
- sucursales (`id`, nombre/código).

**Cambio exacto**

1. Extraer un builder/helper privado de predicados para no duplicar semántica lista/resumen.
2. Fecha:
   - sólo inicio -> `>=`;
   - sólo fin -> `<=`;
   - ambas -> rango inclusivo;
   - usar el campo solicitado por negocio: **fecha de creación**. Como `fecha_creacion` está mapeada como String, respetar formato real del proyecto y probar límites de día; si no puede filtrarse de forma confiable con ese formato, documentar y usar expresión SQL compatible sin migrar columna.
3. `usuario` filtra `factura.usuario_creacion`.
4. `sucursal_id` filtra `factura.id_sucursal`.
5. `search` conserva búsqueda por factura/cliente/estatus.
6. `/resumen` aplica exactamente los mismos filtros.
7. Evitar que el resumen dependa de la página actual. Puede seguir calculando en backend; preferir agregados SQL donde no rompa multimoneda.
8. No alterar semántica VE/PA de moneda.

**Tests**

- search por código factura;
- usuario;
- sucursal;
- sólo desde;
- sólo hasta;
- rango;
- combinación;
- resumen y lista usan mismo universo.

**Riesgo:** MEDIUM  
**Complejidad:** L

---

## TASK-09 — UI Android de historial con filtros y sumatorio backend

**Objetivo**

Exponer todos los filtros y dejar de sumar sólo las 200 facturas descargadas.

**Archivos a modificar**

- `domain/repository/InvoiceHistoryRepository.kt`
- `data/remote/api/SalesApi.kt`
- `data/remote/api/SalesApiImpl.kt`
- `data/repository/ApiTransactionRepository.kt`
- DTOs `domain/model/sales/*`
- `ui/history/HistoryState.kt`
- `ui/history/HistoryViewModel.kt`
- `ui/history/HistoryScreen.kt`
- tests de ViewModel/repository/API serialization.

**Cambio exacto**

1. Crear `InvoiceHistoryFilter` con search, usuario, sucursalId, fechaInicio, fechaFin y estatus.
2. `SalesApi.getFacturas` recibe filtro + paginación.
3. Crear método para `/facturas/resumen` y opciones `/facturas/filtros` si TASK-08 lo expuso.
4. `HistoryViewModel` mantiene filtro en state y aplica con debounce sólo al texto de búsqueda; selectores aplican inmediatamente o con botón “Aplicar”, pero no mezclar comportamientos arbitrariamente.
5. UI:
   - búsqueda por factura;
   - selector Usuario;
   - selector Sucursal;
   - Desde/Hasta;
   - botón limpiar.
6. Summary/Header usa respuesta `/resumen` del filtro, **no** `transactions.sumOf`.
7. Retirar dependencia del hardcode `limit=200` como fuente de verdad. La lista puede paginar; el total mostrado viene del backend.

**Tests**

- estado inicial;
- aplicar/limpiar filtros;
- parámetros enviados correctamente;
- el total del header es el resumen filtrado aunque sólo haya una página cargada.

**Riesgo:** MEDIUM  
**Complejidad:** L

**Dependencias:** TASK-08.

---

# FASE D — Nota de crédito: consistencia comercial antes del PAC

## TASK-10 — Endurecer sobre-devolución concurrente y elegibilidad PA

**Objetivo**

Garantizar que dos NC simultáneas no acrediten más que la factura y permitir múltiples devoluciones PA aun cuando la factura global quede `cod_estatus=3` desde la primera.

**Archivos a modificar**

- `features/creditnotes/application/CreditNoteService.kt`
- `features/creditnotes/data/CreditNoteRepository.kt`
- tests nuevos `CreditNoteRepositoryTest`/`CreditNoteServiceTest`.

**Cambio exacto**

1. `listEligibleInvoices` recibe `countryCode`.
2. VE conserva `cod_estatus != 3`.
3. PA no usa ese estado como exclusión; carga candidatas y sólo devuelve facturas con cantidad/monto aún acreditable.
4. Para create/reserva, bloquear las líneas de factura o la factura origen dentro de la transacción mediante mecanismo SQL/Exposed equivalente a `SELECT ... FOR UPDATE` antes de calcular cantidad previamente devuelta.
5. Conteo de devuelto debe distinguir estados fiscales PA una vez TASK-14 los introduzca:
   - CONFIRMADA/PENDIENTE/INCIERTA consumen o reservan;
   - RECHAZADA no consume.
6. Mantener validación por `idDetalleFactura` único.
7. No cambiar todavía dinero/caja.

**Tests**

- dos devoluciones secuenciales;
- devolución exactamente restante;
- > restante -> 400;
- PA cod_estatus 3 parcial todavía elegible si queda saldo;
- VE anulada sigue no elegible;
- test concurrente/transactional si el harness DB lo permite.

**Riesgo:** HIGH  
**Complejidad:** L

---

## TASK-11 — Prorratear descuento global y cuadrar impuesto de NC

**Objetivo**

Hacer que una devolución parcial conserve exactamente la economía de la factura original.

**Archivos a modificar**

- `features/creditnotes/data/CreditNoteTables.kt`
- `features/creditnotes/data/CreditNoteRepository.kt`
- tests financieros NC.

**Cambio exacto**

1. Mapear en `CreditNoteFacturaTable` las columnas existentes de factura necesarias:
   - `totalizar_pdescuento_global`
   - `totalizar_descuento_global`
   - `totalizar_total_operacion`/base equivalente si se requiere para el prorrateo.
   No crear columnas DB; ya existen en `SalesFacturaTable`.
2. Extender `InvoiceHeader` con esos valores.
3. Calcular la porción de descuento global de las líneas devueltas usando `BigDecimal` y una base proporcional estable.
4. La última porción de una devolución total/restante absorbe el residuo de centavos para garantizar invariantes.
5. Recalcular subtotal neto, impuesto y total de la NC después de aplicar descuento global.
6. Persistir `factura_devolucion.descuento_global`, `pdescuento_global` y, para PA, `descuento_global_venta` según correspondencia real.
7. Invariantes obligatorias:
   - devolución total = total original exacto a centavos;
   - suma de devoluciones parciales que agotan todas las líneas = total original exacto;
   - suma de descuentos globales acreditados = descuento global original;
   - impuesto nunca se inventa por diferencia de floating point.

**No asumir**

No cambiar fórmula fiscal fuera de lo que puede reconstruirse de snapshots originales. Si fixtures reales contradicen la base propuesta, detener y reportar evidencia.

**Tests**

- sin descuento;
- 10% global varias líneas con distintas tasas;
- parcial de una línea;
- múltiples parciales hasta 100%;
- residuo de 0.01;
- exento + gravado.

**Riesgo:** HIGH  
**Complejidad:** L

**Dependencias:** TASK-10 recomendada.

---

## TASK-12 — Reversión de caja PA y estatus global

**Objetivo**

Aplicar las reglas PA sin tocar la semántica legacy VE.

**Archivos a modificar**

- `features/creditnotes/data/CreditNoteRepository.kt`
- reutilizar `features/pos/data/CajaFormaPagoTable.kt`
- tests NC/caja.

**Cambio exacto**

1. Extraer resolución de forma pago NC:
   - `CajaFormaPagoTable.siglas == "NC"` case-insensitive/normalizada según Exposed posible;
   - `activo=1`;
   - si no existe -> `CreditNoteValidationException("No existe forma de pago activa para Nota de Crédito (NC)")`.
2. Eliminar `CREDIT_NOTE_PAYMENT_FORM_ID=30` de nuevas escrituras.
3. PA, al finalizar cualquier NC confirmada:
   - `factura.cod_estatus=3`, aunque sea parcial.
4. Si la devolución agota todo:
   - usar `cancelInvoiceAndOriginalCash()` existente;
   - `caja_nueva=Anulada`, recibos `AN`.
5. Si parcial:
   - insertar `caja_nueva_detalle.monto` y `monto_original` como **negativos**;
   - misma transacción/original caja;
   - reducir `caja_nueva.monto` sin caer bajo cero;
   - asociar `id_nota_credito`.
6. VE conserva comportamiento actual. No forzar `cod_estatus=3` en NC parcial VE salvo flujo legacy que ya lo haga.
7. Abono/reintegro/inventario siguen sin cambios funcionales.

**Tests**

- PA $0.01 parcial -> factura estado 3 y reverso -0.01;
- segunda parcial permitida por TASK-10;
- total -> caja anulada/recibo AN;
- falta forma `NC` -> error configuración;
- VE parcial no adopta la regla PA.

**Riesgo:** HIGH  
**Complejidad:** L

---

# FASE E — PAC Panamá para Nota de Crédito

## TASK-13 — Contexto y builder PAC de NC reutilizando infraestructura FE

**Objetivo**

Construir el documento electrónico de NC sin duplicar el PAC.

**Archivos a leer/modificar**

- `features/electronicinvoice/pac/thefactory/TheFactoryHkaPayloadDtos.kt`
- `features/electronicinvoice/pac/thefactory/TheFactoryHkaPayloadBuilder.kt`
- `features/electronicinvoice/data/ElectronicInvoiceRepository.kt`
- `features/electronicinvoice/data/ElectronicInvoiceTables.kt`
- `features/creditnotes/data/CreditNoteRepository.kt`
- crear, sólo si mejora separación, `TheFactoryHkaCreditNotePayloadBuilder.kt`
- crear contexto de dominio PA NC dentro de `electronicinvoice` o `creditnotes/application`, no un nuevo feature paralelo.

**Cambio exacto**

1. Reutilizar `PanamaElectronicInvoiceClient` y DTO wrapper existente.
2. Builder de NC produce `tipoDocumento="04"`.
3. Cargar factura original PA incluyendo:
   - CUFE original;
   - fecha original;
   - número fiscal;
   - receptor/emisor/config FE;
   - líneas ya calculadas por TASK-11.
4. Poblar `listaDocsFiscalReferenciados` con `TheFactoryHkaDocFiscalRef` usando CUFE/fecha original cuando la factura fue electrónica.
5. Reutilizar mappings existentes para emisor/receptor/tasas/formas de pago; no copiar 300 líneas si pueden extraerse helpers internos sin cambiar comportamiento de factura.
6. El número fiscal de la NC debe provenir del correlativo fiscal definido por la configuración PA existente; no usar `cod_devolucion` interno como sustituto salvo evidencia en configuración.

**STOP CONDITION — BLOCKER-PAC-01**

Si el código actual no contiene suficiente información para determinar campos obligatorios específicos de NC The Factory HKA/DGI o la regla del `numeroDocumentoFiscal`, **no inventarlos**. Implementar hasta el builder verificable y reportar exactamente los campos faltantes del contrato PAC.

**Tests**

- golden JSON de NC tipo 04;
- referencia a CUFE original;
- totales iguales a TASK-11;
- factura PA existente sigue generando exactamente el mismo payload anterior.

**Riesgo:** HIGH  
**Complejidad:** L

**Dependencias:** TASK-11.

---

## TASK-14 — Orquestador PA de NC: preparar -> PAC -> finalizar

**Objetivo**

Enviar la NC al PAC de forma síncrona para el usuario sin mantener una transacción DB abierta durante la red y sin doble reversión.

**Archivos a modificar/crear**

- `features/creditnotes/application/CreditNoteService.kt`
- `features/creditnotes/data/CreditNoteRepository.kt`
- `features/creditnotes/domain/CreditNoteModels.kt`
- `features/creditnotes/route/CreditNoteRoutes.kt`
- `features/electronicinvoice/pac/PanamaElectronicInvoiceClient.kt` (reutilizar, no duplicar)
- nuevo `PanamaCreditNoteProcessor.kt` en `features/electronicinvoice/application/` si es la separación más limpia
- Koin wiring existente correspondiente.

**Estado fiscal interno**

Extender `CreditNoteFiscalStatus` a:

- `PENDIENTE`
- `INCIERTA`
- `RECHAZADA`
- `CONFIRMADA`

Mantener compatibilidad de serialización donde aplique.

**Flujo exacto PA**

### A. `prepare` — transacción corta

- lock factura/líneas;
- revalidar disponibilidad;
- calcular líneas/totales;
- generar `id_devolucion` y correlativo interno;
- insertar cabecera/detalle como `PENDIENTE`;
- **NO** modificar aún caja, factura origen, inventario, abono/reintegro;
- commit.

Las líneas pendientes actúan como reserva contra una segunda NC. Ajustar `loadInvoiceLines` para que `RECHAZADA` no cuente y PENDIENTE/INCIERTA/CONFIRMADA sí.

### B. PAC — fuera de transacción

- cargar contexto inmutable de la NC preparada;
- autenticar;
- enviar documento;
- no reusar `ProcessSaleUseCase`.

### C1. PAC confirmado — transacción corta idempotente

- lock cabecera NC;
- si ya `CONFIRMADA`, retornar respuesta existente sin repetir efectos;
- persistir CUFE, QR, `numeroDocumentoFiscal`, fecha DGI/protocolo/fecha límite;
- aplicar una sola vez:
  - `factura_detalle.anulado`;
  - estatus global PA (TASK-12);
  - caja total/parcial (TASK-12);
  - inventario si `devolverStock`;
  - abono/reintegro según request;
- marcar CONFIRMADA;
- commit.

### C2. PAC rechazado

- transacción corta;
- marcar RECHAZADA;
- no aplicar efectos comerciales;
- las cantidades vuelven a estar disponibles porque las notas RECHAZADA no cuentan en `loadInvoiceLines`.

### C3. Timeout/resultado ambiguo

- marcar INCIERTA;
- no aplicar efectos comerciales;
- mantener reserva;
- responder estado explícito; no enviar automáticamente otra NC que pudiera duplicar el documento fiscal.

**VE**

`CreditNoteService.create()`/confirmación HKA legacy debe conservar su secuencia actual. El branch PA se realiza por `countryCode`, no por nuevo flag global.

**Tests**

- PA accepted -> efectos una sola vez;
- reintentar finalización -> idempotente;
- rejected -> sin inventario/caja/abono;
- timeout -> INCIERTA y reserva;
- dos requests simultáneos no exceden línea;
- VE usa flujo antiguo;
- failure DB después de PAC accepted debe dejar diagnóstico recuperable y no reenviar ciegamente.

**Riesgo:** CRITICAL  
**Complejidad:** XL

**Dependencias:** TASK-10, TASK-11, TASK-12, TASK-13.

---

## TASK-15 — Persistir resguardo PDF de NC en storage existente

**Objetivo**

Guardar el CAFE/PDF autorizado sin introducir una nueva variable de entorno.

**Archivos a leer/modificar**

- `features/electronicinvoice/pac/PanamaElectronicInvoiceClient.kt`
- `TheFactoryHkaRestClient.kt`
- wiring que ya lee `DATA_BASE_PATH` en `Routing.kt`
- crear un pequeño port/storage de documentos sólo si evita acoplar filesystem al processor.

**Cambio exacto**

1. Reutilizar `pacClient.downloadPdf(baseUrl, token, cufe)`.
2. Reutilizar `DATA_BASE_PATH` ya existente; no crear env nuevo.
3. Ruta determinista sugerida:
   - `{DATA_BASE_PATH}/{companyDb}/documentos_fiscales/notas_credito/{idDevolucion}/{numeroDocumentoFiscal}.pdf`
4. Crear directorios si no existen.
5. Escritura atómica (`tmp` -> rename/move) para evitar archivos corruptos.
6. Fallo de descarga después de PAC confirmado **no revierte la NC fiscal**; registrar error y permitir retry de resguardo.
7. No guardar PAN, credenciales o tokens en nombre/log.

**XML — BLOCKER-PAC-XML-01**

El port/repo entregado sólo define `/api/DescargaPDF`. No existe endpoint ni campo XML en la respuesta actual. No inventar `/DescargaXML`.

Cuando se proporcione el contrato XML:

- agregar método al mismo `PanamaElectronicInvoiceClient`;
- implementar en `TheFactoryHkaRestClient`;
- guardar junto al PDF con `.xml`.

**Tests**

- PDF guardado con bytes esperados;
- directorio inexistente;
- retry de almacenamiento;
- path traversal imposible usando ID/número sanitizados.

**Riesgo:** MEDIUM  
**Complejidad:** M

**Dependencias:** TASK-14.

---

# 7. Requerimientos bloqueados que NO debe inventar el agente

## BLOCKER-COMBO-01 — Modelo de composición de combos

Para implementar `CRED-10` falta, como mínimo:

- cómo se identifica un item tipo Combo;
- tabla/endpoint que define `combo -> subitems + cantidades`;
- almacén/lote de cada subitem;
- si el precio/tax comercial pertenece sólo al padre;
- nombre real de la tabla de trazabilidad de componentes vendidos.

`rg` sobre el repo no encuentra `combo`, `factura_detalle_producto_combo` ni modelo equivalente.

**Hasta recibir esta información:** no crear tablas ni descontar componentes por heurística.

## BLOCKER-MANUAL-01 — Ítems manuales/no sincronizados

Estado actual:

- UI genera IDs `manual_<timestamp>`.
- `PrepareSaleUseCase` rechaza IDs no numéricos.
- backend `SaleItemInput.idItem` es `Int` y la factura/inventario esperan un item persistido.

Hace falta decidir uno de estos contratos:

A. un “ítem manual” primero se crea/sincroniza en backend y recibe `id_item` real; o  
B. factura permite líneas libres sin FK/item/inventario; esto requiere contrato y esquema explícitos.

**No eliminar el bloqueo hasta que el negocio elija A o B.**

## BLOCKER-PAC-01 — Especificación oficial NC Panamá The Factory HKA

El código confirma que el payload soporta documento fiscal referenciado, pero no contiene documentación suficiente para validar todos los campos obligatorios de una NC tipo 04 ni su correlativo fiscal.

Se necesita la especificación oficial/ejemplo aceptado del PAC para NC Panamá antes de inventar valores.

## BLOCKER-PAC-XML-01 — Descarga XML

No existe contrato/endpoint XML en el port ni adapter actual. Se necesita endpoint, método, body y formato de respuesta real.

---

# 8. No-tareas: cosas que ya están resueltas

El agente no debe gastar tokens ni modificar código por estos puntos salvo fallo de test/repro concreto:

1. Crear endpoint de venta: ya existe.
2. Crear mecanismo de idempotencia básico de factura: ya existe por `idFactura`.
3. Crear inserción de factura/detalle/caja: ya existe.
4. Crear Kardex de venta/devolución desde cero: ya existe.
5. Crear devolución de stock NC desde cero: ya existe.
6. Crear abono NC desde cero: ya existe.
7. Crear reintegro NC desde cero: ya existe.
8. Crear arquitectura FE Panamá desde cero: ya existe Strategy/Factory/Port/Adapter/Builder.
9. Crear un feature flag país nuevo: ya existe `countryCode` + `useHka20` + factory FE.
10. Refactorizar `Sincronizando datos...` a WorkManager: ya está en WorkManager/CoroutineWorker.
11. Insertar `cxc_edocuenta`: expresamente prohibido y no es necesario.

---

# 9. Matriz de pruebas finales

## Venta contado

- VE + HKA20 físico: factura/impresión sin regresión.
- VE digital: FE actual sin regresión.
- PA: FE actual sin regresión.
- pago efectivo exacto.
- pago efectivo con cambio.
- tarjeta/otro.

## Venta crédito

- 100% CXC.
- abono inicial + CXC restante.
- trigger `formaPago=credito`.
- trigger `saldoPendiente>0`.
- cliente `permitecredito=false`.
- cliente días=0.
- cliente días>0.
- contado intenta CXC -> bloqueado frontend y backend.
- stock insuficiente -> rollback completo.
- lote insuficiente -> rollback completo.
- retry mismo `idFactura` -> no duplica.
- caja Pendiente mientras haya saldo; Pagada sin saldo.

## Nota de crédito PA

- $0.01 parcial.
- segunda parcial sobre factura ya `cod_estatus=3`.
- agotar línea exactamente -> `anulado=1`.
- exceder disponibilidad -> 400.
- dos NC concurrentes sobre última unidad -> máximo una la consume.
- descuento global.
- mezcla exento/gravado.
- devolución stock=false.
- devolución stock=true.
- lote.
- ABONO.
- REINTEGRO.
- total -> caja Anulada + recibo AN.
- parcial -> detalle negativo.
- forma `NC` inexistente -> error configuración.
- PAC accepted.
- PAC rejected.
- PAC timeout -> INCIERTA.
- reintento de finalización -> no duplica efectos.
- CUFE/QR/número fiscal persistidos.
- PDF guardado.

## Nota de crédito VE

- creación actual.
- impresión HKA existente.
- confirmación fiscal existente.
- parcial/total legacy sin adoptar reglas PA accidentalmente.

## Android

- `tipoMoneda` ausente/vacío.
- Contado vs Crédito.
- CXC sólo en Crédito.
- table account conserva total.
- draft conserva total.
- Connection reset recuperable en GET.
- ítem manual sigue bloqueado hasta resolver contrato.
- historial combina search/usuario/sucursal/fechas.
- header usa resumen filtrado, no página local.

---

# 10. Migraciones previstas

## Android Room

### MIGRATION 15 -> 16 — Crédito de cliente

```sql
ALTER TABLE clients ADD COLUMN permiteCredito INTEGER NOT NULL DEFAULT 0;
ALTER TABLE clients ADD COLUMN diasCredito INTEGER NOT NULL DEFAULT 0;
```

### MIGRATION 16 -> 17 — Snapshot financiero de drafts

Columnas exactas a añadir según el `SaleFinancialSnapshot` implementado por TASK-07. Como mínimo:

```sql
ALTER TABLE draft_invoices ADD COLUMN subtotal REAL NOT NULL DEFAULT 0;
ALTER TABLE draft_invoices ADD COLUMN descuento REAL NOT NULL DEFAULT 0;
ALTER TABLE draft_invoices ADD COLUMN impuesto REAL NOT NULL DEFAULT 0;
```

`total` ya existe.

> Si el equipo decide almacenar minor units/decimal como String para evitar REAL, usar el patrón monetario que el proyecto ya adopte en Room. No cambiar silenciosamente la representación sin tests de migración.

## Backend/company DB

**NINGUNA migración backend obligatoria para las TASK no bloqueadas identificadas.**

Las columnas necesarias para crédito, NC PA, CUFE/QR y descuentos ya existen/mapean parcial o totalmente.

Combos no se migran hasta resolver `BLOCKER-COMBO-01`.

---

# 11. Gates por fase

## GATE-A — antes de salir de crédito

- TASK-01..05 verdes.
- backend `./gradlew test`.
- Android tests payment/client/migration.
- `./gradlew assembleDebug` Android.
- smoke test contado VE/PA.

## GATE-B — bugs app

- retry no afecta POST.
- snapshot no cambia carrito normal.

## GATE-C — historial

- lista y resumen coinciden con filtros.
- no depende de `limit=200`.

## GATE-D — NC comercial

- concurrencia y prorrateo verdes.
- regresión VE verde.

## GATE-E — PAC

No comenzar TASK-14 en entorno integrado si TASK-13 termina con `BLOCKER-PAC-01` sin resolver.

Antes de compilar/prueba real PAC:

- golden payload aprobado;
- credenciales existentes (no modificarlas);
- NC accepted/rejected simuladas en tests;
- flujo VE sin cambios.

---

# 12. Orden exacto recomendado

```text
TASK-01
-> TASK-02
-> TASK-03
-> TASK-04
-> TASK-05
-> GATE-A
-> TASK-06
-> TASK-07
-> GATE-B
-> TASK-08
-> TASK-09
-> GATE-C
-> TASK-10
-> TASK-11
-> TASK-12
-> GATE-D
-> TASK-13
-> [resolver BLOCKER-PAC-01 si aparece]
-> TASK-14
-> TASK-15
-> GATE-E
```

Los blockers de Combo e Ítems Manuales se trabajan después, cuando exista contrato funcional/esquema.

---

# 13. Handoff compacto para el agente pequeño

| TASK | Scope permitido | Tests mínimos | Stop condition |
|---|---|---|---|
| 01 | FormaPago backend + Android | serialización/mapping + build | cualquier cambio de moneda funcional no solicitado |
| 02 | Cliente crédito + Room | client mapping + migration | schema Room distinto al auditado |
| 03 | Sales backend CxC | sales tests + backend build | necesidad de tocar HKA/FE |
| 04 | Payment Android | Payment VM/use cases | backend distinto a contrato TASK-03 |
| 05 | Lotes sale backend | stock/lot tests | esquema lote distinto |
| 06 | HTTP GET retry | retry tests | plugin Ktor incompatible: usar helper local, no actualizar framework |
| 07 | Snapshot precargado | drafts/table account/payment | dato fuente no trae snapshot suficiente: reportar cuál |
| 08 | Facturas backend filtros | repository/routes | formato `fecha_creacion` incompatible: reportar muestra real |
| 09 | History Android | VM/repository | contrato TASK-08 ausente |
| 10 | NC locking/elegibilidad | NC repo/service | DB test no soporta locking: implementar y documentar test faltante |
| 11 | NC descuentos/impuestos | financial unit tests | fixtures reales contradicen fórmula: detener |
| 12 | NC caja PA | caja/NC tests | catálogo no tiene sigla NC: error funcional, no hardcodear 30 |
| 13 | Builder NC PAC | golden payload | `BLOCKER-PAC-01` |
| 14 | Orquestación PA PAC | accepted/rejected/timeout/idempotency | contrato PAC no confirmado |
| 15 | PDF storage | filesystem + fake PAC | `BLOCKER-PAC-XML-01` para XML |

---

# 14. Prompt mínimo para ejecutar cada TASK

Usar este prompt cambiando únicamente el número:

```text
Implementa únicamente TASK-XX de doc/PLAN_TODO_POS.md.

Reglas:
- Lee primero el AGENTS.md del proyecto afectado y la TASK completa.
- No ejecutes otras TASK.
- No rediseñes el plan ni amplíes scope.
- Reutiliza el código/patrones existentes indicados.
- No toques .env, secretos ni producción.
- VE/HKA20 no puede sufrir regresiones.
- Si encuentras una contradicción real o un BLOCKER del plan, DETENTE y reporta archivo/línea/evidencia; no improvises.
- Implementa los tests exigidos y ejecuta sólo los comandos mínimos de la TASK.

Al terminar responde:
TASK-XX: DONE/BLOCKED
Archivos modificados: ...
Tests: comando -> PASS/FAIL
Criterios de aceptación: ...
Blockers: ...
Siguiente TASK recomendada: ...
Y DETENTE.
```

---

# 15. Definición de “TODO completo”

El TODO se considera completo cuando:

1. TASK-01..15 aplicables están DONE y gates verdes.
2. `BLOCKER-PAC-01` y `BLOCKER-PAC-XML-01` fueron resueltos con documentación real y las partes pendientes de TASK-13/15 fueron implementadas.
3. `BLOCKER-COMBO-01` fue resuelto y se agregó una TASK específica basada en el esquema real del combo.
4. `BLOCKER-MANUAL-01` fue resuelto con una decisión de contrato y se agregó la implementación correspondiente.
5. Se ejecutó la matriz de regresión VE/PA, especialmente HKA-20 Venezuela.

Hasta entonces, el agente debe distinguir **código pendiente** de **requerimiento imposible de implementar con evidencia disponible**; no rellenar esos huecos con supuestos.
