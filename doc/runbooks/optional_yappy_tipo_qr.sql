-- =============================================================================
-- OPCIONAL — Tipo de QR Yappy del kiosco por empresa (parametros_generales.yappy_tipo_qr)
--
-- NO es una migración obligatoria y NO forma parte de la secuencia
-- amaxoniaerp-backend/src/main/resources/migrations/. Yappy en el kiosco funciona
-- sin esta columna: el backend comprueba (una vez por base de datos de empresa)
-- si existe y, si no existe o está vacía o inválida, usa la variable de entorno
-- YAPPY_QR_TYPE y, por último, 'DYN'. Si la columna se agrega con el backend en
-- marcha, se detecta en un máximo de 10 minutos (sin reiniciar).
--
-- Aplicar solo en las bases de datos operacionales (PA) donde se quiera elegir
-- el tipo de QR desde el administrativo PHP (Parámetros generales > Yappy >
-- "Tipo de QR Yappy (kiosco)"). El PHP solo guarda el campo si la columna existe.
--
-- Valores:
--   DYN = Dinámico de un solo uso; el cliente escanea el QR en la pantalla del kiosco (por defecto).
--   HYB = Híbrido.
--
-- Compatible con MariaDB 10.3+ (ADD COLUMN IF NOT EXISTS). En MySQL 5.7/8.0, que no
-- soporta IF NOT EXISTS en ADD COLUMN, ejecutar la sentencia sin esa cláusula solo
-- si `SHOW COLUMNS FROM parametros_generales LIKE 'yappy_tipo_qr'` no devuelve filas.
--
-- Rollback:
--   ALTER TABLE `parametros_generales` DROP COLUMN `yappy_tipo_qr`;
-- (el backend vuelve a usar YAPPY_QR_TYPE/DYN automáticamente; no requiere reinicio)
-- =============================================================================

ALTER TABLE `parametros_generales`
  ADD COLUMN IF NOT EXISTS `yappy_tipo_qr` VARCHAR(3) NOT NULL DEFAULT 'DYN';
