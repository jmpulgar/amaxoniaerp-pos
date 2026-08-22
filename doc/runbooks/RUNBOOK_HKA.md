# RUNBOOK_HKA — Impresora fiscal Venezuela HKA-20 (POS)

## Alcance

Impresora fiscal física HKA-20 manejada por el POS (flavor BanescoVenezuela).
La facturación DIGITAL VE está cubierta en `RUNBOOK_PAC.md`.

## Selección de mecanismo

- El usuario elige impresora en Settings → `PrinterType.THE_FACTORY_HKA`.
- Por cada venta el POS envía `useHka20=true` en `/ventas/procesar`; el
  backend entonces OMITE la facturación digital (no autentica contra PAC, no
  reserva correlativo digital). Fuente: `ProcessSaleUseCase.kt` (reglas
  useHka20) y tests `ProcessSaleUseCaseSelectionTest`.
- `useHka20` viaja en el cable solo cuando es true; ausente ⇒ null ⇒ FE
  digital por defecto (compatibilidad). Test:
  `MultiCountryContractMatrixTest`.

## Flujo POS

1. Venta persistida en backend (comercial confirmada).
2. Impresión por HKA-20: comandos en
   `data/printer/TheFactoryFiscalCommandBuilder.kt` (montos como enteros a
   escala fiscal); formateo de ticket VE en
   `data/printer/venezuela/VenezuelaInvoiceTicketFormatter.kt` (usa
   `factura.numeroDocumentoFiscal` / `numeroControlThka` cuando hay FE).
3. Confirmación fiscal: PATCH `/facturas/{id}/confirmacion-fiscal` con
   `numeroDocumentoFiscal` + `impresoraSerial`
   (`ConfirmFiscalDocumentUseCase`). Si falla entra a la cola durable
   (ver `RUNBOOK_PAYMENT.md`).

## Diagnóstico

- Diagnostics device: `FiscalDeviceDiagnostics` (`HkaFiscalDeviceDiagnostics`)
  desde Settings.
- Sin numeración fiscal inventada: si el flujo FE no terminó sano los campos
  fiscales permanecen null/vacíos (invariante testeada).
- Conexión Rapid Pay: resultado manejado en `MainActivity.handleRapidPayResult`.

## Regla dura

No modificar payloads ni semántica HKA sin TASK funcional: es criterio de
parada (fiscal).
