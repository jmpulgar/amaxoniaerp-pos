# 01: Formateo y renderizado de ticket Sunmi local/offline (Panamá y Venezuela)

**What to build:**
Permitir que el POS construya un documento de impresión de ticket térmico Sunmi a partir de los datos locales de la transacción y configuración de la empresa/caja, sin requerir conexión a internet ni consultar el endpoint /api/sales/print-payload/{id} del backend. Para Panamá y Venezuela, el ticket debe reutilizar la estructura comercial estándar (cabecera, datos del cliente, productos con impuestos, totales, métodos de pago y cambio) y omitir de forma limpia los bloques fiscales del PAC (CUFE, QR, número de control fiscal) cuando estos aún no existan.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Existe un conversor/mapper local que transforma una Transaction o ProcessSaleRequestDto junto con los datos de sesión/empresa en un FacturaPrintPayloadDto.
- [ ] Los formateadores PanamaInvoiceTicketFormatter y VenezuelaInvoiceTicketFormatter generan tickets visualmente idénticos a los del backend para ventas offline sin generar excepciones por campos fiscales ausentes.
- [ ] Pruebas unitarias completas con TDD que verifiquen el formateo offline para Panamá y Venezuela con diferentes métodos de pago y desglose de impuestos.
