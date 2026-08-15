# Arquitectura de Amaxonia ERP POS

## Propósito

Este documento define las reglas arquitectónicas que deben conservarse durante el plan `PLAN_ARCHITECTURE_10_10.md`. Es una constitución de ingeniería: no introduce cambios de negocio y no autoriza cambios de contratos, cálculos, crédito/CxC, PAC, HKA-20, Room, migraciones ni comportamiento multi-país.

El refactor de Payment está cerrado. Durante el primer bloque Payment se considera código estabilizado y no es objetivo de rediseño.

## Android

### Capas y dependencias

La dirección permitida es:

```text
ui -> domain
data -> domain
composition -> ui + domain + data
domain -> Kotlin/JDK only
```

No se permite:

```text
ui -> data
domain -> data
domain -> ui
Screen/ViewModel -> DependencyContainer como service locator
```

`DependencyContainer` puede existir mientras se migra la composición, pero su acceso debe quedar confinado al composition root. Las pantallas reciben dependencias explícitas o ViewModels ya construidos por la frontera de composición.

### Feature composition

Un feature Android debe exponer una superficie pequeña y profunda: Screen/Route, ViewModel, estado/acciones/efectos cuando aporten valor y operaciones de dominio para workflows no triviales. No se crean capas o interfaces para getters triviales.

La UI no conoce implementaciones de repositories, DAOs, clientes HTTP, impresoras ni detalles de almacenamiento. Los adaptadores viven en `data`; los contratos que necesita negocio viven en `domain`.

### Estado y efectos

El estado de UI representa datos renderizables. Efectos de una sola vez —navegación, mensajes o disparos de interacción externa— no deben convertirse en estado persistente accidental. El ViewModel coordina UI con operaciones de dominio, no implementa infraestructura.

## Backend

### Archetype A — Query / CRUD simple

Permitido:

```text
route -> repository
```

Sólo cuando la operación no contiene reglas de negocio no triviales, coordinación de múltiples recursos, idempotencia o integración externa.

### Archetype B — Workflow / operación de negocio

Obligatorio:

```text
route -> application -> domain ports -> adapters
```

La route valida el contrato HTTP, obtiene contexto autenticado/tenant, invoca la operación y traduce el resultado a HTTP. La lógica del workflow vive fuera de Ktor.

### Archetype C — Integración externa

Obligatorio:

```text
application/domain port -> adapter -> PAC/HKA/API
```

Una route no crea ni invoca directamente clientes PAC/HKA. La infraestructura externa queda detrás de un port apropiado.

### Criterio para un módulo profundo

Se justifica `application/` o un módulo de operación cuando existe al menos una de estas condiciones:

- workflow con más de una dependencia significativa;
- invariantes o decisiones de negocio;
- idempotencia, retry o reconciliación;
- coordinación DB + I/O externo;
- comportamiento distinto por país;
- operación que merece tests unitarios independientes del framework.

No se agrega `application/` a CRUD simples sólo por simetría.

## Composition roots y DI

La estrategia canónica es constructor DI manual. El composition root crea adaptadores, repositories, application services/use cases y los entrega a routing/UI. Koin no debe convertirse en una segunda estrategia paralela para el mismo grafo.

Ninguna clase de dominio busca dependencias globalmente. Las dependencias son visibles en constructores o factories de composición.

## Multi-tenant

Debe existir una única seam tipada para resolver el contexto de empresa. El contexto canónico debe encapsular, según aplique, claims autenticados, `token_type`, `admin_db`, `Company-DB`, `country_code` y la base de datos de la compañía.

Las routes no duplican algoritmos de resolución de tenant. Una vez disponible la seam canónica, cada route obtiene un `CompanyRequestContext` ya validado.

No se cambia la semántica actual de selección de compañía durante el refactor arquitectónico.

## Dinero y redondeo

- Los límites monetarios deben mantener la representación y escalas que exige el negocio y los contratos actuales.
- No se introducen conversiones implícitas mediante `Double` en nuevas reglas monetarias.
- El redondeo ocurre en boundaries explícitos y documentados; no se dispersa por UI, routes o adapters.
- Una refactorización no cambia escala, modo de redondeo, orden de operaciones, impuestos, descuentos, crédito/CxC ni totales.
- Serialización, persistencia y adapters fiscales conservan exactamente los formatos contractuales existentes salvo una TASK funcional separada.

## Transacciones SQL e I/O externo

Nunca mantener una transacción SQL abierta mientras se realiza HTTP, PAC, HKA u otro I/O externo.

El patrón para workflows que combinan persistencia e integración externa es por fases explícitas:

```text
1. leer/reservar/persistir intención en transacción corta
2. cerrar transacción
3. ejecutar I/O externo
4. persistir resultado en nueva transacción corta
5. reconciliar estados inciertos de forma explícita
```

No se altera el comportamiento fiscal existente al aplicar esta regla; cada migración requiere characterization tests antes de mover fronteras.

## Errores

Categorías objetivo:

```text
Validation
Unauthorized
Forbidden
NotFound
Conflict
DomainRule
ExternalService
Unexpected
```

- El detalle interno se registra en servidor.
- Una respuesta pública usa mensajes estables y no expone SQL, stack traces, secretos ni mensajes crudos de una excepción inesperada.
- `StatusPages` es la frontera HTTP común para errores transversales.
- No se agregan `catch (Exception)` nuevos en routes salvo adaptación estrictamente necesaria y documentada; la convergencia es hacia errores tipados.

## Testing

Cada workflow crítico debe poder probarse sin UI/Ktor y sin servicios externos reales.

Prioridades:

1. dominio/application: invariantes, dinero, multi-país, idempotencia;
2. repositories/adapters: persistencia, serialización e integración;
3. contrato HTTP y seguridad;
4. Room migrations, WorkManager/retry y offline en Android;
5. matriz PA / VE digital / VE HKA-20.

Coverage es un indicador con ratchet, no un objetivo para producir tests triviales. Una caída respecto del baseline aceptado falla CI. Los targets altos del plan son metas progresivas, no un criterio para falsear TASK-023.

No se permiten tests ignorados o suppressions/baselines nuevos para ocultar deuda.

## Reglas multi-país

- Panamá, Venezuela y cualquier país habilitado comparten core sólo cuando la regla es realmente común.
- Una estrategia específica por país se mantiene detrás de un boundary explícito.
- VE HKA-20 y VE digital son flujos distintos cuando así lo establece el comportamiento vigente.
- Un cambio arquitectónico debe demostrar que preserva las rutas PA, VE digital y VE HKA-20 aplicables.
- No se cambian endpoints, payloads, flags fiscales o selección de PAC como parte de una limpieza arquitectónica.

## Quality gates

Antes de integrar a `main` deben pasar los jobs definidos por el primer bloque:

Android:

- `android-unit-test`
- `android-detekt`
- `android-ktlint`
- `android-build-amaxonia`
- `android-build-banesco-debug`
- `android-build-listoerp-debug`

Backend:

- `backend-test`
- `backend-detekt`
- `backend-ktlint`
- `backend-build`

Además, el coverage ratchet debe impedir regresión del baseline medido.

## Regla de cambio

Una TASK arquitectónica cambia fronteras, no decisiones de negocio. Antes de cerrar cada TASK se revisa el diff y se responde explícitamente:

> ¿Alguna modificación cambia una decisión de negocio?

La respuesta necesaria para integrar una TASK de este programa es `NO`.
