---
status: accepted
---

# Alcance de sincronización offline por departamento/sucursal ("Ajustes Offline")

El catálogo real alcanza 100k+ productos y 165.534 clientes; sincronizar todo a
cada terminal hace el bootstrap pesado y la mayoría del surtido no se usa en una
sucursal dada. Decidimos: el POS sincroniza según un **alcance declarado por el
dispositivo** — PRODUCTOS = `TODOS` | departamentos seleccionados, CLIENTES =
`TODOS` | sucursales seleccionadas (default `TODOS`, "usar todo" es un toggle) —
que viaja como parámetro en cada request de `/api/sync/v1/*`. El servidor filtra
bootstrap/manifest/reconcile **server-side y sin estado**; el delta hidrata
"existe pero quedó fuera de alcance" como **DELETE (scope-exit)**; un cambio de
alcance dispara re-bootstrap por entidad (wipe lógico + carga reanudable) con el
cursor intacto. La pantalla "Ajustes Offline" (Configuración POS) muestra
conteos de preview y aplica el cambio con confirmación. Detalle completo:
PLAN_OFFLINE_SYNC_DESIGN.md §18.

## Considered Options

- **Server con estado por dispositivo** (tabla de scopes): precisión idéntica
  pero añade lifecycle (alta/baja de terminales, limpieza) y acoplamiento
  dispositivo↔servidor. Rechazado: el alcance cabe en los parámetros del request.
- **Filtrar solo en el cliente** (descargar todo, guardar todo): no resuelve el
  costo de bootstrap ni el storage. Rechazado.
- **Reconciliar contra hashes globales con catálogo local parcial**: compararía
  manzanas con naranjas; forzaría resync falso en cada barrido. Rechazado —
  manifest/reconcile calculan count/hash bajo el alcance del request.
- **Bootstrap parcial incremental al ampliar alcance** (solo departamentos
  nuevos): optimización diferida a post-v1; v1 hace re-bootstrap por entidad
  (simple, correcto, reanudable).

## Consequences

- Bootstrap proporcional a lo seleccionado; un dispositivo puede reducir de
  165.534 clientes a los de sus sucursales operativas.
- Un producto/cliente fuera de alcance no existe localmente: escanearlo offline
  = "no encontrado" (decisión de producto consciente; evita catálogo zombi).
- La exactitud del filtro de clientes depende de identificar la asociación
  cliente↔sucursal real en el esquema (§16.16, abierto hasta staging).
- `estatus` (D4) y alcance son independientes: estatus filtra venta, alcance
  filtra presencia.
