# 04: Actualización de estado en historial y reimpresión de factura fiscal definitiva

**What to build:**
Una vez que la venta pendiente fue reenviada y certificada por el PAC en el backend (obteniendo su CUFE/QR en Panamá o Número de Documento Fiscal/Control en Venezuela), el historial de transacciones del POS refleja el estado definitivo de la factura y permite al cajero reimprimir el ticket fiscal completo con todos sus datos oficiales.

**Blocked by:** 01-local-sunmi-ticket-formatting, 02-offline-payment-autoprint-and-reprint, 03-reactive-workmanager-sync-trigger

**Status:** ready-for-agent

- [ ] El historial de transacciones (HistoryScreen) muestra el estado sincronizado de las facturas previamente offline.
- [ ] Al seleccionar una factura sincronizada en el historial, la reimpresión en Sunmi consulta el print-payload remoto con los datos fiscales finales (CUFE, QR, número de documento fiscal).
- [ ] Pruebas unitarias de integración verificando la transición de ticket provisional a ticket fiscal definitivo.
