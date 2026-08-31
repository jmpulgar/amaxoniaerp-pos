-- =============================================================================
-- Migración: Sesión operativa de mesa
-- Aplicar sobre la base de datos de cada empresa (bd_nomina NO, bd_contabilidad NO).
-- Compatible con MySQL 5.7+ / MariaDB 10.3+.
-- =============================================================================

-- Asegurar existencia de la tabla base `mesas` si el tenant aún no la tiene.
CREATE TABLE IF NOT EXISTS `mesas` (
    `id`          INT(11)        NOT NULL AUTO_INCREMENT,
    `nombre`      VARCHAR(100)   COLLATE utf8mb4_unicode_ci NOT NULL,
    `planta_id`   INT(11)        NOT NULL,
    `posicion_x`  DECIMAL(10,6)  DEFAULT 0.000000,
    `posicion_y`  DECIMAL(10,6)  DEFAULT 0.000000,
    `codigo`      VARCHAR(50)    COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    `capacidad`   INT(11)        NOT NULL DEFAULT 4,
    `forma`       VARCHAR(30)    COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RECTANGULAR',
    `ancho`       DECIMAL(10,2)  NOT NULL DEFAULT 100.00,
    `alto`        DECIMAL(10,2)  NOT NULL DEFAULT 100.00,
    `rotacion`    DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    `activo`      TINYINT(1)     NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    KEY `fk_mesas_plantas` (`planta_id`),
    CONSTRAINT `fk_mesas_plantas` FOREIGN KEY (`planta_id`) REFERENCES `plantas` (`id`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `sesion_mesa` (
    `id`                 INT(11)       NOT NULL AUTO_INCREMENT,
    `sucursal_id`        INT(11)       NOT NULL,
    `caja_id`            VARCHAR(36)   NOT NULL,
    `area_id`            INT(11)       NOT NULL,
    `mesa_id`            INT(11)       NOT NULL,
    `usuario_id`         INT(11)       NOT NULL,
    `cantidad_personas`  INT(11)       NOT NULL DEFAULT 1,
    `estado`             VARCHAR(30)   NOT NULL,
    `fecha_apertura`     DATETIME      NOT NULL,
    `fecha_cierre`       DATETIME      NULL,
    `activo`             TINYINT(1)    NOT NULL DEFAULT 1,
    `mesa_activa_id`     INT(11) GENERATED ALWAYS AS
        (CASE WHEN `activo` = 1 THEN `mesa_id` ELSE NULL END) STORED,
    PRIMARY KEY (`id`),
    KEY `ix_sesion_mesa_mesa`     (`mesa_id`),
    KEY `ix_sesion_mesa_area`     (`area_id`),
    KEY `ix_sesion_mesa_caja`     (`caja_id`),
    KEY `ix_sesion_mesa_estado`   (`estado`),
    UNIQUE KEY `uq_sesion_mesa_activa` (`mesa_activa_id`),
    CONSTRAINT `fk_sesion_mesa_mesa`   FOREIGN KEY (`mesa_id`) REFERENCES `mesas`   (`id`),
    CONSTRAINT `fk_sesion_mesa_planta` FOREIGN KEY (`area_id`) REFERENCES `plantas` (`id`)
    -- Nota: `caja` es MyISAM en el ERP legado, por lo que `caja_id` se indexa pero no lleva FK física InnoDB.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
