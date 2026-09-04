---
status: proposed
---

# Sincronización de catálogo offline vía change feed con cursor y tombstones

El POS debe operar 100% offline (escaneo→precio <150 ms) con un catálogo de ~21.811 productos, pero hoy `CatalogSyncer` re-descarga el catálogo completo en cada sync (73 páginas seriales de 300, sin deletes, sin reanudabilidad, payload con campos que la app descarta). Decidimos: **catálogo completo local** (11–22 MB, trivial para SQLite) + **sync incremental basado en un change feed server-side** (`catalog_changes` con `change_id` monótono por tenant, escritura en la misma transacción que la mutación de origen), recorrido por un **cursor que vive solo en el dispositivo** (persistido en la misma transacción Room que el lote aplicado), **tombstones** para propagar borrados, **bootstrap reanudable por keyset** acotado a un snapshot de consistencia, y **reconciliación periódica** (conteos + hash) como red de seguridad. El outbox de ventas (`pending_invoices`) existente se mantiene; su taxonomía de errores se corrige (409 = éxito reconciliado; 400 de dominio = rechazo terminal visible; red = backoff con tope).

## Considered Options

- **Watermark `updated_at` + tabla de borrados**: más simple de construir, pero frágil (ediciones que no tocan el watermark, filas restauradas, relojes). Descartado como mecanismo principal.
- **Optimizar solo la full-sync** (DTO slim + páginas de 1000 + upserts por lote): cero backend nuevo y necesario como base, pero cada sync seguiría moviendo ~5–10 MB y sin deletes; queda como subconjunto del diseño elegido.
- **Plataformas turnkey** (PowerSync, Couchbase Lite, Realm/Atlas Device Sync, ElectricSQL): Realm EOL (30-sep-2025), Couchbase Lite con restricciones de licencia, ElectricSQL limitado a Postgres, PowerSync añade un servicio externo para un dataset de ~20 MB. Descartadas; si cambian los requisitos de escala, PowerSync es la única candidata a revisitar.
- **Snapshot bundle precocinado (CDN/APK)**: bootstrap en ~1 request, pero exige pipeline de build de snapshots. **Diferido**: se activa solo si el bootstrap optimizado medido en un SUNMI V2 Pro real supera los criterios (≤3 min).
- **CRDT / sync bidireccional de catálogo**: las ventas son append-only con idempotencia y el catálogo es server-authoritative; no hay conflictos que un CRDT resuelva mejor que LWW server-side. Descartado por sobredimensionado.

## Consequences

- El backend requiere trabajo nuevo (tabla de feed, endpoints `/api/sync/v1/*`, hooks de escritura en los repositorios de catálogo y prune de retención). Los endpoints actuales no cambian: las apps en producción siguen operando (más lentas) hasta actualizar.
- Los borrados dejan de ser invisibles: eliminar un producto/cliente en el ERP ahora exige escribir su tombstone en el feed (regla de desarrollo del backend).
- Un dispositivo que permanezca inactivo más que la retención (30 días) recibe `410 Gone` y ejecuta resync completo automático — comportamiento esperado, no error.
- El cursor es local y transaccional con los datos aplicados: un crash entre lotes nunca pierde ni duplica cambios.
- El stock (`item_existencia_almacen`) queda explícitamente **fuera** del feed (dato caliente); su único juez sigue siendo el servidor vía `validar_stock` por tenant.
