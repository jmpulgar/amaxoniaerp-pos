# Repository Guidelines

## Project Structure & Module Organization
- `src/main/kotlin` contains the Ktor application and feature packages.
- `src/main/resources` contains runtime configuration, logging and OpenAPI resources.
- `src/test/kotlin` mirrors production packages for unit/integration tests.
- Architecture policy: `../doc/ARCHITECTURE.md`; ADRs: `../doc/adr/`.
- Build tooling: `build.gradle.kts`, `settings.gradle.kts`, Gradle wrapper.

## Build, Test, and Quality Commands
Run from `amaxoniaerp-backend/` with Java 21:
- `./gradlew run` — starts the Ktor application locally.
- `./gradlew test` — backend test suite.
- `./gradlew detekt` — static-analysis gate.
- `./gradlew ktlintCheck` — Kotlin formatting gate.
- `./gradlew build` — compile, test and package.
- `./gradlew buildFatJar` — executable fat JAR when needed.
- `./gradlew jacocoTestReport` — JaCoCo coverage report (`build/reports/jacoco/test/jacocoTestReport.xml`).
- `./gradlew jacocoTestCoverageVerification` — coverage ratchet gate; fails if line coverage drops below the measured baseline (COVEREDRATIO >= 0.46526415).

Do not add quality baselines, suppressions, ignores or disabled rules to make a gate green. Fix measured debt explicitly.

## Backend Feature Archetypes
Use the minimum structure justified by behavior:

1. Query/CRUD simple: `route -> repository` is acceptable when no non-trivial business workflow exists.
2. Workflow/business operation: `route -> application -> domain ports -> adapters`.
3. External integration: `application/domain port -> adapter -> PAC/HKA/API`.

Do not add `application/`, interfaces or wrappers for symmetry alone. A deep module is justified by invariants, multi-resource coordination, idempotency/retry, DB + external I/O, country-specific behavior or logic that deserves framework-independent tests.

## Tenancy
Company/tenant resolution must converge on one typed seam (`CompanyRequestContext`). Do not add new per-route copies of JWT/`token_type`/`admin_db`/`Company-DB`/`country_code` resolution.

All feature tables must use the company database stored in `nomempresa.bd`; never substitute `bd_contabilidad` or `bd_nomina`.

## Error Rules
Routes should validate HTTP input/context, call the application operation and map its result. Do not expose raw exception, SQL, PAC or stack-trace details to clients. Prefer typed error categories and central `StatusPages` mapping. Do not add generic `catch (Exception)` to routes as a convenience.

## Transaction and External-I/O Rules
Never perform PAC, HKA, HTTP or other external I/O while a SQL transaction is open. Use staged operations: short DB transaction, external call, short persistence/reconciliation transaction. Moving an existing critical workflow requires characterization tests and must preserve current behavior.

## Coding Style & Composition
- Kotlin standard style, 4-space indentation, no tabs.
- `UpperCamelCase` for classes/files; `lowerCamelCase` for functions/properties.
- Constructor DI manual is the canonical composition strategy. Do not expand Koin into a parallel graph.
- Routing must not become a service locator or construct feature infrastructure ad hoc.
- Domain code should remain independent of Ktor and Exposed when the feature is a deep workflow boundary.

## Testing Guidelines
- Tests use Kotlin test/JUnit as configured by the build.
- Mirror production package structure and use `*Test` suffix.
- Prioritize domain/application invariants, money, idempotency, tenancy, contract/security and PA / VE digital / VE HKA-20 behavior.
- Coverage is ratcheted from measured reality; do not write trivial tests solely to increase percentage.

## Business-Safety Constraints
Architecture/quality work must not change business rules, calculations, credit/CxC, HTTP/JSON contracts, schema/migrations, PAC, HKA-20, fiscal behavior or multi-country policy unless a separate approved functional TASK explicitly requests it.

Never modify `.env`, `.env.development`, secrets or production configuration for architecture work.

## Commit & Pull Request Guidelines
- Keep commits scoped, reversible and tied to one TASK or guardrail.
- PRs include commands/evidence and state whether a business decision changed.
- Required CI checks are listed in `../doc/ARCHITECTURE.md`.
- Do not force-push shared branches.

## Configuration & Security
Runtime configuration is sourced from the existing configuration/env mechanism. Do not commit credentials or change production defaults as part of this plan.
