-- 005_catalog_changes.sql
-- =============================================================================
-- Change feed de catálogo: tabla + triggers de captura obligatoria.
--
-- MOTOR OBJETIVO: MySQL 5.7 ESTRICTO (producción; NO MariaDB, NO MySQL 8).
-- Compatibilidad verificada estáticamente contra 5.7:
--   * TIMESTAMP(3) / DEFAULT CURRENT_TIMESTAMP(3)  -> OK (5.6.4+)
--   * BIGINT UNSIGNED AUTO_INCREMENT + índices utf8mb4:
--       idx_cc_scan   = 32*4+8  = 136 bytes  (< 767, OK sin large_prefix)
--       idx_cc_entity = (32+64)*4 = 384 bytes (< 767, OK sin large_prefix)
--   * <=> (null-safe), IF/THEN, CAST AS CHAR, CONCAT -> OK en 5.7
--   * Un trigger por tabla/evento -> no requiere 5.7.2 (multi-trigger)
--   * DROP TRIGGER IF EXISTS -> OK (5.0.32+)
--   * DELIMITER es directiva del CLIENTE mysql: ejecutar con mysql CLI
--     (no con drivers/API). Los DROP van ANTES del bloque DELIMITER $$.
-- La verificación definitiva es el checklist §16.14 en STAGING MySQL 5.7
-- por país (decisión D3; sin testcontainers por ahora).
--
-- EJECUCIÓN (governance de RUNBOOK_DATABASE_MIGRATION.md):
--   * NO se aplica automáticamente. La aplica operaciones, por país, con
--     ventana coordinada y rollback documentado.
--   * Se ejecuta UNA VEZ POR CADA BASE DE DATOS DE EMPRESA (nomempresa.bd)
--     en cada servidor (VE y PA). El aislamiento por tenant lo da la conexión
--     (CompanyRequestContext -> DatabaseManager), no una columna tenant_id.
--   * El feed captura cambios DESDE su instalación; el estado previo lo cubre
--     el bootstrap del POS (snapshotId = MAX(change_id) del primer manifest).
--
-- VERIFICACIONES PREVIAS (obligatorias, por DB de empresa):
--   1. Confirmar nombres de columna contra el esquema real:
--        SHOW COLUMNS FROM item;
--        SHOW COLUMNS FROM clientes;
--        SHOW COLUMNS FROM cliente_sucursal;
--        SHOW COLUMNS FROM tipo_cliente;      -- PK divergente por país (ver bloque)
--        SHOW COLUMNS FROM promocion;
--        SHOW COLUMNS FROM promocion_detalle;
--        SHOW COLUMNS FROM caja_forma_pago;
--        SHOW COLUMNS FROM caja_forma;
--   2. Confirmar motor de cada tabla (los triggers en MyISAM se disparan pero
--      NO son transaccionales: si la TX externa hace rollback puede quedar un
--      cambio fantasma. Es inofensivo: el lector hidrata el estado vigente y
--      la aplicación es idempotente, pero hay que saberlo):
--        SELECT TABLE_NAME, ENGINE FROM information_schema.TABLES
--         WHERE TABLE_SCHEMA = DATABASE()
--           AND TABLE_NAME IN ('item','clientes','cliente_sucursal','tipo_cliente',
--                              'promocion','promocion_detalle','caja_forma_pago','caja_forma');
--
-- VERIFICACIÓN POSTERIOR (por DB de empresa): esperar 24 triggers.
--   SELECT COUNT(*) FROM information_schema.TRIGGERS
--    WHERE TRIGGER_SCHEMA = DATABASE() AND TRIGGER_NAME LIKE 'trg_%_cc_%';
--
-- SEMÁNTICA:
--   * catalog_changes es APÉNDICE: nunca UPDATE ni DELETE de filas (solo el
--     prune por retención fuera de este script).
--   * op es informativo/auditoría; el lector de /changes deduce el efecto
--     real hidratando la fila vigente por entity_id (existe -> UPSERT con
--     payload, no existe -> DELETE). Por eso los triggers no serializan JSON.
--   * Los triggers AFTER UPDATE comparan SOLO columnas relevantes para el POS
--     (null-safe <=>): una mutación irrelevante (p.ej. item.existencia_total
--     actualizada por el ERP en cada venta) NO genera entrada de feed.
-- =============================================================================

CREATE TABLE IF NOT EXISTS catalog_changes (
    change_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    entity_type VARCHAR(32)  NOT NULL,
    entity_id   VARCHAR(64)  NOT NULL,
    op          VARCHAR(8)   NOT NULL,
    created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (change_id),
    KEY idx_cc_scan   (entity_type, change_id),
    KEY idx_cc_entity (entity_type, entity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- =============================================================================
-- Limpieza idempotente (delimiter por defecto ";", ANTES de DELIMITER $$)
-- =============================================================================
DROP TRIGGER IF EXISTS trg_item_cc_ai;
DROP TRIGGER IF EXISTS trg_item_cc_au;
DROP TRIGGER IF EXISTS trg_item_cc_ad;
DROP TRIGGER IF EXISTS trg_clientes_cc_ai;
DROP TRIGGER IF EXISTS trg_clientes_cc_au;
DROP TRIGGER IF EXISTS trg_clientes_cc_ad;
DROP TRIGGER IF EXISTS trg_cliente_sucursal_cc_ai;
DROP TRIGGER IF EXISTS trg_cliente_sucursal_cc_au;
DROP TRIGGER IF EXISTS trg_cliente_sucursal_cc_ad;
DROP TRIGGER IF EXISTS trg_tipo_cliente_cc_ai;
DROP TRIGGER IF EXISTS trg_tipo_cliente_cc_au;
DROP TRIGGER IF EXISTS trg_tipo_cliente_cc_ad;
DROP TRIGGER IF EXISTS trg_promocion_cc_ai;
DROP TRIGGER IF EXISTS trg_promocion_cc_au;
DROP TRIGGER IF EXISTS trg_promocion_cc_ad;
DROP TRIGGER IF EXISTS trg_promocion_detalle_cc_ai;
DROP TRIGGER IF EXISTS trg_promocion_detalle_cc_au;
DROP TRIGGER IF EXISTS trg_promocion_detalle_cc_ad;
DROP TRIGGER IF EXISTS trg_caja_forma_pago_cc_ai;
DROP TRIGGER IF EXISTS trg_caja_forma_pago_cc_au;
DROP TRIGGER IF EXISTS trg_caja_forma_pago_cc_ad;
DROP TRIGGER IF EXISTS trg_caja_forma_cc_ai;
DROP TRIGGER IF EXISTS trg_caja_forma_cc_au;
DROP TRIGGER IF EXISTS trg_caja_forma_cc_ad;

DELIMITER $$

-- =============================================================================
-- item (PRODUCT)
-- =============================================================================

CREATE TRIGGER trg_item_cc_ai AFTER INSERT ON item
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('PRODUCT', CAST(NEW.id_item AS CHAR), 'UPSERT');
END$$

CREATE TRIGGER trg_item_cc_au AFTER UPDATE ON item
FOR EACH ROW
BEGIN
    IF NOT (
        NEW.cod_item         <=> OLD.cod_item
        AND NEW.referencia     <=> OLD.referencia
        AND NEW.descripcion1   <=> OLD.descripcion1
        AND NEW.codigo_barras  <=> OLD.codigo_barras
        AND NEW.codigo_barras2 <=> OLD.codigo_barras2
        AND NEW.codigo_barras3 <=> OLD.codigo_barras3
        AND NEW.cod_departamento <=> OLD.cod_departamento
        AND NEW.departamento_id  <=> OLD.departamento_id
        AND NEW.monto_exento  <=> OLD.monto_exento
        AND NEW.iva           <=> OLD.iva
        AND NEW.costo_actual  <=> OLD.costo_actual
        AND NEW.cantidad_bulto   <=> OLD.cantidad_bulto
        AND NEW.unidad_empaque   <=> OLD.unidad_empaque
        AND NEW.unidad_o_empaque <=> OLD.unidad_o_empaque
        AND NEW.unidad_porcion   <=> OLD.unidad_porcion
        AND NEW.estatus       <=> OLD.estatus
        AND NEW.precio1 <=> OLD.precio1 AND NEW.utilidad1 <=> OLD.utilidad1
        AND NEW.coniva1 <=> OLD.coniva1 AND NEW.descuento1 <=> OLD.descuento1
        AND NEW.precio1_extra <=> OLD.precio1_extra
        AND NEW.precio2 <=> OLD.precio2 AND NEW.utilidad2 <=> OLD.utilidad2
        AND NEW.coniva2 <=> OLD.coniva2 AND NEW.descuento2 <=> OLD.descuento2
        AND NEW.precio2_extra <=> OLD.precio2_extra
        AND NEW.precio3 <=> OLD.precio3 AND NEW.utilidad3 <=> OLD.utilidad3
        AND NEW.coniva3 <=> OLD.coniva3 AND NEW.descuento3 <=> OLD.descuento3
        AND NEW.precio3_extra <=> OLD.precio3_extra
        AND NEW.precio4 <=> OLD.precio4 AND NEW.utilidad4 <=> OLD.utilidad4
        AND NEW.coniva4 <=> OLD.coniva4 AND NEW.descuento4 <=> OLD.descuento4
        AND NEW.precio4_extra <=> OLD.precio4_extra
        AND NEW.precio5 <=> OLD.precio5 AND NEW.utilidad5 <=> OLD.utilidad5
        AND NEW.coniva5 <=> OLD.coniva5 AND NEW.descuento5 <=> OLD.descuento5
        AND NEW.precio5_extra <=> OLD.precio5_extra
    ) THEN
        INSERT INTO catalog_changes (entity_type, entity_id, op)
        VALUES ('PRODUCT', CAST(NEW.id_item AS CHAR), 'UPSERT');
    END IF;
END$$

CREATE TRIGGER trg_item_cc_ad AFTER DELETE ON item
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('PRODUCT', CAST(OLD.id_item AS CHAR), 'DELETE');
END$$

-- =============================================================================
-- clientes (CLIENT)
-- =============================================================================

CREATE TRIGGER trg_clientes_cc_ai AFTER INSERT ON clientes
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('CLIENT', NEW.id_cliente, 'UPSERT');
END$$

CREATE TRIGGER trg_clientes_cc_au AFTER UPDATE ON clientes
FOR EACH ROW
BEGIN
    IF NOT (
        NEW.cod_cliente   <=> OLD.cod_cliente
        AND NEW.rif         <=> OLD.rif
        AND NEW.dv          <=> OLD.dv
        AND NEW.nombre      <=> OLD.nombre
        AND NEW.apellido    <=> OLD.apellido
        AND NEW.direccion   <=> OLD.direccion
        AND NEW.direccion_nivel1 <=> OLD.direccion_nivel1
        AND NEW.direccion_nivel2 <=> OLD.direccion_nivel2
        AND NEW.direccion_nivel3 <=> OLD.direccion_nivel3
        AND NEW.telefonos   <=> OLD.telefonos
        AND NEW.email       <=> OLD.email
        AND NEW.estado      <=> OLD.estado
        AND NEW.cod_tipo_cliente <=> OLD.cod_tipo_cliente
        AND NEW.cod_tipo_precio  <=> OLD.cod_tipo_precio
        AND NEW.tipo_contribuyente <=> OLD.tipo_contribuyente
        AND NEW.permitecredito <=> OLD.permitecredito
        AND NEW.limite      <=> OLD.limite
        AND NEW.dias        <=> OLD.dias
    ) THEN
        INSERT INTO catalog_changes (entity_type, entity_id, op)
        VALUES ('CLIENT', NEW.id_cliente, 'UPSERT');
    END IF;
END$$

CREATE TRIGGER trg_clientes_cc_ad AFTER DELETE ON clientes
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('CLIENT', OLD.id_cliente, 'DELETE');
END$$

-- =============================================================================
-- cliente_sucursal (CLIENT_BRANCH)
-- =============================================================================

CREATE TRIGGER trg_cliente_sucursal_cc_ai AFTER INSERT ON cliente_sucursal
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('CLIENT_BRANCH', CAST(NEW.sucursal_id AS CHAR), 'UPSERT');
END$$

CREATE TRIGGER trg_cliente_sucursal_cc_au AFTER UPDATE ON cliente_sucursal
FOR EACH ROW
BEGIN
    IF NOT (
        NEW.cliente_codigo    <=> OLD.cliente_codigo
        AND NEW.nombre_sucursal <=> OLD.nombre_sucursal
        AND NEW.nombre_contacto <=> OLD.nombre_contacto
        AND NEW.telefono_contacto <=> OLD.telefono_contacto
        AND NEW.correo_contacto <=> OLD.correo_contacto
        AND NEW.direccion       <=> OLD.direccion
        AND NEW.observaciones   <=> OLD.observaciones
    ) THEN
        INSERT INTO catalog_changes (entity_type, entity_id, op)
        VALUES ('CLIENT_BRANCH', CAST(NEW.sucursal_id AS CHAR), 'UPSERT');
    END IF;
END$$

CREATE TRIGGER trg_cliente_sucursal_cc_ad AFTER DELETE ON cliente_sucursal
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('CLIENT_BRANCH', CAST(OLD.sucursal_id AS CHAR), 'DELETE');
END$$

-- =============================================================================
-- tipo_cliente (CLIENT_TYPE)
-- ¡PK y columna de descripción DIVERGEN por país! Confirmar con SHOW COLUMNS:
--   * si la PK se llama `id`          -> usar NEW.id
--   * si la PK se llama `cod_tipo_cliente` -> usar NEW.cod_tipo_cliente
--   * descripción: `descripcion` o `denominacion` -> ajustar el guard del UPDATE
-- Bloque canónico asumido: PK = id. Ajustar antes de aplicar si difiere.
-- =============================================================================

CREATE TRIGGER trg_tipo_cliente_cc_ai AFTER INSERT ON tipo_cliente
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('CLIENT_TYPE', CAST(NEW.id AS CHAR), 'UPSERT');
END$$

CREATE TRIGGER trg_tipo_cliente_cc_au AFTER UPDATE ON tipo_cliente
FOR EACH ROW
BEGIN
    IF NOT (
        NEW.descripcion   <=> OLD.descripcion
        AND NEW.tipoclientefe <=> OLD.tipoclientefe
    ) THEN
        INSERT INTO catalog_changes (entity_type, entity_id, op)
        VALUES ('CLIENT_TYPE', CAST(NEW.id AS CHAR), 'UPSERT');
    END IF;
END$$

CREATE TRIGGER trg_tipo_cliente_cc_ad AFTER DELETE ON tipo_cliente
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('CLIENT_TYPE', CAST(OLD.id AS CHAR), 'DELETE');
END$$

-- =============================================================================
-- promocion (PROMOTION) — escrita por el ERP legacy fuera de este backend
-- =============================================================================

CREATE TRIGGER trg_promocion_cc_ai AFTER INSERT ON promocion
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('PROMOTION', CAST(NEW.id AS CHAR), 'UPSERT');
END$$

CREATE TRIGGER trg_promocion_cc_au AFTER UPDATE ON promocion
FOR EACH ROW
BEGIN
    IF NOT (
        NEW.id_item         <=> OLD.id_item
        AND NEW.codigo        <=> OLD.codigo
        AND NEW.inicio        <=> OLD.inicio
        AND NEW.fin           <=> OLD.fin
        AND NEW.promocion     <=> OLD.promocion
        AND NEW.imagen        <=> OLD.imagen
        AND NEW.activo        <=> OLD.activo
        AND NEW.descuento_global <=> OLD.descuento_global
    ) THEN
        INSERT INTO catalog_changes (entity_type, entity_id, op)
        VALUES ('PROMOTION', CAST(NEW.id AS CHAR), 'UPSERT');
    END IF;
END$$

CREATE TRIGGER trg_promocion_cc_ad AFTER DELETE ON promocion
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('PROMOTION', CAST(OLD.id AS CHAR), 'DELETE');
END$$

-- =============================================================================
-- promocion_detalle (PROMOTION_DETAIL) — escrita por el ERP legacy
-- =============================================================================

CREATE TRIGGER trg_promocion_detalle_cc_ai AFTER INSERT ON promocion_detalle
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('PROMOTION_DETAIL', CAST(NEW.id AS CHAR), 'UPSERT');
END$$

CREATE TRIGGER trg_promocion_detalle_cc_au AFTER UPDATE ON promocion_detalle
FOR EACH ROW
BEGIN
    IF NOT (
        NEW.id_promocion    <=> OLD.id_promocion
        AND NEW.id_item       <=> OLD.id_item
        AND NEW.cantidad      <=> OLD.cantidad
        AND NEW.cantidad_total  <=> OLD.cantidad_total
        AND NEW.unidad_empaque   <=> OLD.unidad_empaque
        AND NEW.descuento      <=> OLD.descuento
        AND NEW.descuento_monto  <=> OLD.descuento_monto
        AND NEW.id_tipo_precio   <=> OLD.id_tipo_precio
        AND NEW.precio         <=> OLD.precio
        AND NEW.impuesto       <=> OLD.impuesto
        AND NEW.impuesto_porcentaje <=> OLD.impuesto_porcentaje
        AND NEW.importe        <=> OLD.importe
        AND NEW.grupo          <=> OLD.grupo
    ) THEN
        INSERT INTO catalog_changes (entity_type, entity_id, op)
        VALUES ('PROMOTION_DETAIL', CAST(NEW.id AS CHAR), 'UPSERT');
    END IF;
END$$

CREATE TRIGGER trg_promocion_detalle_cc_ad AFTER DELETE ON promocion_detalle
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('PROMOTION_DETAIL', CAST(OLD.id AS CHAR), 'DELETE');
END$$

-- =============================================================================
-- caja_forma_pago (PAYMENT_METHOD)
-- =============================================================================

CREATE TRIGGER trg_caja_forma_pago_cc_ai AFTER INSERT ON caja_forma_pago
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('PAYMENT_METHOD', CAST(NEW.id_forma_pago AS CHAR), 'UPSERT');
END$$

CREATE TRIGGER trg_caja_forma_pago_cc_au AFTER UPDATE ON caja_forma_pago
FOR EACH ROW
BEGIN
    IF NOT (
        NEW.siglas          <=> OLD.siglas
        AND NEW.codigo        <=> OLD.codigo
        AND NEW.descripcion   <=> OLD.descripcion
        AND NEW.id_caja_tp_concepto <=> OLD.id_caja_tp_concepto
        AND NEW.cuenta_contable   <=> OLD.cuenta_contable
        AND NEW.id_caja_tp_registro <=> OLD.id_caja_tp_registro
        AND NEW.FormaPagoFact   <=> OLD.FormaPagoFact
        AND NEW.activo        <=> OLD.activo
        AND NEW.pos           <=> OLD.pos
        AND NEW.imagen        <=> OLD.imagen
        AND NEW.grupo         <=> OLD.grupo
        AND NEW.orden         <=> OLD.orden
        AND NEW.id_banco_cuenta   <=> OLD.id_banco_cuenta
        AND NEW.id_banco_operacion <=> OLD.id_banco_operacion
        AND NEW.tipo_moneda   <=> OLD.tipo_moneda
    ) THEN
        INSERT INTO catalog_changes (entity_type, entity_id, op)
        VALUES ('PAYMENT_METHOD', CAST(NEW.id_forma_pago AS CHAR), 'UPSERT');
    END IF;
END$$

CREATE TRIGGER trg_caja_forma_pago_cc_ad AFTER DELETE ON caja_forma_pago
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('PAYMENT_METHOD', CAST(OLD.id_forma_pago AS CHAR), 'DELETE');
END$$

-- =============================================================================
-- caja_forma (CAJA_PAYMENT_METHOD) — PK compuesta (id_caja, id_forma_pago)
-- =============================================================================

CREATE TRIGGER trg_caja_forma_cc_ai AFTER INSERT ON caja_forma
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('CAJA_PAYMENT_METHOD', CONCAT(NEW.id_caja, ':', CAST(NEW.id_forma_pago AS CHAR)), 'UPSERT');
END$$

CREATE TRIGGER trg_caja_forma_cc_au AFTER UPDATE ON caja_forma
FOR EACH ROW
BEGIN
    IF NOT (NEW.activo <=> OLD.activo) THEN
        INSERT INTO catalog_changes (entity_type, entity_id, op)
        VALUES ('CAJA_PAYMENT_METHOD', CONCAT(NEW.id_caja, ':', CAST(NEW.id_forma_pago AS CHAR)), 'UPSERT');
    END IF;
END$$

CREATE TRIGGER trg_caja_forma_cc_ad AFTER DELETE ON caja_forma
FOR EACH ROW
BEGIN
    INSERT INTO catalog_changes (entity_type, entity_id, op)
    VALUES ('CAJA_PAYMENT_METHOD', CONCAT(OLD.id_caja, ':', CAST(OLD.id_forma_pago AS CHAR)), 'DELETE');
END$$

DELIMITER ;
