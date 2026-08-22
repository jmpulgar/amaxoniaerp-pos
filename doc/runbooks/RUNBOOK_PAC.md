# RUNBOOK_PAC — Facturación electrónica digital (PA y VE)

## Componentes

| Pieza | Archivo |
|---|---|
| Orquestador PA | `amaxoniaerp-backend/.../electronicinvoice/application/PanamaInvoiceProcessor.kt` |
| Cliente REST PA | `pac/thefactory/TheFactoryHkaRestClient.kt` (todo `Result.failure`; timeout = IOException cruda) |
| Estrategia VE | `electronicinvoice/domain/VenezuelaInvoiceStrategy.kt` + `VenezuelaEmissionEvaluation.kt` |
| Cliente VE | `pac/thefactory/venezuela/` (`VenezuelaHkaRestClient`, payload builders) |
| Selección post-venta | `features/sales/application/ProcessSaleUseCase.kt` (`applyFeResult`) |

## Panamá (CUFE/DGI)

- Requiere `parametros_generales.tipo_facturacion >= 3`; menor ⇒
  `NotApplicable` (0 llamadas PAC).
- Pasos: auth token → build payload → sendDocument → persistencia best-effort
  (CUFE/QR) → incremento correlativo → correo best-effort.
- Códigos de fallo tipados: `INVOICE_NOT_FOUND`, `CONFIG_ERROR`, `AUTH_ERROR`,
  `BUILD_ERROR`, `SEND_ERROR`. Un rechazo de negocio del PAC o CUFE vacío se
  caracteriza como escape `FeStepFailure` (HALLAZGO documentado en
  `PanamaInvoiceProcessorTest` — NO corregir sin decisión fiscal).
- Timeout de transporte ⇒ `SEND_ERROR` determinista. PA no produce
  `Uncertain` hoy (solo VE); promoverlo es decisión fiscal pendiente.

## Venezuela digital

- `useHka20=true` omite todo el flujo digital (ver `RUNBOOK_HKA.md`).
- Resultados: Success / Failure (`AUTH_REJECTED`, código≠200, 400) /
  Uncertain (`EMISION_TIMEOUT`, `AUTH_NET_ERROR`) / AlreadyIssued
  (idempotente: retorna numeración persistida sin llamar al PAC) /
  UnsupportedDocumentType (tipoDoc ≠ '01').
- Correlativo: max(local, remoto+1); `UltimoDocumento` no concluyente cae a
  local.
- Persistencia: tuplo fiscal en tablas VE solo tras emisión sana o
  AlreadyIssued; jamás se inventan valores.

## Diagnóstico

- Logs con prefijo `[FE]` (auth, envío, respuesta PAC, CUFE truncado).
- Estado fiscal de una factura: tablas `electronic_invoice*` /
  `venezuela_electronic_invoice*`; endpoint manual de reintento para FE
  fallida (la venta nunca se revierte por fallo FE).
- Tests de referencia: `PanamaInvoiceProcessorTest`,
  `VenezuelaInvoiceStrategyTest`, `TheFactoryHkaRestClientTest`,
  `VenezuelaHkaPayloadBuilderTest`.

## Regla dura

Timeout/rechazo/incertidumbre NUNCA revierten la venta ni duplican emisión;
cualquier cambio aquí es criterio de parada (fiscal/PAC).
