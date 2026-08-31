# 02: Auto-impresión en flujo de cobro offline y reimpresión en pantalla de éxito

**What to build:**
Al completar un cobro en modo offline, la aplicación dispara la impresión física automática del ticket provisional en la Sunmi (igualando la experiencia del modo online) y permite reimprimirlo desde la pantalla de éxito (SuccessScreen) sin lanzar errores de red ni bloquear la navegación de la siguiente orden.

**Blocked by:** 01-local-sunmi-ticket-formatting

**Status:** ready-for-agent

- [ ] CompletePaymentSaleUseCase.processOffline invoca printInvoice usando el generador local de payload para Sunmi.
- [ ] PaymentGraph.printSuccessReceipt y DefaultInvoicePrintGateway.printSunmi utilizan el payload local como fallback inmediato si la venta es offline o el backend no está disponible.
- [ ] La pantalla SuccessScreen permite reimprimir el ticket offline sin errores de red.
- [ ] Pruebas unitarias de flujo de pago y reimpresión cubriendo el caso offline.
