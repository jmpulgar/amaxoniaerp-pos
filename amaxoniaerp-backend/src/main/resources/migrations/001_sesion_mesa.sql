-- =============================================================================
-- Migración: Sesión operativa de mesa
-- Aplicar sobre la base de datos de cada empresa (bd_nomina NO, bd_contabilidad NO).
--
-- Mecanismo de migración del proyecto: DDL idempotente entregado como script SQL.
-- La aplicación no orquesta migraciones automáticamente (no Flyway/Liquibase);
-- el equipo de operaciones lo aplica al desplegar la nueva versión del backend.
--
-- Qué hace:
--   1. Crea la tabla `sesion_mesa` con una clave generada nullable que impide
--      dos sesiones activas simultáneas sin limitar el histórico inactivo.
--   2. Crea una secuencia de apoyo si el servidor la soporta (MySQL 8+: ignorada
--      si ya existe; MariaDB/MariaDB>=10.3 soporta SEQUENCE; en entornos legacy
--      se puede omitir y dejar el AUTO_INCREMENT puro de la tabla).
-- =============================================================================

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
    KEY `ix_sesion_mesa_mesa`   (`mesa_id`),
    KEY `ix_sesion_mesa_area`   (`area_id`),
    KEY `ix_sesion_mesa_caja`   (`caja_id`),
    KEY `ix_sesion_mesa_estado` (`estado`),
    -- NULL no colisiona en índices UNIQUE: se conserva cualquier cantidad de
    -- sesiones históricas y sólo la sesión activa proyecta su mesa_id.
    UNIQUE KEY `uq_sesion_mesa_activa` (`mesa_activa_id`),
    CONSTRAINT `fk_sesion_mesa_mesa`   FOREIGN KEY (`mesa_id`)   REFERENCES `mesas`   (`id`),
    CONSTRAINT `fk_sesion_mesa_planta` FOREIGN KEY (`area_id`)   REFERENCES `plantas` (`id`),
    CONSTRAINT `fk_sesion_mesa_caja`   FOREIGN KEY (`caja_id`)   REFERENCES `caja`    (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
