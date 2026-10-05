-- =============================================================================
-- Migración: Kiosco de autoservicio y modificadores/combos
-- Aplicar sobre la base de datos operacional de cada empresa (PA y VE).
-- Compatible con MySQL 5.7+ / MariaDB 10.3+.
-- =============================================================================

-- 1. Gestión de Dispositivos Kiosco
CREATE TABLE IF NOT EXISTS `kiosco_dispositivo` (
  `id` CHAR(36) NOT NULL,
  `nombre` VARCHAR(80) NOT NULL,
  `prefijo_pedido` VARCHAR(5) NOT NULL DEFAULT 'K1',
  `id_caja` VARCHAR(36) NOT NULL,
  `id_sucursal` INT NOT NULL,
  `id_almacen` INT NOT NULL,
  `cod_vendedor` INT NOT NULL,
  `id_cliente_generico` VARCHAR(36) NOT NULL,
  `token_hash` CHAR(64) NULL,
  `codigo_emparejamiento_hash` CHAR(64) NULL,
  `codigo_expira_en` DATETIME NULL,
  `activo` TINYINT(1) NOT NULL DEFAULT 1,
  `ultimo_contacto` DATETIME NULL,
  `creado_en` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `ix_kiosco_dispositivo_caja` (`id_caja`),
  INDEX `ix_kiosco_dispositivo_token` (`token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Media para Attract Loop
CREATE TABLE IF NOT EXISTS `kiosco_media` (
  `id` INT AUTO_INCREMENT NOT NULL,
  `tipo` ENUM('IMAGE','VIDEO') NOT NULL,
  `archivo` VARCHAR(255) NOT NULL,
  `orden` INT NOT NULL DEFAULT 0,
  `duracion_seg` INT NOT NULL DEFAULT 6,
  `activo` TINYINT(1) NOT NULL DEFAULT 1,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `ix_kiosco_media_activo_orden` (`activo`, `orden`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Grupos de Modificadores (McDonald's Customization)
CREATE TABLE IF NOT EXISTS `item_modificador_grupo` (
  `id` INT AUTO_INCREMENT NOT NULL,
  `nombre` VARCHAR(80) NOT NULL,
  `min_seleccion` INT NOT NULL DEFAULT 0,
  `max_seleccion` INT NOT NULL DEFAULT 1,
  `es_obligatorio` TINYINT(1) NOT NULL DEFAULT 0,
  `es_combo` TINYINT(1) NOT NULL DEFAULT 0,
  `orden` INT NOT NULL DEFAULT 0,
  `activo` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `item_modificador` (
  `id` INT AUTO_INCREMENT NOT NULL,
  `id_grupo` INT NOT NULL,
  `id_item_asociado` INT NULL,
  `nombre` VARCHAR(80) NOT NULL,
  `precio_adicional` DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
  `orden` INT NOT NULL DEFAULT 0,
  `activo` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  INDEX `ix_item_modificador_grupo` (`id_grupo`),
  CONSTRAINT `fk_item_modificador_grupo` FOREIGN KEY (`id_grupo`) REFERENCES `item_modificador_grupo` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `item_modificador_relacion` (
  `id_item` INT NOT NULL,
  `id_grupo` INT NOT NULL,
  `orden` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id_item`, `id_grupo`),
  INDEX `ix_item_mod_rel_grupo` (`id_grupo`),
  CONSTRAINT `fk_item_mod_rel_grupo` FOREIGN KEY (`id_grupo`) REFERENCES `item_modificador_grupo` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Pedidos del Kiosco
CREATE TABLE IF NOT EXISTS `kiosco_pedido` (
  `id` CHAR(36) NOT NULL,
  `id_dispositivo` CHAR(36) NOT NULL,
  `numero_pedido_diario` INT NOT NULL,
  `codigo_pedido` VARCHAR(20) NOT NULL,
  `fecha` DATE NOT NULL,
  `estado` ENUM('COTIZADO','PAGADO','FACTURADO','PAGADO_SIN_FACTURA','ANULADO','RECHAZADO') NOT NULL,
  `modalidad` ENUM('COMER_AQUI','PARA_LLEVAR') NOT NULL,
  `portamesa` VARCHAR(10) NULL,
  `id_cliente` VARCHAR(36) NOT NULL,
  `total` DECIMAL(18,4) NOT NULL,
  `quote_expira_en` DATETIME NOT NULL,
  `pago_referencia` VARCHAR(64) NULL,
  `pago_autorizacion` VARCHAR(32) NULL,
  `pago_ultimos4` CHAR(4) NULL,
  `pago_marca` VARCHAR(20) NULL,
  `id_factura` VARCHAR(36) NULL,
  `motivo_rechazo` VARCHAR(255) NULL,
  `creado_en` DATETIME NOT NULL,
  `actualizado_en` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_kiosco_pedido_dia` (`id_dispositivo`, `fecha`, `numero_pedido_diario`),
  INDEX `ix_kiosco_pedido_fecha` (`fecha`),
  INDEX `ix_kiosco_pedido_estado` (`estado`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `kiosco_pedido_item` (
  `id_pedido` CHAR(36) NOT NULL,
  `linea` INT NOT NULL,
  `id_item` INT NOT NULL,
  `cantidad` DECIMAL(18,4) NOT NULL,
  `precio_unitario` DECIMAL(18,4) NOT NULL,
  `nota` VARCHAR(80) NULL,
  PRIMARY KEY (`id_pedido`, `linea`),
  CONSTRAINT `fk_kiosco_pedido_item_pedido` FOREIGN KEY (`id_pedido`) REFERENCES `kiosco_pedido` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `kiosco_pedido_item_modificador` (
  `id_pedido` CHAR(36) NOT NULL,
  `linea` INT NOT NULL,
  `id_modificador` INT NOT NULL,
  `nombre` VARCHAR(80) NOT NULL,
  `precio_adicional` DECIMAL(18,4) NOT NULL,
  PRIMARY KEY (`id_pedido`, `linea`, `id_modificador`),
  CONSTRAINT `fk_kiosco_pedido_mod_pedido` FOREIGN KEY (`id_pedido`, `linea`) REFERENCES `kiosco_pedido_item` (`id_pedido`, `linea`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Parámetros Generales (configuración aditiva para kiosco)
ALTER TABLE `parametros_generales`
  ADD COLUMN IF NOT EXISTS `kiosco_destino_pedido` ENUM('RETIRO_MOSTRADOR','IMPRESORA_COCINA','MESAS') NOT NULL DEFAULT 'RETIRO_MOSTRADOR';

ALTER TABLE `parametros_generales`
  ADD COLUMN IF NOT EXISTS `kiosco_impresora_cocina_ip` VARCHAR(45) NULL;

ALTER TABLE `parametros_generales`
  ADD COLUMN IF NOT EXISTS `kiosco_modalidades` VARCHAR(30) NOT NULL DEFAULT 'COMER_AQUI,PARA_LLEVAR';

ALTER TABLE `parametros_generales`
  ADD COLUMN IF NOT EXISTS `kiosco_color_marca` CHAR(7) NULL;

ALTER TABLE `parametros_generales`
  ADD COLUMN IF NOT EXISTS `kiosco_config_version` INT NOT NULL DEFAULT 1;
