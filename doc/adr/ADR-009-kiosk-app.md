---
status: accepted
---

# ADR-009 — App de Kiosco de autoservicio (`amaxoniaerp-kiosk`)

- Estado: Aceptado
- Fecha: 2026-10-05

## Contexto

Se requiere un kiosco de autoservicio (estilo comida rápida) donde el cliente final arma su pedido sin intervención de un cajero. La configuración visual (banners/videos del attract loop, menú, clave del kiosco) ya vive en `parametros_generales` del ERP administrativo (`banner_1..3`, `menu_1`, `clave_kiosko` en bcrypt, archivos en `pathData()/banners`).

El POS (`amaxoniaerp-pos`) es una app operada por personal autenticado, con caja, notas de crédito, fiscal y ventas offline. El kiosco es operado por público anónimo en un dispositivo desatendido.

## Decisión

1. **App Android separada** `amaxoniaerp-kiosk` (`applicationId com.amaxonia.kiosk`), hermana de `amaxoniaerp-pos` en el monorepo. No es un flavor del POS: superficie de ataque, ciclo de release y UX son distintos.
2. **Mismas reglas de arquitectura** que el POS: DI manual con composition root (ADR-001), capas `ui → domain ← data` (ADR-006), dinero según ADR-004, misma configuración JSON canónica (`contracts/README.md`).
3. **Código compartido solo por extracción demostrada**: se empieza duplicando lo mínimo (money, cliente HTTP, drivers de impresora); se extrae a un composite build `shared/` cuando la duplicación sea real en ambos proyectos.
4. **Backend**: nueva feature `features/kiosk` según arquetipos de ADR-003, bajo `/api/v1/kiosk`, aislada por empresa (ADR-002).
5. **Identidad del dispositivo, no del usuario**: el kiosco se empareja una vez con un código de un solo uso generado en el admin y recibe un token de dispositivo con rol `KIOSK`, revocable. Ese rol solo autoriza `/api/v1/kiosk/*` y assets públicos de catálogo/banners.
6. **`clave_kiosko` se verifica en el servidor** (`POST /api/v1/kiosk/unlock`, con rate limit). El hash nunca viaja al dispositivo.
7. **Modo kiosco vía Lock Task Mode** con la app como Device Owner; nada de bloqueos por overlay o interceptación de botones.
8. **Sin pedidos offline**: el catálogo y los medios se cachean, pero sin conectividad el kiosco no acepta pedidos (no hay personal que resuelva un rechazo de dominio).
9. **Banners/videos** se sirven por una ruta de assets nueva (`/api/data/{countryCode}/{companyDb}/banners/{filename}`) con `Cache-Control: immutable` (nombres únicos por `uniqid`) y soporte de Range (`PartialContent`) para video.

10. **Hardware objetivo: SUNMI K2** (24" FHD vertical, Android 13 obligatorio por `minSdk 29`, impresora Seiko 80 mm con cortador, NFC, escáner 2D). El K2 no es terminal PCI PTS: el cobro con tarjeta se hace con una terminal certificada montada en el kiosco (SUNMI serie P o la del adquirente) o, si el adquirente lo ofrece, SoftPOS NFC.
11. **Cobro en el kiosco** detrás del puerto `PaymentTerminal` (adaptadores: mock dev, Intent app-to-app, ECR LAN). La app nunca toca datos de tarjeta; solo recibe autorización, referencia, últimos 4 y marca.
12. **Cotización antes del pago**: el servidor fija totales en un `quoteId` con TTL; se cobra exactamente ese monto. `/pay` es idempotente por `orderId`.
13. **Factura fiscal** reutilizando `ProcessSaleUseCase` (PA: FE por PAC; VE: solo modalidad digital, el K2 no admite HKA20). Prioridad inicial de despliegue: **Panamá**.
14. **Ciclo de Caja 100% automático**: Cada kiosco está ligado a una `Caja` propia. El backend gestiona la secuencia de forma transparente: al primer pedido del día, si existe una secuencia abierta de una fecha anterior, se cierra automáticamente con `CajaAutoClose` (diferencias en cero) y se abre una nueva secuencia para el día actual con monto 0.
15. **Múltiples kioscos por sucursal**: La numeración diaria de pedidos incluye el prefijo del kiosco (ej. `K1-001`, `K2-001`) para evitar colisiones en cocina y pantalla de llamados.
16. **Cliente por defecto y obligatoriedad fiscal**: Se asigna por defecto el cliente predeterminado configurado en `parametros_generales` ("Consumidor Final"), pero se ofrece la pantalla de identificación para ingresar RUC/Cédula/DV cuando el cliente requiera crédito fiscal.
17. **Multimoneda**: Soporta moneda base (USD) y moneda secundaria con tasa de cambio para visualización, calculando todos los totales con `Money`/BigDecimal conforme a ADR-004.
18. **Catálogo**: Se muestran exclusivamente los departamentos con `visible_pos = 1` y precios a nivel A. Promociones quedan excluidas del MVP inicial.
19. **Combos y modificadores integrados en MVP**: Soporte nativo para grupos de modificadores (ej. tamaño, ingredientes, acompañantes, salsas) con reglas de selección mínima y máxima, permitiendo el flujo de personalización estilo McDonald's desde la primera versión.
20. **Pagado sin factura**: si el cobro se aprueba y la factura falla tras reintentos, el pedido queda `PAGADO_SIN_FACTURA`, se imprime comprobante y se concilia; jamás se recobra. Solo se anula automáticamente ante rechazo de dominio previo a facturar.
21. **Destino del pedido configurable por empresa** (`RETIRO_MOSTRADOR` | `IMPRESORA_COCINA` | `MESAS`) vía Strategy server-side.
22. **Medios** migran a `kiosco_media` (tipo, orden, duración, activo) con lectura compatible de `banner_1..3`/`menu_1`.

Plan de ejecución detallado: `doc/PLAN_KIOSK_K2.md`.

## Pendiente (requiere acción humana)

- Elección del adquirente por país y obtención de SDK/especificación ECR + terminal de prueba (bloquea el adaptador real de `PaymentTerminal`).
- Confirmación con el distribuidor del SKU K2 con Android 13 y soporte compatible con la terminal elegida.

Los fixtures en `contracts/kiosk/` se agregan cuando el contrato exista en código (los fixtures documentan contratos existentes, no aspiracionales).

## Consecuencias

- Dos apps Android con builds independientes; duplicación inicial acotada y explícita.
- El backend gana un rol de dispositivo y una superficie pública mínima que debe auditarse.
- El admin PHP necesita gestión de emparejamiento/revocación de kioscos.
