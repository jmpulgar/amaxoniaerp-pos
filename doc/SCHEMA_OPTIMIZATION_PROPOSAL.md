# Propuesta Técnica: Optimización y Saneamiento de Esquema SQL (B9 / TASK-148)

> **ESTADO: PROPUESTA DOCUMENTAL (SOLO LECTURA / PLANIFICACIÓN)**  
> En cumplimiento estricto de las reglas duras del proyecto, **este documento es una propuesta de diseño técnico**.  
> **NO se ejecuta DDL sobre bases de producción ni se alteran las migraciones Room de frontend**.

---

## 1. Justificación y Objetivos

Durante las auditorías de rendimiento y arquitectura del backend (`amaxoniaerp-backend`), se identificaron varias oportunidades de optimización en los esquemas relacionales MySQL multi-tenant (`{nomempresa}.bd`) que dan soporte a las operaciones de Punto de Venta (POS), Facturación, Caja y Notas de Crédito:

1. **Rendimiento de Reportes y Arqueos:** Consultas de resumen de caja, filtros de facturas y agregaciones de más vendidos (`getBestSellerItemQuantities`) realizan escaneos completos de tabla (*full table scans*) o usan índices primarios no compuestos.
2. **Precisión Monetaria a Nivel de Almacenamiento:** Columnas heredadas con tipos `FLOAT` o `DOUBLE` en tablas de facturas y cajas que deben alinearse a `DECIMAL(14,2)` y `DECIMAL(12,4)` para evitar discrepancias de redondeo en almacenamiento físico (en coherencia con la política `MONEY-001` de la aplicación).
3. **Consistencia Referencial e Integridad:** Claves foráneas virtuales e índices de cobertura para operaciones en lote (`batchInsert`).

---

## 2. Diagnóstico de Tablas Críticas e Índices Propuestos

### 2.1 Tabla `factura`

| Índice Actual | Problema / Consulta Afectada | Propuesta DDL |
| :--- | :--- | :--- |
| `PRIMARY (id_factura)` | `FacturasRepository.listFacturas` y `FacturasResumenCalculator` filtran por `fecha_factura`, `cod_estatus` y `id_caja`. | Agregar índice compuesto `idx_factura_caja_fecha_estatus (id_caja, fecha_factura, cod_estatus)`. |
| `idx_factura_cliente` (simple) | Búsqueda de facturas por cliente ordenadas por fecha genera `filesort`. | Agregar índice compuesto `idx_factura_cliente_fecha (id_cliente, fecha_factura DESC)`. |
| Ninguno | Búsqueda por número fiscal / correlativo externo (`cod_factura_fiscal`). | Agregar índice `idx_factura_fiscal (cod_factura_fiscal)`. |

```sql
-- DDL Propuesto (factura)
ALTER TABLE factura 
    ADD INDEX idx_factura_caja_fecha_estatus (id_caja, fecha_factura, cod_estatus),
    ADD INDEX idx_factura_cliente_fecha (id_cliente, fecha_factura DESC),
    ADD INDEX idx_factura_fiscal (cod_factura_fiscal);
```

---

### 2.2 Tabla `factura_detalle`

| Índice Actual | Problema / Consulta Afectada | Propuesta DDL |
| :--- | :--- | :--- |
| `PRIMARY (id_factura_detalle)` | Carga de líneas por factura (`findByFacturaId`) y cálculo de items más vendidos (`getBestSellerItemQuantities`). | Agregar índice compuesto `idx_factura_det_factura_item (id_factura, id_item)`. |
| Ninguno | Agregación de ventas por ítem en rangos de fechas. | Agregar índice `idx_factura_det_item (id_item)`. |

```sql
-- DDL Propuesto (factura_detalle)
ALTER TABLE factura_detalle 
    ADD INDEX idx_factura_det_factura_item (id_factura, id_item),
    ADD INDEX idx_factura_det_item (id_item);
```

---

### 2.3 Tablas de Caja (`caja_nueva_secuencia`, `caja_nueva_detalle`, `caja_nueva_detalle_forma_pago`)

| Tabla | Problema / Consulta Afectada | Propuesta DDL |
| :--- | :--- | :--- |
| `caja_nueva_detalle` | `CajaMovimientosDevolucionesReader.loadMovimientoEntradaSalida` filtra por `id_caja_secuencia` y `tipo IN ('E', 'S')`. | Agregar índice `idx_caja_det_sec_tipo (id_caja_secuencia, tipo)`. |
| `caja_nueva_detalle_forma_pago` | Búsqueda de formas de pago por detalle de caja en arqueos. | Agregar índice `idx_caja_fp_det (id_caja_detalle, cod_forma_pago)`. |
| `caja_nueva_secuencia` | Búsqueda de secuencia activa por usuario y caja (`id_usuario, id_caja, estatus`). | Agregar índice `idx_caja_sec_usuario_caja (id_usuario, id_caja, estatus)`. |

```sql
-- DDL Propuesto (caja)
ALTER TABLE caja_nueva_detalle 
    ADD INDEX idx_caja_det_sec_tipo (id_caja_secuencia, tipo);

ALTER TABLE caja_nueva_detalle_forma_pago 
    ADD INDEX idx_caja_fp_det (id_caja_detalle, cod_forma_pago);

ALTER TABLE caja_nueva_secuencia 
    ADD INDEX idx_caja_sec_usuario_caja (id_usuario, id_caja, estatus);
```

---

### 2.4 Tablas de Notas de Crédito (`nota_credito`, `nota_credito_detalle`)

| Tabla | Problema / Consulta Afectada | Propuesta DDL |
| :--- | :--- | :--- |
| `nota_credito` | Búsqueda de devoluciones por factura original y por fecha. | Agregar índice `idx_nc_factura (id_factura)` e `idx_nc_fecha (fecha_nota_credito)`. |
| `nota_credito_detalle` | Carga de líneas anuladas por nota de crédito. | Agregar índice `idx_nc_det_nc_item (id_nota_credito, id_item)`. |

```sql
-- DDL Propuesto (notas de crédito)
ALTER TABLE nota_credito 
    ADD INDEX idx_nc_factura (id_factura),
    ADD INDEX idx_nc_fecha (fecha_nota_credito);

ALTER TABLE nota_credito_detalle 
    ADD INDEX idx_nc_det_nc_item (id_nota_credito, id_item);
```

---

## 3. Propuesta de Migración de Tipos de Datos (FLOAT/DOUBLE → DECIMAL)

Para garantizar consistencia con `BigDecimal(scale = 2, RoundingMode.HALF_UP)` en backend y `Money` en frontend:

### 3.1 Esquema de Conversión Recomendado

```sql
-- Conversión de campos en factura
ALTER TABLE factura 
    MODIFY COLUMN total_total_factura DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN sub_total_factura DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN monto_impuesto DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN monto_exento DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN monto_gravado DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN tasa_cambio DECIMAL(14, 4) NOT NULL DEFAULT 1.0000;

-- Conversión de campos en factura_detalle
ALTER TABLE factura_detalle 
    MODIFY COLUMN precio_unitario DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN monto_iva DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN total_detalle DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN cantidad DECIMAL(12, 3) NOT NULL DEFAULT 1.000;

-- Conversión de campos en caja_nueva_detalle
ALTER TABLE caja_nueva_detalle 
    MODIFY COLUMN monto DECIMAL(14, 2) NOT NULL DEFAULT 0.00;

-- Conversión de campos en caja_nueva_detalle_forma_pago
ALTER TABLE caja_nueva_detalle_forma_pago 
    MODIFY COLUMN monto DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    MODIFY COLUMN monto_ref DECIMAL(14, 2) NOT NULL DEFAULT 0.00;
```

---

## 4. Estrategia de Ejecución Segura (Zero-Downtime)

Cuando el equipo de Infraestructura / DBA programe la ventana de mantenimiento para aplicar este esquema:

1. **Herramienta Online Schema Change:** Usar `pt-online-schema-change` o `gh-ost` para aplicar los `ALTER TABLE` sin bloquear lecturas ni escrituras concurrentes del POS.
2. **Idempotencia:** Verificar la existencia previa de índices antes de la creación (`SHOW INDEX FROM {tabla}`).
3. **Validación de Datos:** Ejecutar queries de control previo y posterior para asegurar que la conversión de `FLOAT` a `DECIMAL` no genere truncamientos imprevistos.

---

## 5. Procedimiento de Rollback

En caso de requerir reversión de los índices creados:

```sql
-- Rollback de índices
ALTER TABLE factura 
    DROP INDEX idx_factura_caja_fecha_estatus,
    DROP INDEX idx_factura_cliente_fecha,
    DROP INDEX idx_factura_fiscal;

ALTER TABLE factura_detalle 
    DROP INDEX idx_factura_det_factura_item,
    DROP INDEX idx_factura_det_item;

ALTER TABLE caja_nueva_detalle 
    DROP INDEX idx_caja_det_sec_tipo;

ALTER TABLE caja_nueva_detalle_forma_pago 
    DROP INDEX idx_caja_fp_det;

ALTER TABLE caja_nueva_secuencia 
    DROP INDEX idx_caja_sec_usuario_caja;

ALTER TABLE nota_credito 
    DROP INDEX idx_nc_factura,
    DROP INDEX idx_nc_fecha;

ALTER TABLE nota_credito_detalle 
    DROP INDEX idx_nc_det_nc_item;
```

---
*Fin de la propuesta técnica (TASK-148)*.
