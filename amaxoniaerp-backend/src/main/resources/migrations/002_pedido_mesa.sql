-- =============================================================================
-- Migración: Pedidos y comandas asociados a la sesión operativa de mesa.
-- Aplicar sobre la base de datos de cada empresa (bd_nomina NO, bd_contabilidad NO).
--
-- Mecanismo de migración del proyecto: DDL idempotente entregado como script SQL.
-- La aplicación no orquesta migraciones automáticamente (no Flyway/Liquibase);
-- el equipo de operaciones lo aplica al desplegar la nueva versión del backend.
--
-- Modelo:
--   - Una sola tabla `pedido_mesa` por línea de pedido ligada a una sesión.
--   - La "comanda" es un concepto DERIVADO: el conjunto de pedidos que comparten
--     el mismo `comanda_secuencia` (no nulo) fueron enviados juntos a cocina/bar.
--   - Los pedidos con `comanda_secuencia IS NULL` están PENDIENTES de enviar y
--     viven en el buffer del POS hasta que el operario presione "Enviar comanda".
--   - El estado (`PENDIENTE`, `ENVIADA`, `EN_PREPARACION`, `LISTA`, `ENTREGADA`,
--     `CANCELADA`) vive por línea para permitir cancelar un item sin tocar la
--     comanda completa.
--
-- `tieneOperaciones(sesionId)` consulta: existe algún pedido activo con estado
-- distinto de ENTREGADA/CANCELADA. Si lo hay, la sesión no se puede cerrar ni
-- cancelar; el POS debe mostrar el mensaje del backend.
-- =============================================================================

CREATE TABLE IF NOT EXISTS `pedido_mesa` (
    `id`                   INT(11)        NOT NULL AUTO_INCREMENT,
    `sesion_mesa_id`       INT(11)        NOT NULL,
    `comanda_secuencia`    INT(11)        NULL,
    -- Datos del producto snapshot al momento del pedido (no se relee el catálogo
    -- al imprimir la comanda ni al facturar; el precio pactado es este).
    `producto_id`          INT(11)        NOT NULL,
    `item_almacen`         INT(11)        NOT NULL DEFAULT 1,
    `item_codigo`          VARCHAR(80)    NOT NULL DEFAULT '',
    `item_descripcion`     VARCHAR(500)   NOT NULL,
    `item_cantidad`        DECIMAL(32,3)  NOT NULL,
    `item_preciosiniva`    DECIMAL(20,2)  NOT NULL,
    `item_descuento`       DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    `item_montodescuento`  DECIMAL(20,2)  NOT NULL DEFAULT 0.00,
    `item_piva`            DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    `item_totalsiniva`     DECIMAL(20,2)  NOT NULL,
    `item_totalconiva`     DECIMAL(20,2)  NOT NULL,
    `cantidad_bulto`       INT(11)        NOT NULL DEFAULT 1,
    `unidad_empaque`       VARCHAR(40)    NOT NULL DEFAULT 'UNIDAD',
    `notas`                VARCHAR(300)   NULL,
    -- Promoción opcional (cuando el item viene de una promo aplicada).
    `promocion_id`         VARCHAR(40)    NULL,
    `promocion_tipo`       VARCHAR(40)    NULL,
    `promocion_detalle_id` VARCHAR(40)    NULL,
    -- Estado operativo de la línea. Refleja el enum del backend EstadoPedidoMesa.
    `estado`               VARCHAR(30)    NOT NULL DEFAULT 'PENDIENTE',
    `fecha_creacion`       DATETIME       NOT NULL,
    `fecha_envio`          DATETIME       NULL,
    `fecha_entrega`        DATETIME       NULL,
    `activo`               TINYINT(1)     NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    KEY `ix_pedido_mesa_sesion`      (`sesion_mesa_id`),
    KEY `ix_pedido_mesa_comanda`     (`sesion_mesa_id`, `comanda_secuencia`),
    KEY `ix_pedido_mesa_estado`      (`estado`),
    KEY `ix_pedido_mesa_producto`    (`producto_id`),
    CONSTRAINT `fk_pedido_mesa_sesion` FOREIGN KEY (`sesion_mesa_id`) REFERENCES `sesion_mesa` (`id`),
    CONSTRAINT `fk_pedido_mesa_producto` FOREIGN KEY (`producto_id`)  REFERENCES `items` (`id_item`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
