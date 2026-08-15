# Repository Guidelines

## Project Structure & Module Organization
This is currently a single-module Android app. The main module is `app/`.
- Kotlin/Compose source: `app/src/main/java/com/amaxonia/pos/...`
- Android resources: `app/src/main/res/`
- Unit tests: `app/src/test/java/...`
- Instrumented tests: `app/src/androidTest/java/...`
- Architecture policy: `../doc/ARCHITECTURE.md`
- ADRs: `../doc/adr/`

Do not introduce Gradle feature modules only for visual symmetry. Module boundaries are enforced first through package/dependency rules and tests.

## Build, Test, and Quality Commands
Run from `amaxoniaerp-pos/` with Java 17 and Android SDK 36 available:
- `./gradlew test` — local JVM unit tests.
- `./gradlew detekt` — Detekt quality gate.
- `./gradlew ktlintCheck` — Kotlin formatting gate.
- `./gradlew assembleAmaxoniaDebug` — Amaxonia debug flavor.
- `./gradlew assembleBanescoVenezuelaDebug` — Banesco Venezuela debug flavor.
- `./gradlew assembleListoerpDebug` — ListoERP debug flavor.
- `./gradlew connectedAndroidTest` — instrumented tests when a device/emulator is required.
- `./gradlew lint` — Android Lint.

Before integration, run the relevant tests plus Detekt, ktlint and the required flavor build(s). CI executes all three debug flavors.

## Architecture Rules
The canonical dependency direction is:

```text
ui -> domain
data -> domain
composition -> ui + domain + data
domain -> Kotlin/JDK only
```

Do not add:
- `ui -> data` imports;
- `domain -> data` or `domain -> ui` imports;
- new direct `DependencyContainer` usage from Screens/ViewModels;
- service-location from domain code.

Dependencies should be supplied explicitly from the composition boundary. Do not add interfaces, wrappers or use cases for trivial one-line delegation with no invariant or boundary value.

The Payment refactor is closed. Do not redesign Payment architecture unless a TASK explicitly requires a behavior-neutral quality-tooling correction.

## Coding Style & Naming Conventions
- Kotlin + Jetpack Compose, 4-space indentation and standard Kotlin formatting.
- Classes/composables: `UpperCamelCase`; functions/properties: `lowerCamelCase`.
- Keep feature UI/state/action/effect types together when they form one cohesive feature.
- Do not add `@Suppress`, `@file:Suppress`, ignores, baseline entries or disabled rules to make quality gates pass.
- Existing Detekt baseline entries are debt to remove progressively, not precedent for adding more.

## Testing Guidelines
- Unit tests use JUnit/Kotlin test as configured by the project.
- Instrumented tests use AndroidX test infrastructure.
- Test behavior and invariants, not implementation trivia.
- Cover critical money, idempotency, offline/retry and PA / VE digital / VE HKA-20 paths when touching them.
- Coverage is ratcheted from the measured baseline; do not add trivial tests only to increase a percentage.

## Business-Safety Constraints
Architecture/quality work must not change business decisions, calculations, credit/CxC, HTTP/JSON contracts, Room schema/migrations, PAC, HKA-20 behavior, fiscal payloads or multi-country policy unless a separate approved functional TASK explicitly says so.

Never modify `.env`, `.env.development`, secrets, keystores or production configuration for architecture work.

## Commit & Pull Request Guidelines
- Keep commits small, scoped and reversible.
- PRs state what changed, evidence/tests run and whether any business decision changed.
- Required CI checks are documented in `../doc/ARCHITECTURE.md`.
- Do not force-push shared branches.

## Configuration & Local Setup
`local.properties` is local SDK configuration and must remain uncommitted. The application targets Android SDK 36; CI uses Java 17.
