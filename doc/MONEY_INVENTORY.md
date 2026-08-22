# Inventario de dinero por rol (FASE 10 / TASK-100)

Clasificación de cada uso de `Double/Float/BigDecimal/Money` según el PLAN
(§TASK-100): **wire**, **display**, **cálculo** o **DB**. Regla del PLAN:
cálculo → `Money`/`BigDecimal`; wire Double puede mantenerse por
compatibilidad; conversión en boundary.

**Decisión aplicada (2026-08-22):** los sitios de cálculo que aún operan en
`Double` puro NO se migran en esta fase. Convertirlos puede alterar los
resultados numéricos que hoy viajan por el cable o se persisten, y eso es un
criterio de parada (decisión de negocio/cálculo). Quedan documentados abajo
como deuda clasificada con su ruta de migración propuesta.

## Android POS (`amaxoniaerp-pos/app/src/main/java/com/amaxonia/pos`)

### Tipos canónicos

| Rol | Tipo | Ubicación |
|---|---|---|
| Cálculo | `Money` (BigDecimal, escala 2, HALF_EVEN) | `domain/model/money/Money.kt` |
| Persistencia | minor-units `Long` + currency | `domain/model/money/MinorUnitMoney.kt`, `TransactionLogEntity.totalAmountMinor` |
| Wire | `Double` en DTOs `@Serializable` | ver tabla siguiente |
| Display | `Money.format` / `"%.2f"` legado | ver tabla display |

### Cálculo — ya BigDecimal (no tocar sin TASK funcional)

| Sitio | Qué calcula |
|---|---|
| `domain/usecase/payment/BuildSaleItemsUseCase.kt:34-58` | precio/descuento/totales por línea |
| `domain/usecase/payment/CalculateSaleTotalsUseCase.kt:24-65` | rollup subtotal/descuento/IVA/total |
| `domain/usecase/payment/BuildPaymentDetailsUseCase.kt:26-83` | pago mixto efectivo/no-efectivo/CxC |
| `domain/usecase/payment/PrepareSaleUseCase.kt:349-358` | saldo pendiente CxC (`fold Money`) |
| `domain/usecase/payment/DefaultPaymentOperation.kt:50-53` | cambio y conversión Bs (`Money.times(tasa)`) |
| `ui/payment/PaymentViewModel.kt:103-187` | restante/cambio/tendered |
| `ui/payment/PaymentScreen.kt:510` | multi-moneda UI con `BigDecimal.valueOf(tasa)` |
| `domain/usecase/PromotionUseCases.kt:34-45` | totales promoción (`money()` HALF_UP) |
| `data/printer/TheFactoryFiscalCommandBuilder.kt:255-260` | comandos fiscales (`roundToInt` sobre escala) |

### Cálculo — residual en Double (deuda clasificada; NO migrar sin decisión)

| Sitio | Riesgo de conversión |
|---|---|
| `ui/cart/CartCoordinators.kt:101`, `ui/dashboard/DashboardCartCoordinator.kt:48` | `items.sumOf { it.total }`: cambiar a BigDecimal puede alterar centavos mostrados vs wire |
| `ui/cart/CartViewModel.kt:37` | `total * tasa` en Double + `"%.2f"`: mismo riesgo en display Bs |
| `ui/payment/PaymentState.kt:142-145` | `toBs()` legado Double (convive con `toBsMoney()` BigDecimal) |
| `domain/usecase/caja/CashCloseTicketPayloadBuilder.kt:62-165` | sumas de cierre de caja en Double para ticket |

### Wire — Double retenido por compatibilidad (correcto según PLAN)

`ProcessSaleDtos.kt` (factura/items/pagos/resumen), `CajaSecuencia.kt`
(apertura→cierre), `CreditNoteDtos.kt` (montos/tasa), `CuentaDtos.kt:56`,
`PaymentSuccessPayload.kt`. La conversión ocurre en los use cases de arriba.

### DB (Room)

| Entidad | Columna | Estado |
|---|---|---|
| `TransactionLogEntity` | `totalAmountMinor Long` + `currencyCode` | canónico (MONEY-001) |
| `TransactionLogEntity:38` | `totalAmount Double` | legacy retenido |
| `DraftInvoiceEntity:19-25`, `PendingInvoiceEntity:23` | montos Double | pendiente migración minor-units (requiere schema → fuera de alcance) |
| `Entities.kt:55-56`, `PromotionEntities.kt` | precios/costos Double | catálogo, solo display/wire |

## Backend Ktor (`amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp`)

### Cálculo — ya BigDecimal (estándar dominante)

| Sitio | Redondeo |
|---|---|
| `features/sales/data/ProcessSaleMonetary.kt:20-56` | boundary wire→base (`toMoney`, `toBase`), escala 2 HALF_UP; tasa escala 8 |
| `features/sales/data/CreditDecision.kt:32-95` | saldo esperado, CxC vs pagos |
| `features/sales/data/ProcessSaleInvoiceWrites.kt:90-125` (+ DetailWrites:152-186) | totales factura a base |
| `features/creditnotes/data/CreditNoteFinancialCalc.kt` + `CreditNoteSupport.kt:109-115` | prorrateo (escala 12), IVA=total−subtotal, `divideSafe` HALF_UP |
| `features/electronicinvoice/pac/thefactory/venezuela/VenezuelaHkaPayloadBuilder.kt:130-275` | IGTF, divisa VES÷tasa (`MONEY_SCALE=2`) |
| `features/mesas/data/CuentaMesaCreacion.kt:110` | reparto cuenta mesa (**HALF_EVEN**, único caso) |

### Cálculo — residual en Double (deuda clasificada; NO migrar sin decisión)

| Sitio | Riesgo de conversión |
|---|---|
| `features/caja/domain/CajaAutoClose.kt:14-63` + readers de caja | cierre auto y sumas caja end-to-end Double |
| `features/facturas/data/FacturasResumenCalculator.kt:11-110` | resumen ventas brutas/netas/descuentos Double sin redondeo |
| `features/sales/data/ProcessSaleInvoiceDetailWrites.kt:216-269` | `PaymentBreakdown` suma pagos en Double antes de convertir a base |

### Wire — Double retenido por compatibilidad (correcto según PLAN)

`ProcessSaleModels.kt` (request/response venta), `CajaModels.kt`,
`FacturaModels.kt` (incluye `tasa: Float?`), `CreditNoteModels.kt:108`.

### DB (Exposed) — columnas monetarias

| Tabla | Tipo | Nota |
|---|---|---|
| Caja/Items/Lot/ItemStock/ElectronicInvoice/VenezuelaElectronicInvoice/ParametrosGenerales/CreditNote | `decimal(p,s)` → BigDecimal | canónico |
| `ClientsTable.kt:26` `limite` | `double(...)` | binaria excepcional |
| `SalesTables.kt:76-96`, `FacturasTable.kt:33-34` | `float("tasa")`, `float("total_ref")` | pérdida de precisión al persistir; corregir exige schema → criterio de parada |

## Resumen ejecutivo

1. Ventas (boundary+totales), notas de crédito y PAC VE: **BigDecimal** en
   ambos lados, con tests de caracterización (TASK-101).
2. Wire permanece Double/Float: permitido explícitamente por el PLAN.
3. Deuda clasificada (caja backend, resúmenes facturas, carrito Android,
   columnas float/double): migrar cambia resultados numéricos o esquema →
   requiere decisión de negocio/TASK funcional; NO se toca en FASE 10.
