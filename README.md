# Amaxonia

POS multi-país (Panamá / Venezuela digital / Venezuela HKA-20) con backend Ktor.

## Layout

```text
amaxoniaerp-pos/       App Android (Kotlin + Jetpack Compose, single-module :app)
amaxoniaerp-backend/   Backend Ktor (Java 21) con features por paquete
contracts/             Fixtures wire compartidos Android↔backend (fuente única)
doc/                   PLAN, ARCHITECTURE.md, ADRs, runbooks, inventarios
.github/workflows/     CI
```

Docs clave: `doc/PLAN_ARCHITECTURE_10_10.md` (plan maestro y estado por fase),
`doc/ARCHITECTURE.md` (política arquitectónica), `doc/MONEY_INVENTORY.md`
(política monetaria), `doc/runbooks/*.md` (operación).

## Build y tests

Requisitos: JDK 17 (Android), JDK 21 (backend), Android SDK 36.

Android (desde `amaxoniaerp-pos/`):

```text
./gradlew test                                # unit tests JVM
./gradlew detekt ktlintCheck                  # quality gates
./gradlew assembleAmaxoniaDebug               # flavors: Amaxonia | BanescoVenezuela | Listoerp
./gradlew :app:koverVerifyAmaxoniaDebug       # ratchet de cobertura
```

Backend (desde `amaxoniaerp-backend/`):

```text
./gradlew test
./gradlew detekt ktlintCheck
./gradlew jacocoTestCoverageVerification      # ratchet de cobertura
./gradlew run                                 # servidor local
```

Gates completos requeridos antes de cada cierre de fase (ver
`doc/PLAN_ARCHITECTURE_10_10.md`). CI ejecuta los tres debug flavors y ambos
ratchets de cobertura.

## Contratos wire

`contracts/` es la fuente única de los payloads HTTP/JSON entre POS y backend;
ambos lados tienen `ContractFixtureTest` que consumen los MISMOS archivos.
Si Android y backend discrepan en un payload, es una decisión de contrato que
se escala — no se "arregla" en un test. Ver `contracts/README.md`.

## Reglas de oro

- Sin cambios de negocio/cálculo/crédito/PAC/HKA/multi-país sin TASK funcional
  aprobada; los cambios de esquema (Room/Exposed) son criterio de parada.
- Prohibido añadir `@Suppress`, ignores o baselines para pasar gates.
- La resolución de tenant converge en un único seam
  (`CompanyRequestContext` backend; composition root Android).
- Nunca modificar `.env`, secretos ni configuración de producción.
