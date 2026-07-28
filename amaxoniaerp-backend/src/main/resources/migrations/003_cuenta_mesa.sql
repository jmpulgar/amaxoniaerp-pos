-- =============================================================================
-- Migración: Cuenta de mesa, divisiones, facturación asociada e idempotencia.
-- Aplicar sobre la base de datos de cada empresa (bd_nomina NO, bd_contabilidad NO).
--
-- Mecanismo de migración del proyecto: DDL idempotente entregado como script SQL.
-- La aplicación no orquesta migraciones automáticamente (no Flyway/Liquibase);
-- el equipo de operaciones lo aplica al desplegar la nueva versión del backend.
--
-- Modelo:
--   - `cuenta_mesa`                  : cabecera de cuenta/división de una sesión.
--   - `cuenta_mesa_detalle`          : líneas facturables (cantidad parcial permitida).
--   - `cuenta_mesa_idempotencia`     : claves idempotentes por intento de cobro.
--   - `pedido_mesa.cantidad_facturada`: contador de cantidad ya facturada (no cobrar 2x).
--   - `sesion_mesa.estado`           : ya existe; uso de CUENTA_SOLICITADA y CERRADA_PAGADA
--                                      se permite con este DDL porque no agrega columnas.
--
-- Conservative y retrocompatible: ninguna columna DROP, ningún CREATE OR REPLACE.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- (1) Ampliar `pedido_mesa` con el contador de cantidad facturada.
-- -----------------------------------------------------------------------------
ALTER TABLE `pedido_mesa`
    ADD COLUMN IF NOT EXISTS `cantidad_facturada` DECIMAL(32,3) NOT NULL DEFAULT 0.000
    COMMENT 'Cantidad acumulada asociada a cuentas facturadas. Evita cobrar dos veces la misma línea y permite divisiones parciales.';

-- Índice de soporte para listar pedidos con saldo pendiente por sesión.
CREATE INDEX IF NOT EXISTS `ix_pedido_mesa_saldo`
    ON `pedido_mesa` (`sesion_mesa_id`, `estado`, `activo`);

-- -----------------------------------------------------------------------------
-- (2) Cabecera de cuenta: una sesión puede tener N cuentas consecutivas
--     (cuenta completa + divisiones por producto/cantidad + varias cuentas
--     sucesivas para saldos restantes). El `numero_cuenta` se reinicia por
--     sesión. El `id_factura` queda NULL hasta que la facturación tiene éxito.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `cuenta_mesa` (
    `id`               INT(11)        NOT NULL AUTO_INCREMENT,
    `sesion_mesa_id`   INT(11)        NOT NULL,
    `numero_cuenta`    INT(11)        NOT NULL DEFAULT 1,
    -- ACTIVA: creada y pendiente de pago.
    -- PAGADA: facturada exitosamente (id_factura NOT NULL).
    -- CANCELADA: descartada sin facturación.
    `estado`           VARCHAR(20)    NOT NULL DEFAULT 'ACTIVA',
    -- Snapshots de los totales que el POS visualizó como "esta cuenta".
    `subtotal`         DECIMAL(20,2)  NOT NULL DEFAULT 0.00,
    `descuento`        DECIMAL(20,2)  NOT NULL DEFAULT 0.00,
    `impuesto`         DECIMAL(20,2)  NOT NULL DEFAULT 0.00,
    `total`            DECIMAL(20,2)  NOT NULL DEFAULT 0.00,
    -- Saldo de esta cuenta antes de confirmarse la factura.
    `saldo_restante`   DECIMAL(20,2)  NOT NULL DEFAULT 0.00,
    -- Se completa cuando el proceso de facturación confirma.
    `id_factura`       VARCHAR(64)    NULL,
    `cod_factura`      VARCHAR(64)    NULL,
    `fecha_factura`    DATETIME       NULL,
    `fecha_creacion`   DATETIME       NOT NULL,
    `fecha_cierre`     DATETIME       NULL,
    `activo`           TINYINT(1)     NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_cuenta_mesa_factura` (`id_factura`),
    UNIQUE KEY `uq_cuenta_mesa_numero` (`sesion_mesa_id`, `numero_cuenta`, `estado`),
    KEY `ix_cuenta_mesa_sesion` (`sesion_mesa_id`, `estado`),
    CONSTRAINT `fk_cuenta_mesa_sesion` FOREIGN KEY (`sesion_mesa_id`)
        REFERENCES `sesion_mesa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- (3) Detalle de cuenta: cada fila es una porción facturable de un pedido.
--     Permite división por producto (cantidad = pedido.item_cantidad) o división
--     parcial por cantidad (cantidad < pedido.item_cantidad). La unicidad la
--     garantiza el backend al validar contra `pedido_mesa.cantidad_facturada`,
--     NO una constraint dura aquí, porque un mismo pedido puede aparecer en
--     varias cuentas para liquidarse por partes.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `cuenta_mesa_detalle` (
    `id`                   INT(11)        NOT NULL AUTO_INCREMENT,
    `cuenta_mesa_id`       INT(11)        NOT NULL,
    `pedido_mesa_id`       INT(11)        NOT NULL,
    `producto_id`          INT(11)        NOT NULL,
    `item_almacen`         INT(11)        NOT NULL DEFAULT 1,
    `item_codigo`          VARCHAR(80)    NOT NULL DEFAULT '',
    `item_descripcion`     VARCHAR(500)   NOT NULL,
    `cantidad`             DECIMAL(32,3)  NOT NULL,
    -- Snapshots: precio y porcentaje de IVA del pedido original; los montos
    -- se recalculan proporcionalmente si la cantidad es parcial.
    `item_preciosiniva`    DECIMAL(20,2)  NOT NULL,
    `item_descuento`       DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    `item_montodescuento`  DECIMAL(20,2)  NOT NULL DEFAULT 0.00,
    `item_piva`            DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    `item_totalsiniva`     DECIMAL(20,2)  NOT NULL,
    `item_totalconiva`     DECIMAL(20,2)  NOT NULL,
    -- Indica si el backend ya ejecutó la baja del saldo en pedido_mesa
    -- (cantidad_facturada += cantidad) y ató id_factura al detalle.
    `facturado`            TINYINT(1)     NOT NULL DEFAULT 0,
    `fecha_creacion`       DATETIME       NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_cuenta_mesa_detalle_linea` (`cuenta_mesa_id`, `pedido_mesa_id`),
    KEY `ix_cuenta_detalle_pedido` (`pedido_mesa_id`),
    CONSTRAINT `fk_cuenta_detalle_cuenta` FOREIGN KEY (`cuenta_mesa_id`)
        REFERENCES `cuenta_mesa` (`id`),
    CONSTRAINT `fk_cuenta_detalle_pedido` FOREIGN KEY (`pedido_mesa_id`)
        REFERENCES `pedido_mesa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- (4) Idempotencia: clave única por intento de cobro. Previene dobles toques,
--     reintentos, timeouts y reconexiones. La carga útil del pago se encola
--     aquí como una sola fila con la respuesta del último intento.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `cuenta_mesa_idempotencia` (
    `idempotency_key`      VARCHAR(64)    NOT NULL,
    `cuenta_mesa_id`       INT(11)        NOT NULL,
    `sesion_mesa_id`       INT(11)        NOT NULL,
    -- SENDING (en proceso), CONFIRMED (factura asociada), FAILED (no factura).
    `estado`               VARCHAR(20)    NOT NULL DEFAULT 'SENDING',
    `id_factura_resultado` VARCHAR(64)    NULL,
    `cod_factura_resultado` VARCHAR(64)   NULL,
    `error_mensaje`        VARCHAR(500)   NULL,
    `intentos`             INT(11)        NOT NULL DEFAULT 0,
    `fecha_primer_intento` DATETIME       NOT NULL,
    `fecha_ultimo_intento` DATETIME       NULL,
    PRIMARY KEY (`idempotency_key`),
    KEY `ix_cuenta_idem_cuenta` (`cuenta_mesa_id`),
    KEY `ix_cuenta_idem_sesion` (`sesion_mesa_id`),
    CONSTRAINT `fk_cuenta_idem_cuenta` FOREIGN KEY (`cuenta_mesa_id`)
        REFERENCES `cuenta_mesa` (`id`),
    CONSTRAINT `fk_cuenta_idem_sesion` FOREIGN KEY (`sesion_mesa_id`)
        REFERENCES `sesion_mesa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
