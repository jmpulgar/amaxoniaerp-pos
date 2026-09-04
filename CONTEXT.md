# Amaxonia POS/ERP

Sistema de punto de venta (POS Android en terminal SUNMI) con backend ERP en Kotlin/Ktor, multiempresa (Panamá, Venezuela), diseñado para operar **offline-first en el dispositivo** y reconciliar contra el servidor al recuperar conectividad.

## Language

### Sincronización

**Catálogo**:
Conjunto de datos maestros que el POS necesita en local para vender sin red: productos, clientes, sucursales de cliente, tipos de cliente, promociones, detalles de promoción y formas de pago.
_Avoid_: "datos maestros", "maestros", "base de datos remota"

**Bootstrap**:
Carga inicial del catálogo en un dispositivo sin datos (primera instalación, resync). Es reanudable y paginada por keyset, nunca una descarga monolítica.
_Avoid_: "sincronización inicial" ambigua, "descarga inicial", "full download"

**Sync incremental**:
Descarga de solo los cambios ocurridos desde la última posición del cursor. O(cambios), no O(catálogo).
_Avoid_: "delta" a secas, "refresh"

**Change feed**:
Registro server-side, apéndice y ordenado, de cada mutación del catálogo (alta/cambio/borrado) por empresa. Fuente única del sync incremental.
_Avoid_: "log de cambios", "cola de sincronización"

**Cursor**:
Posición del dispositivo dentro del change feed (`change_id` monótono). Vive solo en el dispositivo, persistida en la misma transacción que los datos que aplica.
_Avoid_: "offset", "página actual"

**Tombstone**:
Marca de borrado en el change feed (sin payload). Único mecanismo por el que un producto/cliente eliminado en el servidor desaparece del dispositivo.
_Avoid_: "delete flag", "borrado lógico" (reservado para conceptos del ERP legacy)

**Snapshot**:
Punto de consistencia (`change_id` límite) que fija el servidor al iniciar un bootstrap. El cursor del dispositivo se posiciona en el snapshot solo cuando el bootstrap completo termina.
_Avoid_: "snapshot bundle" (que es otra cosa: paquete precocinado, decisión diferida)

**Reconciliación**:
Comparación periódica (conteos + hash por entidad) entre servidor y dispositivo para detectar divergencias silenciosas. Dispara bootstrap por-entidad solo donde hay diferencia.
_Avoid_: "sanity check", "verificación" genérica

**Resync completo**:
Borrado controlado de las tablas de catálogo en el dispositivo + bootstrap desde cero. Nunca borra ventas pendientes, historial ni sesiones de caja.
_Avoid_: "reinstalar", "limpiar datos"

**Retención del feed**:
Días de cambios que el servidor conserva (30 por defecto). Un dispositivo con cursor más viejo recibe `410 Gone` y pasa a resync completo.
_Avoid_: "TTL", "purga" (reservado para el prune de kardex del ERP)

### Ventas offline

**Factura pendiente**:
Venta completa capturada sin red, persistida en el outbox local con clave de idempotencia propia (UUID), que sube al servidor al reconectar. El servidor le asigna numeración definitiva y autorización fiscal.
_Avoid_: "venta offline" como estado del servidor, "borrador" (reservado para DraftInvoice)

**Clave de idempotencia**:
UUID generado en el dispositivo, persistido antes de salir de la pantalla de pago y enviado como `idFactura`. Garantiza que reenvíos no duplican la venta (409 → reconciliación).
_Avoid_: "correlation id" en contextos de negocio

**Outbox**:
Tabla local (`pending_invoices`) con estados, leases y reintentos que desacopla la captura de la venta de su envío al servidor.
_Avoid_: "cola de sync" genérica

**Rechazo de dominio**:
Venta que el servidor rechaza por regla de negocio al reconciliar (stock insuficiente, crédito revocado, almacén inválido). Estado terminal con motivo visible; jamás se reintenta automáticamente.
_Avoid_: "error de sync" (ambiguo entre red y dominio)

### Caja

**Caja**:
Punto de cobro físico/lógico asignado a una sucursal, con numeración propia (`factura_correlativo`). Un usuario abre una caja delimitada; un dispositivo trabaja contra una caja a la vez.
_Avoid_: "register", "terminal" (el terminal es el hardware SUNMI)

**Secuencia de caja**:
Período abierto entre apertura y cierre de una caja (`caja_secuencia`, con id server-side). Las ventas se adscriben a una secuencia.
_Avoid_: "sesión del servidor"

**Sesión local de caja**:
Copia local y persistente del estado de la secuencia (abierta/cerrada + identificador), que permite vender y reiniciar la app sin red. Es la única verdad de caja que el POS consulta offline.
_Avoid_: "cache de caja"

**Ticket offline**:
Comprobante térmico NO fiscal impreso sin red para una factura pendiente (numeración provisional `OFF-…`, sin CUFE/número fiscal). La legitimidad fiscal llega al reconciliar.
_Avoid_: "factura fiscal offline" (solo existe con impresora HKA20 física)

### Catálogo y precios

**Bulto**:
Unidades por empaque de un producto (`bulkQuantity`), usado para matemática de precio/cantidad al vender por empaque. NO es stock.
_Avoid_: "cantidad en stock", "existencia bulto"

**Existencia (stock)**:
Cantidad vendible por almacén (`item_existencia_almacen`). Dato server-side y caliente: nunca se sincroniza al catálogo; en el dispositivo solo puede mostrarse como informativo del último snapshot y jamás bloquea una venta.
_Avoid_: "bulkQuantity" como stock

**Nivel de precio**:
Uno de los 5 precios por producto (A–E). El POS vende con el nivel A más el precio por bulto. Se almacenan los valores crudos; los derivados se recalculan en el cliente.
_Avoid_: "lista de precios" (reservado al campo legacy `itemListaPrecio="BASE"`)

**Empresa (tenant)**:
Compañía que delimita todos los datos: catálogo, feed, cajas, facturas. El cursor y el outbox se filtran por empresa.
_Avoid_: "tenantId" en conversación de negocio; "compañía DB"
