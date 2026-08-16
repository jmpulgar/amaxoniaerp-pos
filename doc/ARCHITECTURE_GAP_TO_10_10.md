# Auditoría de brecha arquitectónica hacia 10/10

## Alcance y metodología

Esta auditoría es de solo lectura sobre el estado de `main` previo a este documento. No implementa TASKs, no cambia lógica, arquitectura, CI, cobertura, contratos HTTP/JSON, Room, migraciones, crédito/CxC/caja, PAC/HKA-20 ni reglas multi-país.

Fuentes leídas antes de auditar:

- `doc/PLAN_ARCHITECTURE_10_10.md`
- `doc/ARCHITECTURE.md`
- todos los ADR vigentes de `doc/adr/`
- `amaxoniaerp-pos/AGENTS.md`
- `amaxoniaerp-backend/AGENTS.md`

También se revisó el árbol completo del repositorio, búsquedas estructurales sobre código, historial entre la última medición arquitectónica reproducible y el HEAD auditado, y los GitHub Actions ejecutados sobre ese mismo HEAD. Los comandos de Gradle disponibles se verificaron por sus jobs reales de CI. Durante la auditoría se reejecutó el job Backend Detekt sobre el mismo SHA: volvió a fallar sin modificar el repositorio.

Una limitación importante de auditabilidad es que el workflow actual de Backend Detekt no persiste el reporte detallado como artefacto. Por tanto, el total exacto de findings sí es conocido (`1.415`), pero su distribución exacta por regla no puede recuperarse de la evidencia persistida del HEAD actual. No se inventa esa distribución: se marca explícitamente como **N/D**.

---

## 1. HEAD auditado

- Rama: `main`
- SHA exacto auditado: `0caed47f806521b3241883b379f936df99721d9c`
- El commit auditado ya declara TASK-023 y TASK-025 completadas.
- TASK-024 está **ANULADA** por decisión de proyecto y no se considera deuda pendiente.

---

## 2. Score actual

### Fórmula

El score global usa la siguiente ponderación, igual para Android y Backend:

| Categoría | Peso |
|---|---:|
| Arquitectura | 30% |
| Consistencia | 20% |
| Testing | 25% |
| Calidad estática | 15% |
| CI/Delivery | 10% |

La puntuación de cada categoría combina el cumplimiento de los criterios del plan (`CUMPLIDO = 10`, `PARCIAL = 5`, `NO CUMPLIDO = 0`) con ajustes cuantitativos por cobertura, deuda estática y estado real de los gates. No se otorga 10 a un gate verde si solo está verde por baseline.

### Android

| Categoría | Score | Evidencia principal |
|---|---:|---|
| Arquitectura | 5.5/10 | Persisten 19 usos de `DependencyContainer` desde UI/ViewModels, 4 `ui -> data` y 4 `domain -> data/ui`. |
| Consistencia | 6.0/10 | La arquitectura objetivo está documentada, pero no todas las features usan de forma consistente composition root + Route/Screen + MVI. |
| Testing | 4.0/10 | Cobertura lineal ~15.7747%; 7 features sin tests suficientes por inventario; sin migration tests de Room ni suite dedicada de WorkManager/retry. |
| Calidad estática | 6.0/10 | ktlint verde; Detekt verde únicamente con baseline de 246 findings; 45 suppressions de producción. |
| CI/Delivery | 8.5/10 | Unit tests, lint, Detekt, ktlint, coverage y builds de PA/VE Digital/VE HKA-20 están automatizados y verdes; faltan gates arquitectónicos más fuertes. |
| **Global** | **5.6/10** | `5.5×0.30 + 6.0×0.20 + 4.0×0.25 + 6.0×0.15 + 8.5×0.10 = 5.60`. |

### Backend

| Categoría | Score | Evidencia principal |
|---|---:|---|
| Arquitectura | 4.0/10 | 14 routes con acceso directo a `DatabaseManager`, 12 duplicaciones de tenant resolution, composición híbrida y violaciones de Archetype C. |
| Consistencia | 4.5/10 | ADR y archetypes existen, pero routes, errores, DI y application layer no son uniformes. |
| Testing | 5.0/10 | Cobertura lineal ~46.526415%; 7 features con gaps; integración Ktor efectiva prácticamente ausente; sin architecture tests. |
| Calidad estática | 2.0/10 | Backend Detekt está rojo con 1.415 findings; ktlint verde; 4 suppressions. |
| CI/Delivery | 5.5/10 | test, ktlint, coverage y security pasan; Detekt falla y el build queda cancelado por dependencia del gate. |
| **Global** | **4.2/10** | `4.0×0.30 + 4.5×0.20 + 5.0×0.25 + 2.0×0.15 + 5.5×0.10 = 4.20`. |

---

## 3. Scorecard cuantitativo

### Android

| Métrica | Estado actual |
|---|---:|
| Features inventariadas | 16 |
| Usos directos de `DependencyContainer` desde UI/ViewModels | **19** |
| Archivos con `ui -> data` | **4** |
| Archivos con `domain -> data/ui` | **4** |
| Entradas del baseline Detekt | **246** |
| `@Suppress` en producción | **45** |
| Features con gaps de tests | **7** |
| Coverage lineal global | **~15.7747%** |
| Migration tests de Room detectados | **0** |
| Suites dedicadas WorkManager/Worker/retry detectadas | **0** |
| Serialization tests | Sí, parciales; sin fixture contractual compartido completo |
| Flavors de build/lint cubiertos en CI | PA, VE Digital, VE HKA-20 |
| Architecture tests de dependencias/ciclos detectados | **0** |

Features con gaps de tests detectados: `clients`, `company`, `drafts`, `products`, `reports`, `settings`, `welcome`.

Inventario estructural relevante por feature (`ui/usecases/tests/container/ui-data`):

| Feature | UI | Use cases | Tests | Container | ui->data |
|---|---:|---:|---:|---:|---:|
| caja | 6 | 2 | 1 | 1 | 0 |
| cart | 4 | 4 | 6 | 1 | 0 |
| clients | 7 | 0 | 0 | 3 | 0 |
| company | 3 | 0 | 0 | 1 | 0 |
| creditnotes | 5 | 1 | 1 | 1 | 0 |
| dashboard | 9 | 0 | 2 | 1 | 1 |
| drafts | 2 | 1 | 0 | 1 | 0 |
| history | 4 | 0 | 1 | 1 | 0 |
| login | 5 | 0 | 1 | 1 | 0 |
| mesas | 14 | 1 | 2 | 1 | 0 |
| payment | 7 | 23 | 19 | 2 | 0 |
| products | 6 | 0 | 0 | 2 | 0 |
| reports | 3 | 0 | 0 | 1 | 0 |
| settings | 2 | 0 | 0 | 1 | 0 |
| sync | 3 | 1 | 1 | 1 | 1 |
| welcome | 1 | 0 | 0 | 0 | 0 |

`payment` es actualmente la feature más fuerte en separación y tests, pero todavía no representa por sí sola el cumplimiento repo-wide del archetype Android objetivo.

### Backend

| Métrica | Estado actual |
|---|---:|
| Features inventariadas | 14 |
| Findings Detekt exactos | **1.415** |
| Distribución exacta por regla Detekt | **N/D: el workflow no persiste el reporte detallado** |
| `@Suppress` en producción | **4** |
| Route files con duplicación de tenant resolution | **12** |
| Route files con `catch (Exception)` genérico | **10** |
| Route files con acceso directo a `DatabaseManager` | **14** |
| Route files creando `HttpClient` directamente | **0** en la medición estructural |
| Domain files con imports de framework | **2** |
| Features con gaps de tests | **7** |
| Coverage lineal global | **~46.526415%** |
| Architecture tests detectados | **0** |
| Ktor `testApplication` efectivo | `ApplicationTest` existe pero no contiene tests útiles |

Features Backend inventariadas: `assets`, `auth`, `caja`, `clients`, `companies`, `creditnotes`, `electronicinvoice`, `facturas`, `geography`, `items`, `mesas`, `pos`, `promotions`, `sales`.

Features con gaps de tests detectados: `assets`, `auth`, `caja`, `companies`, `geography`, `items`, `promotions`.

### Gates ejecutados sobre el HEAD auditado

#### Android

- Unit tests + coverage: **VERDE**.
- Detekt: **VERDE ÚNICAMENTE POR BASELINE** de 246 entradas.
- ktlint: **VERDE**.
- Android Lint: **VERDE**.
- Build Android: **VERDE**.
- Coverage gate: **VERDE**, pero es un ratchet mínimo, no un objetivo 10/10: >=15% y >=4.383 líneas cubiertas.
- Builds/lint cubren `panamaDevDebug`, `venezuelaDigitalDevDebug` y `venezuelaHkaDevDebug`.

#### Backend

- `test`: **VERDE**.
- `ktlintCheck`: **VERDE**.
- coverage: **VERDE** con ratchet >=46.526415% lineal.
- security/container checks: **VERDES**.
- `detekt`: **ROJO**, reproducido nuevamente durante esta auditoría sobre el mismo SHA; 1.415 findings preexistentes.
- `build -x test`: **CANCELADO** por el gate Detekt fallido en el workflow; no se oculta como verde.

Clasificación general:

- **Deuda preexistente**: baseline Android, suppressions, violaciones de capas, 1.415 findings Backend, cobertura insuficiente, gaps de test y DI/route debt.
- **Gate verde real**: ktlint Android/Backend, Android unit/build/lint, Backend test/coverage/security, builds de flavors Android.
- **Gate verde únicamente por baseline**: Android Detekt.
- **Gate rojo**: Backend Detekt; por dependencia del workflow, Backend build no completa.
- **Criterio todavía no implementado**: architecture tests repo-wide, prueba automatizada de ciclos, cobertura contractual/country matrix completa, migration tests Room, WorkManager/retry/idempotencia integral.

---

## 4. Gap contra 10/10

### Arquitectura 10/10

| Criterio | Android | Backend | Evidencia |
|---|---|---|---|
| Dependencias solo hacia adentro | **NO CUMPLIDO** | **NO CUMPLIDO** | Android: 4 `ui -> data` y 4 `domain -> outer`. Backend: routes todavía conocen DB/infra y 2 domain files importan framework. |
| Composition root único y explícito | **PARCIAL** | **PARCIAL** | Android aún resuelve dependencias desde 19 UI/VM. Backend `Routing.kt` concentra wiring pero también crea HTTP/PAC/storage/repos y convive con Koin. |
| UI/route no actúa como service locator | **NO CUMPLIDO** | **NO CUMPLIDO** | Android usa `DependencyContainer`; Backend routes acceden DB y, en casos C, infraestructura/procesadores. |
| Archetypes de feature aplicados repo-wide | **PARCIAL** | **PARCIAL** | Algunas referencias están mejor estructuradas, pero no todo el repositorio cumple A/B/C o Route/Screen/MVI. |
| Tenant resolution centralizado | N/A | **NO CUMPLIDO** | 12 route files repiten resolución de tenant. |
| External/PAC I/O fuera de transacciones SQL | N/A | **PARCIAL** | Las referencias documentadas lo separan y no se detectó creación directa de `HttpClient` en routes, pero no existe guard arquitectónico repo-wide que demuestre cero violaciones. |
| Sin dependencias cíclicas | **PARCIAL** | **PARCIAL** | No hay suite de architecture tests/cycle detection capaz de probar el criterio automáticamente. |

### Consistencia 10/10

| Criterio | Android | Backend | Evidencia |
|---|---|---|---|
| Patrón de construcción uniforme | **PARCIAL** | **PARCIAL** | Android mezcla factories/composición con container desde UI. Backend mezcla constructor DI y Koin. |
| `UiState/UiAction/UiEffect/ViewModel` consistente | **PARCIAL** | N/A | No todas las features aplican el patrón objetivo con el mismo nivel de separación. |
| Route/Screen consistente | **PARCIAL** | N/A | Existe en parte; falta enforcement repo-wide. |
| Error model tipado y mapping central | N/A | **NO CUMPLIDO** | 10 route files tienen catches genéricos; hay rutas que exponen `e.message`. |
| DTO ↔ domain consistente | N/A | **PARCIAL** | Hay separación en varias features, pero no contrato/archetype enforcement completo. |
| Money/rounding centralizado | **PARCIAL** | **PARCIAL** | ADR-004 define la frontera, pero falta inventario final y tests/gates repo-wide. |
| Multi-país consistente | **PARCIAL** | **PARCIAL** | CI compila los tres perfiles Android, pero falta matriz contractual/funcional automatizada PA/VE Digital/VE HKA-20. |

### Calidad / Testing 10/10

| Criterio | Android | Backend | Evidencia |
|---|---|---|---|
| Detekt sin deuda oculta | **NO CUMPLIDO** | **NO CUMPLIDO** | Android: baseline 246. Backend: 1.415 findings y gate rojo. |
| Suppressions mínimos y justificados | **NO CUMPLIDO** | **PARCIAL** | Android 45, Backend 4; falta auditoría/cierre sistemático. |
| ktlint | **CUMPLIDO** | **CUMPLIDO** | Gates verdes actuales. |
| Coverage global objetivo | **NO CUMPLIDO** | **NO CUMPLIDO** | Android ~15.77%; Backend ~46.53%; objetivo 10/10 muy superior. |
| Critical workflow coverage | **PARCIAL** | **PARCIAL** | Payment tiene buena densidad de tests, pero no existe cobertura crítica completa y medida por workflow. |
| Tests por feature | **NO CUMPLIDO** | **NO CUMPLIDO** | 7 gaps Android y 7 Backend. |
| Room migrations | **NO CUMPLIDO** | N/A | 0 migration tests detectados. |
| WorkManager/retry/idempotencia | **NO CUMPLIDO** | **PARCIAL** | Sin suite dedicada Android; idempotencia/retry no está demostrada como matriz end-to-end. |
| Android ↔ Backend serialization | **PARCIAL** | **PARCIAL** | Existen tests de serialización en ambos lados, pero no shared fixture contractual completo. |
| Integration tests Ktor | N/A | **NO CUMPLIDO** | `ApplicationTest` no aporta suite efectiva. |
| Tenant/security tests | N/A | **PARCIAL** | Existen controles/security CI, pero falta cobertura de comportamiento tenant/error en rutas críticas. |
| Architecture tests | **NO CUMPLIDO** | **NO CUMPLIDO** | No se detectaron tests dedicados. |
| TASK-024 Branch protection | **ANULADO** | **ANULADO** | Decisión explícita del proyecto; no cuenta como deuda. |

---

## 5. TASKs restantes

Leyenda de estado:

- **CUMPLIDO**: evidencia actual suficiente.
- **PARCIAL**: parte existe, pero no satisface la definición final.
- **PENDIENTE**: todavía requiere ejecución sustantiva.
- **ANULADO**: no debe ejecutarse.

`PARCIAL` cuenta como TASK restante porque aún requiere trabajo para cerrar su definición.

| TASK | Estado | Impacto | Riesgo | Dependencias | Scope | ¿Sigue necesaria? |
|---|---|---|---|---|---|---|
| TASK-000 Baseline reproducible | CUMPLIDO | Alto | Bajo | — | Ambos | No, salvo mantener evidencia. |
| TASK-001 Inventario arquitectónico | CUMPLIDO | Alto | Bajo | 000 | Ambos | No; esta auditoría lo actualiza. |
| TASK-002 Scorecard inicial | CUMPLIDO | Alto | Bajo | 000-001 | Ambos | No; actualizado aquí. |
| TASK-010 ARCHITECTURE.md | CUMPLIDO | Alto | Bajo | 001 | Ambos | No. |
| TASK-011 ADRs | CUMPLIDO | Alto | Bajo | 010 | Ambos | No. |
| TASK-012 AGENTS | CUMPLIDO | Medio | Bajo | 010-011 | Ambos | No. |
| TASK-020 GitHub Actions Android | CUMPLIDO | Alto | Bajo | 000 | Android | No; mantener gates. |
| TASK-021 GitHub Actions Backend | CUMPLIDO | Alto | Bajo | 000 | Backend | No como infraestructura; el gate Detekt sigue rojo por deuda. |
| TASK-022 Static analysis Backend | PENDIENTE | Muy alto | Bajo-Medio | 021 | Backend | **Sí**. 1.415 findings. |
| TASK-023 Coverage ratchets/gates | CUMPLIDO | Alto | Bajo | 020-021 | Ambos | No; completada. |
| TASK-024 Branch protection | ANULADO | — | — | — | Ambos | **No**. |
| TASK-025 Repo hygiene | CUMPLIDO | Medio | Bajo | 000 | Ambos | No; completada. |
| TASK-030 Tenant seam | PENDIENTE | Muy alto | Medio | 040 | Backend | **Sí**. 12 duplicaciones. |
| TASK-031 Error model | PENDIENTE | Muy alto | Medio | 040 | Backend | **Sí**. 10 catches genéricos. |
| TASK-032 Composition root Backend | PENDIENTE | Muy alto | Medio | 040 | Backend | **Sí**. Wiring híbrido en `Routing.kt`. |
| TASK-033 Observability | PENDIENTE | Alto | Bajo-Medio | 031-032 | Backend | **Sí**. |
| TASK-040 Clasificar features A/B/C | PENDIENTE | Muy alto | Bajo | 010-011 | Backend | **Sí**. Base para refactors seguros. |
| TASK-041 Sales reference architecture | PARCIAL | Muy alto | Alto | 030-032,040 | Backend | **Sí** para completar y probar archetype. |
| TASK-042 Credit notes | PARCIAL | Muy alto | Alto | 041 | Backend | **Sí**. |
| TASK-043 Caja Backend | PENDIENTE | Muy alto | Alto | 041-042 | Backend | **Sí**. |
| TASK-044 Mesas Backend | PENDIENTE | Alto | Medio-Alto | 040-043 | Backend | **Sí**. |
| TASK-045 Query features | PARCIAL | Medio-Alto | Medio | 040 | Backend | **Sí**. |
| TASK-046 Electronic invoice | PARCIAL | Muy alto | Alto | 031-032,040 | Backend | **Sí**. Route aún llama processor de integración y captura `Exception`. |
| TASK-050 Prohibir DependencyContainer en screens | PENDIENTE | Muy alto | Medio | 051-052 | Android | **Sí**. 19 usos. |
| TASK-051 Feature factories/graphs | PENDIENTE | Muy alto | Medio | 010-011 | Android | **Sí**. |
| TASK-052 ViewModel construction | PENDIENTE | Muy alto | Medio | 051 | Android | **Sí**. |
| TASK-053 Route/Screen pattern | PARCIAL | Alto | Medio | 051-052 | Android | **Sí**. |
| TASK-054 Android architecture guard | PENDIENTE | Muy alto | Bajo | 050-053 | Android | **Sí**. No architecture tests actuales. |
| TASK-060 Cart | PARCIAL | Alto | Medio | 050-054 | Android | **Sí**. |
| TASK-061 Dashboard | PENDIENTE | Alto | Medio | 050-054 | Android | **Sí**. Tiene `ui -> data`. |
| TASK-062 Caja Android | PENDIENTE | Muy alto | Alto | 050-054 | Android | **Sí**. |
| TASK-063 Mesas Android | PARCIAL | Alto | Medio-Alto | 050-054 | Android | **Sí**. |
| TASK-064 Credit notes Android | PARCIAL | Alto | Medio-Alto | 050-054 | Android | **Sí**. |
| TASK-065 Remaining Android features | PENDIENTE | Alto | Medio | 050-054 | Android | **Sí**. |
| TASK-070 Clasificar baseline Android | PENDIENTE | Alto | Bajo | 020 | Android | **Sí**. 246 entradas. |
| TASK-071 Composition findings | PENDIENTE | Alto | Bajo-Medio | 070 | Android | **Sí**. |
| TASK-072 UI long-method findings | PENDIENTE | Medio | Bajo | 070 | Android | **Sí**. |
| TASK-073 Business complexity findings | PENDIENTE | Alto | Medio-Alto | 070 | Android | **Sí**, sin cambiar negocio. |
| TASK-074 Hardware parsers | PARCIAL | Alto | Alto | 070 | Android | **Sí**, especialmente HKA/PAC boundaries. |
| TASK-075 Baseline deletion | PENDIENTE | Muy alto | Bajo | 070-074 | Android | **Sí**. Objetivo final 0 baseline. |
| TASK-080 Android domain tests | PARCIAL | Muy alto | Bajo | 060-065 | Android | **Sí**. |
| TASK-081 Android VM tests | PARCIAL | Muy alto | Bajo | 053,060-065 | Android | **Sí**. |
| TASK-082 Repository adapter tests | PARCIAL | Alto | Bajo-Medio | 060-065 | Android | **Sí**. |
| TASK-083 Room migrations | PENDIENTE | Muy alto | Bajo | 082 | Android | **Sí**. 0 migration tests detectados. |
| TASK-084 WorkManager | PENDIENTE | Muy alto | Bajo-Medio | 082 | Android | **Sí**. |
| TASK-085 Backend app/domain tests | PARCIAL | Muy alto | Bajo | 041-046 | Backend | **Sí**. |
| TASK-086 Backend route integration tests | PARCIAL | Muy alto | Bajo-Medio | 031,041-046 | Backend | **Sí**. Ktor app integration insuficiente. |
| TASK-087 Backend repository integration tests | PARCIAL | Alto | Medio | 041-046 | Backend | **Sí**. |
| TASK-088 PAC client tests | PARCIAL | Muy alto | Alto | 046 | Backend | **Sí**. |
| TASK-090 Contract fixtures | PENDIENTE | Muy alto | Bajo | 091-093 | Ambos | **Sí**. |
| TASK-091 Android serialization | PARCIAL | Alto | Bajo | 090 | Android | **Sí**. |
| TASK-092 Backend serialization | PARCIAL | Alto | Bajo | 090 | Backend | **Sí**. |
| TASK-093 Multi-country matrix | PENDIENTE | Muy alto | Bajo-Medio | 090-092 | Ambos | **Sí**. |
| TASK-100 Money inventory | PENDIENTE | Muy alto | Bajo | 004 ADR | Ambos | **Sí**. |
| TASK-101 Rounding tests | PARCIAL | Muy alto | Bajo | 100 | Ambos | **Sí**. |
| TASK-102 Idempotency matrix | PENDIENTE | Muy alto | Medio | 041-046,084 | Ambos | **Sí**. |
| TASK-103 Fiscal matrix | PENDIENTE | Muy alto | Alto | 093,100-102 | Ambos | **Sí**. |
| TASK-120 Compose performance | PENDIENTE | Medio | Bajo-Medio | Android stabilization | Android | **Sí** para cierre 10/10. |
| TASK-121 Backend DB performance | PENDIENTE | Alto | Medio | Backend stabilization | Backend | **Sí**. |
| TASK-122 Network reliability | PARCIAL | Alto | Medio | 084,102 | Ambos | **Sí**. |
| TASK-123 Startup | PENDIENTE | Medio | Bajo-Medio | 032,051 | Ambos | **Sí**. |
| TASK-130 Root README | PENDIENTE | Medio | Bajo | arquitectura estable | Ambos | **Sí**. |
| TASK-131 Runbooks | PENDIENTE | Alto | Bajo | CI/operación estable | Ambos | **Sí**. |
| TASK-132 PR template | PENDIENTE | Medio | Bajo | criterios finales | Ambos | **Sí**. |

**TASKs restantes: 53** (`PENDIENTE` + `PARCIAL`). TASK-024 queda fuera por estar anulada.

---

## 6. Orden óptimo hacia 10/10

El orden recomendado prioriza máximo impacto arquitectónico, mínimo riesgo funcional, desbloqueo de trabajo posterior y reducción medible de deuda sin refactors masivos.

### Bloque 1 — Backend static debt medible y auditabilidad

TASK principal: `022`.

1. Persistir en CI el reporte Detekt con conteo total y distribución por regla/paquete.
2. No introducir baseline Backend ni nuevas suppressions.
3. Reducir deuda en slices de **50–100 findings como máximo por PR/bloque**, agrupando por una regla o paquete de bajo riesgo.
4. Empezar por reglas mecánicas sin efecto funcional: estilo estructural, imports, naming/complexity puramente local cuando sea demostrablemente behavior-preserving.
5. Cada bloque debe exigir: tests verdes, ktlint verde, raw Detekt strictly menor que el bloque anterior, cero cambios de contratos/negocio.

No se debe intentar arreglar los 1.415 findings en una sola ejecución. La métrica de salida de cada slice debe ser `findings_after < findings_before`, sin baseline ni suppressions nuevas.

### Bloque 2 — Seams fundamentales Backend

TASKs: `040 -> 030 -> 031 -> 032 -> 033`.

- Clasificar A/B/C antes de mover código.
- Centralizar tenant resolution.
- Crear/normalizar error model tipado y mapping HTTP.
- Definir ownership único del composition root; Koin solo donde el ADR/arquitectura lo justifique.
- Añadir caracterización antes de mover rutas de alto riesgo.

### Bloque 3 — Verticales Backend B/C

Orden: `041 Sales -> 042 Credit Notes -> 043 Caja -> 044 Mesas -> 046 Electronic Invoice`, con `045` para query features en paralelo cuando no bloquee.

Cada vertical debe cerrarse de forma pequeña: route fina, application orchestration cuando corresponde, domain libre de framework, ports/adapters para I/O externo, y sin modificar contratos ni reglas funcionales.

### Bloque 4 — Boundaries y DI Android

TASKs: `051 -> 052 -> 050 -> 053 -> 054`, y luego `060-065` feature por feature.

Meta inmediata y cuantificable:

- `DependencyContainer` desde UI/ViewModels: **19 -> 0**.
- `ui -> data`: **4 -> 0**.
- `domain -> data/ui`: **4 -> 0**.

No ejecutar un refactor horizontal masivo; cerrar una feature por bloque con tests de caracterización.

### Bloque 5 — Critical testing

TASKs: `080-088`.

Prioridad: payment, caja, sales, credit notes, mesas, tenant/security/error mapping, PAC, Room migrations y WorkManager/retry/idempotencia.

### Bloque 6 — Contratos, países y dinero

TASKs: `090-093`, `100-103`.

- Shared fixtures Android ↔ Backend.
- Matriz PA / VE Digital / VE HKA-20.
- Inventario único de money/rounding boundaries.
- Idempotencia y fiscal matrices con casos críticos.

### Bloque 7 — Coverage por ratchets pequeños

Mantener TASK-023 y elevar thresholds gradualmente, enfocándose en core/domain/application y workflows críticos, no en tests artificiales para incrementar porcentaje.

Punto de partida:

- Android: ~15.7747%.
- Backend: ~46.526415%.

### Bloque 8 — Cierre de static quality

TASKs Android `070-075` y final de `022` Backend.

- Android Detekt baseline: `246 -> 0`.
- Backend Detekt: `1.415 -> 0`.
- Suppressions no justificadas: `-> 0`.
- ktlint: 0 findings.
- architecture tests verdes.

### Bloque 9 — Performance, reliability y documentación

TASKs: `120-123`, `130-132`.

Solo después de estabilizar boundaries y critical tests, para evitar optimizar/documentar una arquitectura todavía cambiante.

---

## 7. Definición exacta de “terminado”

No se debe declarar `10/10` solo porque compile o porque los gates actuales estén verdes. Deben cumplirse simultáneamente las siguientes métricas.

### Android = 10/10

- `DependencyContainer` desde UI/ViewModels: **0**; permitido únicamente en el composition root explícito si la arquitectura lo requiere.
- Violaciones `ui -> data`: **0**.
- Violaciones `domain -> data/ui`: **0**.
- Dependencias cíclicas: **0**, demostradas por architecture test/gate reproducible.
- Features que incumplen la arquitectura objetivo: **0 de 16**.
- Detekt raw findings: **0** sin esconder deuda nueva.
- Baseline Detekt: **0 entradas / eliminado**.
- Suppressions no justificadas: **0**.
- ktlint findings: **0**.
- Features con gaps de tests: **0**.
- Coverage global: **>=85% lineal y >=80% branches**.
- Workflows críticos: **>=90% lineal y >=90% branches** en el conjunto acordado de payment/caja/cart/credit notes/mesas/sync y lógica fiscal crítica.
- Todas las transiciones Room soportadas tienen migration tests reproducibles.
- Todo Worker crítico tiene tests de retry, duplicación e idempotencia.
- Shared fixtures Android ↔ Backend pasan en ambos proyectos.
- Matriz PA / VE Digital / VE HKA-20 completamente verde para serialization, money/rounding y workflows fiscales aplicables.
- Consistencia Route/Screen + ViewModel + UiState/UiAction/UiEffect donde aplique: 100% de features objetivo.
- CI Android completamente verde en tests, architecture, Detekt sin baseline, ktlint, lint, coverage y builds de flavors relevantes.

### Backend = 10/10

- Detekt findings: **0** sin baseline.
- Suppressions no justificadas: **0**.
- ktlint findings: **0**.
- Duplicaciones de tenant resolution en routes: **0**.
- Routes con `catch (Exception)` genérico: **0**; errores tipados y mapping central.
- Route files accediendo directamente a `DatabaseManager`: **0**.
- Routes llamando directamente PAC/infraestructura en Archetype B/C: **0**.
- HTTP externo ejecutado dentro de transacción SQL: **0**, demostrado por diseño y tests/architecture guard.
- Imports de Ktor/Exposed/infra dentro de domain: **0**.
- Composition root con ownership explícito y único; sin doble resolución Koin/manual para la misma dependencia.
- Todas las features B/C tienen `application/` cuando el archetype lo exige.
- Exposición de mensajes internos (`e.message`) hacia clientes: **0** salvo mensaje explícitamente sanitizado/tipado.
- Features con gaps de tests: **0**.
- Ktor integration tests cubren auth, tenant isolation, error mapping y endpoints críticos.
- Coverage global: **>=85% lineal y >=80% branches**.
- Critical domain/application: **>=90% lineal y >=90% branches**.
- Repositories/integrations críticos: **>=80% lineal y branches** o umbral superior acordado por riesgo.
- Tenant/security tests y architecture tests: 100% verdes.
- DTO ↔ domain y shared contract fixtures: 100% verdes para contratos soportados.
- CI Backend completamente verde incluyendo `test`, `detekt`, `ktlintCheck`, coverage, integration/architecture/security y `build`.

Solo cuando ambos conjuntos se cumplan sin baselines masivos, suppressions evasivas, contratos modificados accidentalmente ni regresiones PA/VE/HKA-20 puede afirmarse de forma defendible:

`Android = 10/10`

`Backend = 10/10`
