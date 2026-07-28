-- =============================================================================
-- Hardening compatible con instalaciones donde 001/003 se aplicaron parcialmente.
-- Aplicar después de 003 sobre la base operacional de cada empresa.
-- Dialecto objetivo: MariaDB (servidor usado por el despliegue actual).
-- =============================================================================

-- Versiones tempranas de 001 usaban UNIQUE(mesa_id, activo), lo cual impedía conservar
-- más de una sesión histórica inactiva por mesa. La proyección NULL sólo restringe activo=1.
ALTER TABLE `sesion_mesa`
    ADD COLUMN IF NOT EXISTS `mesa_activa_id` INT(11)
        GENERATED ALWAYS AS (CASE WHEN `activo` = 1 THEN `mesa_id` ELSE NULL END) STORED;

ALTER TABLE `sesion_mesa`
    DROP INDEX IF EXISTS `uq_sesion_mesa_activa`;

ALTER TABLE `sesion_mesa`
    ADD UNIQUE INDEX `uq_sesion_mesa_activa` (`mesa_activa_id`);

-- Versiones parciales de 003 no copiaban el almacén al snapshot de la cuenta.
ALTER TABLE `cuenta_mesa_detalle`
    ADD COLUMN IF NOT EXISTS `item_almacen` INT(11) NOT NULL DEFAULT 1
        AFTER `producto_id`;

-- Refuerzo para despliegues donde el ALTER inicial de pedido_mesa no llegó a ejecutarse.
ALTER TABLE `pedido_mesa`
    ADD COLUMN IF NOT EXISTS `cantidad_facturada` DECIMAL(32,3) NOT NULL DEFAULT 0.000
        COMMENT 'Cantidad acumulada asociada a cuentas facturadas.';

-- La clave de factura y la idempotencia deben ser únicas en toda la empresa.
ALTER TABLE `cuenta_mesa`
    DROP INDEX IF EXISTS `uq_cuenta_mesa_factura`;

ALTER TABLE `cuenta_mesa`
    ADD UNIQUE INDEX `uq_cuenta_mesa_factura` (`id_factura`);
