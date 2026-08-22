# RUNBOOK_DATABASE_MIGRATION — Esquemas Room y Exposed

## Android (Room, `amaxoniaerp-pos`)

- Estado: migraciones 10→17 encadenadas en
  `app/src/main/java/com/amaxonia/pos/data/local/db/` (AppDatabase).
- Tests: `AppDatabaseMigrationTest` — valida migraciones con esquemas previos
  y que los DAOs nuevos operan sobre el esquema migrado (incluida la fila de
  `transaction_log` con columnas fiscales/gateway).
- Reglas:
  - Toda migración nueva se agrega al final y suma caso en el test de
    migraciones ANTES del merge.
  - `InMemoryTransactionLogDao` (tests) debe espejar la semántica SQL real;
    cualquier cambio de query exige actualizar ambos lados.
  - Columnas monetarias nuevas: minor-units Long + currencyCode (política
    MONEY-001). Ver `doc/MONEY_INVENTORY.md`.

## Backend (Exposed sobre MySQL por empresa)

- Las tablas viven en `features/*/data/*Tables.kt`; el DDL real lo posee el
  esquema MySQL de cada tenant (`nomempresa.bd`). El backend NO ejecuta
  migraciones automáticas.
- **Cualquier cambio de schema (columna/índice/tipo) es criterio de PARADA**:
  requiere decisión explícita y ventana coordinada por país. Pendientes
  detectados en auditoría FASE 12 (índices secundarios en `factura`,
  `factura_detalle`, `caja_nueva*`; columnas `float tasa/total_ref`) quedan
  documentados, no aplicados.
- H2 en tests usa `MODE=MySQL`; los readers/writers se validan contra H2
  sembrado por reflexión (`DatabaseManager.companyDataSources`) — patrón en
  `TenantSeamRouteIntegrationTest` / `SalesRoutesIntegrationTest`.

## Procedimiento ante un cambio aprobado

1. TASK funcional aprobada que nombre tablas/columnas afectadas.
2. Characterization tests primero (comportamiento actual clavado).
3. Migración/DDL por país con rollback documentado.
4. Gates completos Android+backend verdes antes del merge.
