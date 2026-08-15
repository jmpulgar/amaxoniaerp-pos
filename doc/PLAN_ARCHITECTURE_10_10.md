# PLAN MAESTRO — Arquitectura, Consistencia y Calidad 10/10
## Amaxonia ERP POS — Android + Backend Ktor

> **Repositorio:** `jmpulgar/amaxoniaerp-pos`
> **Baseline auditado:** `main` @ `e568f77df275ff54d9a610123fa12c61ed8bf2da`
> **Objetivo:** elevar Android POS + backend Ktor a un estándar medible de arquitectura, consistencia y calidad/testing sin reescribir el producto ni alterar reglas de negocio.
> **Tipo de trabajo:** refactor arquitectónico, hardening y testing.
> **Regla principal:** los cambios arquitectónicos no deben cambiar comportamiento observable salvo que una TASK esté explícitamente marcada como corrección funcional y sea aprobada aparte.

# 0. QUÉ SIGNIFICA “10/10”

“10/10” no significa “cero bugs posibles” ni “100% coverage”.
En este plan significa que el repositorio cumple criterios verificables y mantenibles.

## 0.1 Arquitectura 10/10

- [ ] Todas las dependencias entre capas/features siguen reglas explícitas y automatizadas.
- [ ] Android UI no accede directamente a infraestructura/data salvo desde el composition root.
- [ ] `DependencyContainer` deja de funcionar como service locator global consumido por pantallas.
- [ ] Cada workflow crítico expone una interface profunda y pequeña.
- [ ] Backend tiene una única estrategia de composición/DI.
- [ ] Resolución multi-tenant se realiza en un único seam reutilizable.
- [ ] Rutas Ktor no duplican autenticación/tenant resolution.
- [ ] Workflows backend complejos no son `Route -> Repository` con negocio dentro del route.
- [ ] Features simples no reciben capas artificiales sólo por uniformidad.
- [ ] I/O externo nunca ocurre dentro de una transacción SQL.
- [ ] Facturación, HKA, PAC, offline, idempotencia y dinero tienen boundaries explícitos.
- [ ] No existen dependencias cíclicas entre features.
- [ ] No existe infraestructura Android/Ktor/Exposed dentro del domain puro.
- [ ] Las reglas anteriores están protegidas con architecture tests.

## 0.2 Consistencia 10/10

- [ ] Convención única de nombres de packages/files.
- [ ] Convención única para Routes/UseCases/Repositories/ViewModels/UI state.
- [ ] Convención única para errores y resultados.
- [ ] Convención única para composition roots.
- [ ] Convención única para tenant context.
- [ ] Convención única para logging.
- [ ] Convención única para DTO ↔ domain mapping.
- [ ] Convención única para money/rounding.
- [ ] Convención única para tests.
- [ ] `AGENTS.md` y documentación reflejan el código real.
- [ ] No hay dos formas distintas de resolver el mismo problema transversal.

## 0.3 Calidad/Testing 10/10

- [ ] Android: Detekt y ktlint obligatorios.
- [ ] Backend: Detekt y ktlint equivalentes obligatorios.
- [ ] Baseline Detekt Android reducido a cero.
- [ ] No se agregan nuevos `@Suppress` para esconder deuda.
- [ ] Tests unitarios de dominio/application para todos los workflows críticos.
- [ ] Tests de integración Ktor para contratos y seguridad.
- [ ] Tests de Room migrations.
- [ ] Tests de WorkManager/retry/idempotencia.
- [ ] Tests de serialización Android ↔ backend.
- [ ] Matriz explícita PA / VE digital / VE HKA-20.
- [ ] Coverage mínimo medido y con ratchet.
- [ ] Critical-path coverage por encima del promedio global.
- [ ] CI ejecuta todo automáticamente.
- [ ] `main` no acepta cambios si los quality gates fallan.
- [ ] No hay tests flaky aceptados.

# 1. REGLAS DEL PROGRAMA DE REFACTOR

## 1.1 No reescritura

No migrar a una arquitectura completamente nueva.
La base actual Android `core/data/domain/ui` y el backend por features se conservan.

## 1.2 Refactor incremental

Cada TASK:

1. congela comportamiento;
2. cambia una sola frontera;
3. ejecuta tests;
4. elimina la estructura legacy reemplazada;
5. vuelve a ejecutar quality gates.

## 1.3 No mezclar negocio con arquitectura

No aprovechar estas TASK para:

- cambiar fórmulas;
- cambiar impuestos;
- cambiar crédito;
- cambiar CxC;
- cambiar payload PAC;
- cambiar HKA-20;
- cambiar endpoints;
- cambiar schema;
- cambiar UX;
- cambiar política multi-país.

Cualquier corrección funcional se documenta y ejecuta en otra iniciativa.

## 1.4 No tocar configuración sensible

Este plan no requiere modificar:

```text
.env
.env.development
secretos
keystore
credenciales PAC
configuración de producción
```

## 1.5 No sobrearquitectura

No crear:

- interface por cada clase;
- use case para getters triviales;
- repository sólo para envolver otro repository;
- feature module Gradle por cada pantalla;
- mapper trivial sin boundary real;
- wrapper de una sola línea sin invariantes;
- DI framework sólo para sustituir constructors.

Aplicar siempre deletion test.

# 2. HALLAZGOS BASE

## Android

### A-01 — Service locator leakage

`DependencyContainer` contiene prácticamente todo el grafo de la aplicación y es consumido directamente desde múltiples screens/features.

### A-02 — Detekt baseline todavía representa deuda real

El baseline contiene LongMethod/CyclomaticComplexity y screens que construyen ViewModels directamente con `DependencyContainer`.

Objetivo final: baseline vacío.

### A-03 — Single Gradle module

Actualmente `settings.gradle.kts` incluye únicamente `:app`.

Esto no es un error por sí solo. Primero se impondrán boundaries mediante código + architecture tests.
Sólo se modularizará Gradle cuando exista una razón objetiva.

### A-04 — Arquitectura de UI no completamente uniforme

Payment se acerca al patrón deseado:

```text
Screen/VM -> deep use case/module -> domain ports -> adapters
```

pero otros features todavía ensamblan coordinators/use cases dentro del Composable.

### A-05 — Tooling Android fuerte pero documentación desactualizada

Android ya posee Detekt/ktlint/Kover, pero `AGENTS.md` todavía indica que no existe formatter/linter configurado.

## Backend

### B-01 — Feature maturity inconsistente

Ejemplos actuales:

```text
sales/
  application/
  data/
  domain/
  route/

creditnotes/
  application/
  data/
  domain/
  route/
```

mientras otros features tienen:

```text
clients/
  data/
  domain/
  route/

caja/
  CajaRouting.kt
  data/
  domain/
```

La solución NO es agregar `application/` a todo.
Se definirán archetypes según complejidad.

### B-02 — Tenant resolution duplicado

Múltiples routes resuelven:

```text
JWT principal
token_type
admin_db
Company-DB
country_code
company database
```

de forma independiente.

Debe existir una única seam.

### B-03 — Dos estrategias de composición

Koin existe como dependencia/configuración, pero el grafo real se construye manualmente en `Routing.kt`.

Debe elegirse una sola estrategia.

### B-04 — `Routing.kt` mezcla composition root y routing

El archivo crea repositories, HttpClient, PAC clients, payload builders, strategies, services/use cases y luego registra rutas.

### B-05 — Error handling inconsistente

Hay routes que capturan `Exception` y devuelven `exception.message`.
El `StatusPages` global también puede exponer el mensaje interno.

### B-06 — Quality gates inferiores a Android

Backend no tiene el mismo nivel de Detekt/ktlint/coverage visible en su Gradle build.

### B-07 — Test coverage por feature no uniforme

Hay tests para varios dominios críticos, pero no existe la misma profundidad estructural para todos los features.

## Repositorio / Delivery

### R-01 — `main` sin protección

El branch actual no exige status checks.

### R-02 — No hay PR workflow efectivo

El historial reciente se integra directamente a `main`.

### R-03 — Higiene

Existen artefactos/metadata de IDE/build que deben auditarse y limpiarse sin tocar runtime.

# 3. ARQUITECTURA OBJETIVO

## 3.1 Android

```text
app
│
├── composition
│     └── crea adapters, repositories, use cases y ViewModels
│
├── core
│     ├── logging
│     ├── telemetry
│     ├── money/shared primitives
│     └── platform helpers
│
├── domain
│     ├── model
│     ├── repository (ports)
│     ├── system (ports)
│     └── usecase / operation modules
│
├── data
│     ├── local
│     ├── remote
│     ├── repository (adapters)
│     ├── printer
│     └── sync
│
└── ui
      └── feature
            ├── Route/Screen
            ├── ViewModel
            ├── UiState
            ├── UiAction
            └── UiEffect
```

Regla:

```text
ui -> domain
data -> domain
composition -> ui + domain + data
domain -> Kotlin/JDK only
```

No:

```text
ui -> data
domain -> data
domain -> ui
Screen -> DependencyContainer
```

## 3.2 Backend

```text
com.amaxoniaerp
│
├── core
│   ├── config
│   ├── database
│   ├── http
│   ├── security
│   ├── tenancy
│   └── observability
│
├── composition
│   └── AppDependencies / factories
│
└── features
    └── <feature>
        ├── domain/
        ├── application/   (sólo workflows)
        ├── data/
        └── route/
```

### Archetype A — Query/simple CRUD

Permitido:

```text
route -> repository
```

si no existe lógica de negocio no trivial.

### Archetype B — Workflow/business operation

Obligatorio:

```text
route -> application -> domain ports -> adapters
```

### Archetype C — External integration

```text
application/domain port
          |
        adapter
          |
       PAC/HKA/API
```

Nunca:

```text
Route -> PAC client
SQL transaction -> HTTP request
```

# 4. ROADMAP

## FASE 0 — FREEZE, INVENTORY Y SCORECARD

### TASK-000 — Capturar baseline reproducible

- [ ] Guardar HEAD exacto.
- [ ] Ejecutar Android unit tests, Detekt, ktlint y assemble.
- [ ] Ejecutar backend tests y build.
- [ ] Guardar duración y resultados.
- [ ] No modificar producción.

### TASK-001 — Inventario arquitectónico completo

Generar matriz de todos los features:

```text
Feature
Entrypoint
UI/Route
Application/use case
Domain
Repositories/ports
Adapters
External I/O
DB
Tests
Direct container access
Architecture smells
Risk
```

### TASK-002 — Scorecard inicial

Medir:

```text
architecture-rule violations
direct DependencyContainer usages
duplicated tenant resolution
routes catching generic Exception
features with missing tests
Detekt baseline findings
suppressions
coverage
CI gates
```

### Exit gate

- [ ] 100% de features inventariados.
- [ ] baseline documentado.
- [ ] production code intacto.

---

## FASE 1 — ENGINEERING CONSTITUTION

### TASK-010 — Crear `doc/ARCHITECTURE.md`

Debe definir:

- dependency rules Android;
- feature archetypes backend;
- deep module criteria;
- money policy;
- transaction/I/O policy;
- tenant policy;
- error policy;
- testing policy;
- multi-country rules.

### TASK-011 — ADRs

Crear sólo los ADRs load-bearing:

```text
ADR-001 Manual constructor DI / composition root
ADR-002 Company tenant context
ADR-003 Backend feature archetypes
ADR-004 Money and rounding boundaries
ADR-005 External fiscal I/O outside SQL transactions
ADR-006 Android feature composition
```

### TASK-012 — Actualizar `AGENTS.md`

Android:

- Detekt;
- ktlint;
- comandos reales;
- architecture rules;
- no direct `DependencyContainer` desde screens.

Backend:

- feature archetypes;
- tenant seam;
- error rules;
- quality commands.

---

## FASE 2 — CI, QUALITY GATES Y REPO HYGIENE

### TASK-020 — GitHub Actions Android

Jobs:

```text
android-unit-test
android-detekt
android-ktlint
android-build-amaxonia
android-build-banesco-debug
android-build-listoerp-debug
```

### TASK-021 — GitHub Actions backend

Jobs:

```text
backend-test
backend-detekt
backend-ktlint
backend-build
```

### TASK-022 — Static analysis backend

> **ESTADO: PENDIENTE.** Detekt backend permanece en ROJO: `1.415` findings preexistentes (idénticos en HEAD `0d63a45` y tras TASK-023/025 — cero findings nuevos). El saneamiento pertenece al siguiente bloque de deuda técnica. NO se considera Detekt Backend "verde"; no se creó baseline ni suppression alguna.

Agregar Detekt + ktlint.
No crear baseline masivo permanente.

### TASK-023 — Coverage ratchet

> **ESTADO: COMPLETADA (2026-08-15).**
>
> - Android (Kover, `:app:koverVerifyAmaxoniaDebug`): line coverage `>= 15%` y `>= 4383` líneas cubiertas. Medición real post-formato: 4383/27785 = 15.7747%.
> - Backend (JaCoCo, `jacocoTestCoverageVerification`): LINE COVEREDRATIO `>= 0.46526415`. Medición real: 6878/14783 = 46.526415%.
> - Gates permanentes integrados en `android-ci.yml` (job `android-coverage`) y `backend-ci.yml` (job `backend-coverage`).
> - El threshold nunca queda por debajo de la medición real vigente; no se agregaron tests triviales.
> - TASK-023 no introdujo nuevos findings de Detekt (Android o Backend).

Capturar baseline y prohibir regresión.

Target final recomendado:

```text
critical domain/application: >= 90% line
critical domain/application: >= 80% branch
repositories/integration logic: >= 80% line
overall production code: >= 80% cuando sea razonable
```

Compose UI no debe inflar cobertura con tests triviales.

### TASK-024 — Branch protection

`ANULADA — decisión del proyecto; no implementar.`

### TASK-025 — Repo hygiene

> **ESTADO: COMPLETADA (2026-08-15).**
>
> - Eliminados los workflows temporales: `android-kover-task-audit`, `architecture-audit-metrics`, `architecture-coverage-audit`, `backend-quality-audit`, `backend-quality-fix`, `baseline-audit`.
> - Conservados: `android-ci.yml`, `backend-ci.yml`.
> - Retirados del tracking: `amaxoniaerp-backend/.kotlin/errors/**`, `.idea/workspace.xml`, `amaxoniaerp-pos/app/.project`, `amaxoniaerp-pos/app/.settings/**`.
> - `.gitignore` completados (raíz, backend `.kotlin/`, POS `.project`/`.settings`) sólo donde existía gap real.
> - `.env*`, secretos y configuración productiva intactos.
> - TASK-025 no introdujo nuevos findings de Detekt.

Auditar/retirar sólo artefactos no requeridos:

- `.kotlin/errors/**`;
- IDE-only files no intencionales;
- Eclipse `.project/.settings` si no son requeridos;
- build/temp outputs.

No tocar `.env*`.

---

## FASE 3 — BACKEND CORE: TENANCY, ERRORS Y COMPOSITION

### TASK-030 — Deep tenant seam

Crear concepto canónico:

```kotlin
CompanyRequestContext
```

y una sola forma Ktor de obtenerlo.

Preferencia:

```text
authentication/plugin/interceptor
  -> token_type
  -> Company-DB vs admin_db
  -> country_code
  -> typed context
```

Routes:

```kotlin
val ctx = call.companyContext()
```

Migrar por feature.

Exit:

- [ ] 0 copias de `resolve*CompanyContext`.
- [ ] 0 rutas resolviendo manualmente tenant claims.

### TASK-031 — Error model

Categorías:

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

`StatusPages` mapea a HTTP.

Reglas:

- internal error -> log;
- public response -> mensaje estable;
- 500 no expone SQL/PAC/internal exception details.

### TASK-032 — Composition root backend

Usar constructor DI manual como estrategia canónica.

Crear:

```text
AppDependencies / ApplicationGraph
```

Mover wiring fuera de `Routing.kt`.

`configureRouting(deps)` registra rutas.

Remover Koin/HelloService si queda sin uso real.

### TASK-033 — Observability

Normalizar:

- correlation/request id;
- structured logging;
- tenant context cuando sea seguro;
- no secrets;
- categorías de error;
- duración de operaciones críticas.

---

## FASE 4 — BACKEND FEATURE NORMALIZATION

### TASK-040 — Clasificar features

Cada feature:

```text
QUERY
WORKFLOW
EXTERNAL_INTEGRATION
```

### TASK-041 — Sales reference architecture

Mantenerlo como referencia si ya cumple.

### TASK-042 — Credit notes

Preservar estrictamente:

```text
short DB phase
external PAC outside transaction
short finalize DB phase
```

### TASK-043 — Caja

Separar application workflows sólo donde hay negocio:

```text
OpenCaja
CloseCaja
```

GETs simples pueden continuar como queries.

### TASK-044 — Mesas

Deep operations para:

```text
OpenSession
Add/modify order
RequestAccount
Pay/close account
Cancel/close session
```

### TASK-045 — Query features

Clients/items/geography/promotions/pos pueden seguir simples si no contienen negocio pesado.

### TASK-046 — Electronic invoice

Conservar Strategy/Factory/adapters.
Auditar country selection, HKA/digital policy, results, timeout/retry y SQL/HTTP boundaries.

---

## FASE 5 — ANDROID COMPOSITION ROOT

### TASK-050 — Prohibir `DependencyContainer` en screens

Target: acceso sólo desde composition/platform entrypoints.

### TASK-051 — Feature factories/graphs ligeros

Conceptualmente:

```text
AppGraph
 ├── payment
 ├── cart
 ├── clients
 ├── caja
 ├── mesas
 ├── creditNotes
 └── sync
```

Plain Kotlin; no DI framework obligatorio.

### TASK-052 — ViewModel construction

- constructor injection;
- factory en composition;
- no `DependencyContainer` dentro del ViewModel;
- no concrete data implementation.

### TASK-053 — Route/Screen pattern

Para features complejos:

```text
FeatureRoute -> VM + state/effects
FeatureScreen -> state + callbacks
```

### TASK-054 — Architecture guard

Fallar test si `Screen.kt` o `ViewModel.kt` importa `DependencyContainer`.

---

## FASE 6 — ANDROID FEATURE CONSISTENCY

Orden recomendado:

1. cart;
2. dashboard;
3. clients;
4. caja;
5. mesas;
6. credit notes;
7. drafts;
8. history;
9. products;
10. settings;
11. sync;
12. login/company.

Convención para features complejos:

```text
FeatureRoute
FeatureScreen
FeatureViewModel
FeatureState
FeatureAction
FeatureEffect
```

No exigir Action/Effect en pantallas triviales.

### TASK-060 — Cart
Profundizar operaciones de negocio sin duplicar pricing.

### TASK-061 — Dashboard
Sacar composition de la pantalla y aplicar deletion test a coordinators.

### TASK-062 — Caja
UI no conoce printer factory; deep workflow donde corresponda.

### TASK-063 — Mesas
Alinear con boundaries del backend.

### TASK-064 — Credit notes
UI usa operation/application boundary; no crea fiscal use case en Composable.

### TASK-065 — Resto
Eliminar service locator leakage sin over-engineering.

---

## FASE 7 — DETEKT BASELINE ZERO

### TASK-070 — Clasificar baseline

```text
composition leakage
large UI function
business complexity
mapping complexity
hardware parsing
legitimate framework shape
```

### TASK-071 — Composition findings
Resolver con fases 5/6.

### TASK-072 — UI long methods
Extraer sólo composables cohesionados.

### TASK-073 — Business complexity
Mover reglas a pure functions/domain operations.

### TASK-074 — Hardware parsers
Mantener en adapter y agregar characterization tests.

### TASK-075 — Baseline deletion

Target final:

- baseline vacío o eliminado;
- 0 nuevos suppress usados para esconder deuda.

---

## FASE 8 — TEST ARCHITECTURE

### Android

#### TASK-080 — Domain tests
Money, totals, promotions, CxC, payment, caja, mesas, fiscal selection, idempotency.

#### TASK-081 — ViewModel tests
Loading/success/validation/failure/effects.

#### TASK-082 — Repository adapter tests
Offline-first, Room, API mapping, session/tenant.

#### TASK-083 — Room migrations
Test de cada migration soportada al schema actual.

#### TASK-084 — WorkManager
Offline queue, fiscal retry, gateway callback, lease, backoff, duplicate execution.

### Backend

#### TASK-085 — Application/domain tests
Todos los workflows.

#### TASK-086 — Route integration tests
Ktor `testApplication` para auth, tenant, validation, errors y serialization.

#### TASK-087 — Repository integration tests
Sales/stock/CxC/caja/mesas/credit notes.

#### TASK-088 — PAC client tests
Accepted/rejected/timeout/invalid body/5xx/uncertain usando mock HTTP.

---

## FASE 9 — CROSS-SYSTEM CONTRACT TESTING

### TASK-090 — Contract fixtures

Crear:

```text
contracts/
```

Fixtures para:

```text
login/company
sale
credit sale
partial credit
caja
mesas
credit note
fiscal confirmation
```

### TASK-091 — Android serialization tests
Client produce/consume exactamente fixtures.

### TASK-092 — Backend serialization tests
Backend acepta/produce los mismos fixtures.

### TASK-093 — Multi-country matrix

```text
PA
VE digital
VE HKA-20
```

---

## FASE 10 — FINANCIAL & FISCAL HARDENING

### TASK-100 — Money inventory

Clasificar `Double/Float/BigDecimal/Money` como:

```text
wire DTO
display
business calculation
DB persistence
```

Regla:

- cálculo -> Money/BigDecimal;
- wire Double puede mantenerse por compatibilidad;
- conversión en boundary.

### TASK-101 — Rounding tests

Casos:

```text
0.01
2.30
9.99
10.01
large amounts
mixed payment
partial CXC
tax
exchange rate
```

### TASK-102 — Idempotency matrix

```text
timeout
process death
retry
HTTP 409
duplicate reconciliation
offline -> online
duplicate gateway callback
duplicate fiscal confirmation
```

### TASK-103 — Fiscal matrix

PA, VE digital y VE HKA con success/rejection/uncertain/failure según corresponda.

---

## FASE 11 — ARCHITECTURE TESTS

Android rules:

```text
domain !-> data
domain !-> ui
ui feature !-> data implementation
Screen/ViewModel !-> DependencyContainer
repository implementations -> data
```

Backend rules:

```text
domain !-> Ktor
domain !-> Exposed
domain !-> PAC HTTP client
workflow route !-> DatabaseManager
route !-> HttpClient creation
feature !-> another feature's data implementation
```

La herramienta concreta se elige según compatibilidad real con el proyecto.

---

## FASE 12 — PERFORMANCE & RELIABILITY

### TASK-120 — Compose
Medir recomposition, lazy keys, heavy calculations, image loading.

### TASK-121 — Backend DB
Auditar N+1, repeated queries y evidence de índices.

### TASK-122 — Network reliability
Timeout/cancellation/retry sólo idempotente; nunca retry ciego de venta.

### TASK-123 — Startup
Medir Android startup y backend initialization.

---

## FASE 13 — DOCUMENTATION / OPERATIONS

### TASK-130 — README raíz
Repo layout, build, tests, CI, architecture docs.

### TASK-131 — Runbooks

```text
RUNBOOK_PAYMENT.md
RUNBOOK_HKA.md
RUNBOOK_PAC.md
RUNBOOK_OFFLINE_SYNC.md
RUNBOOK_DATABASE_MIGRATION.md
```

### TASK-132 — PR template
Functional change, country, migration, contract, tests, UI, HKA/PAC, rollback.

# 5. ORDEN DE EJECUCIÓN

No entregar este plan entero a un agente para modificar todo de una sola vez.

```text
0. Baseline/inventory
1. Architecture constitution
2. CI/gates
3. Backend tenant + error + composition
4. Backend feature normalization
5. Android composition root
6. Android feature migration
7. Detekt baseline zero
8. Testing expansion
9. Contract testing
10. Money/fiscal hardening
11. Architecture tests
12. Performance
13. Documentation/final audit
```

Cada fase debe quedar verde antes de continuar.

# 6. PRIORIDAD REAL

## P0

1. CI + branch protection
2. Backend tenant seam
3. Backend composition root
4. Android `DependencyContainer` fuera de Screens
5. Backend static analysis
6. Detekt baseline burn-down
7. Architecture tests

## P1

8. Feature normalization
9. Test matrix
10. Cross-system contracts
11. Money/fiscal hardening

## P2

12. Gradle modularization selectiva
13. Performance optimization
14. Extra runbooks

# 7. MODULARIZACIÓN GRADLE — DECISIÓN DIFERIDA

No convertir Android inmediatamente en muchos módulos.

Después de FASE 6 medir coupling/build time.

Sólo si aporta valor, considerar:

```text
:app
:core:model
:core:network
:core:database
:core:testing
```

Backend puede seguir como un solo Gradle project si las reglas de packages están automatizadas.

# 8. DEFINICIÓN FINAL DE “10/10”

## Arquitectura

- [ ] 0 architecture-test violations.
- [ ] 0 direct `DependencyContainer` usages desde feature UI.
- [ ] 1 Android composition strategy.
- [ ] 1 backend composition strategy.
- [ ] 1 tenant seam.
- [ ] 0 duplicated tenant validation.
- [ ] 0 external HTTP inside SQL transaction.
- [ ] 0 business-heavy Ktor routes.
- [ ] todos los features conformes a su archetype.

## Consistencia

- [ ] naming uniforme.
- [ ] errors uniforme.
- [ ] logging uniforme.
- [ ] feature structure uniforme según archetype.
- [ ] AGENTS/ARCHITECTURE actuales.
- [ ] DTO/domain boundaries documentados.
- [ ] money policy cumplida.

## Calidad/testing

- [ ] Android Detekt PASS sin baseline debt.
- [ ] Android ktlint PASS.
- [ ] Backend Detekt PASS.
- [ ] Backend ktlint PASS.
- [ ] Android unit tests PASS.
- [ ] Backend tests PASS.
- [ ] all debug flavors compile.
- [ ] contract tests PASS.
- [ ] migration tests PASS.
- [ ] idempotency matrix PASS.
- [ ] multi-country matrix PASS.
- [ ] coverage targets PASS.
- [ ] CI required on main.
- [ ] no ignored/flaky tests.
- [ ] no suppressions usadas para esconder deuda.

## Regresión funcional

- [ ] PA preserved.
- [ ] VE digital preserved.
- [ ] VE HKA-20 preserved.
- [ ] contado preserved.
- [ ] crédito/CxC preserved.
- [ ] mesas preserved.
- [ ] offline preserved.
- [ ] fiscal preserved.
- [ ] gateway preserved.
- [ ] idempotency preserved.

# 9. REGLA DE EJECUCIÓN PARA AGENTES

Por TASK:

```text
1. Lee PLAN + AGENTS + archivos indicados.
2. Characterization tests primero si toca comportamiento crítico.
3. Implementa sólo la TASK.
4. No refactors oportunistas.
5. No cambios de negocio.
6. Ejecuta tests específicos.
7. Ejecuta quality gate.
8. Revisa diff.
9. Marca sólo checks demostrados.
10. Commit pequeño y reversible.
```

# 10. PRIMER BLOQUE RECOMENDADO

Después de cerrar formalmente el refactor de Payment:

```text
TASK-000  baseline
TASK-001  inventory
TASK-002  scorecard
TASK-010  ARCHITECTURE.md
TASK-011  ADRs
TASK-012  AGENTS update
TASK-020  Android CI
TASK-021  Backend CI
TASK-022  Backend static analysis
TASK-023  Coverage ratchet
TASK-024  Branch protection
```

No empezar refactors masivos de features antes de estos guardrails.

# 11. CHECKLIST MAESTRO

- [ ] FASE 0 — Baseline/inventory
- [ ] FASE 1 — Architecture constitution
- [ ] FASE 2 — CI/quality gates/repo hygiene
- [ ] FASE 3 — Backend core
- [ ] FASE 4 — Backend feature normalization
- [ ] FASE 5 — Android composition root
- [ ] FASE 6 — Android feature consistency
- [ ] FASE 7 — Detekt baseline zero
- [ ] FASE 8 — Test architecture
- [ ] FASE 9 — Cross-system contracts
- [ ] FASE 10 — Financial/fiscal hardening
- [ ] FASE 11 — Architecture tests
- [ ] FASE 12 — Performance/reliability
- [ ] FASE 13 — Documentation/operations
- [ ] Final Architecture scorecard PASS
- [ ] Final Consistency scorecard PASS
- [ ] Final Quality/Testing scorecard PASS
