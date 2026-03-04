# Repository Guidelines

## Project Structure & Module Organization
- `src/main/kotlin` contains Ktor application code (entry point in `Application.kt`).
- `src/main/resources` holds configuration (`application.yaml`), logging (`logback.xml`), and OpenAPI docs (`openapi/documentation.yaml`).
- `src/test/kotlin` contains unit/integration tests (example: `ApplicationTest.kt`).
- Build tooling lives at the repo root: `build.gradle.kts`, `settings.gradle.kts`, `gradlew`/`gradlew.bat`.

## Build, Test, and Development Commands
- `./gradlew run` starts the Ktor server locally on port `8080`.
- `./gradlew test` runs the Kotlin/JUnit test suite.
- `./gradlew build` compiles and packages the application.
- `./gradlew buildFatJar` creates an executable fat JAR.
- `./gradlew buildImage` or `./gradlew runDocker` builds/runs the Docker image.

## Coding Style & Naming Conventions
- Kotlin code follows standard Kotlin style: 4-space indentation, no tabs.
- File and class names use `UpperCamelCase` (e.g., `UsersSchema.kt`); functions/variables use `lowerCamelCase`.
- Keep Ktor modules organized by concern (e.g., `Routing.kt`, `Security.kt`, `Databases.kt`).
- No formatter is configured; use IntelliJ/Kotlin default formatting.

## Testing Guidelines
- Frameworks: `kotlin.test` with JUnit (`kotlin.test.junit` dependency).
- Place tests in `src/test/kotlin` mirroring production package structure.
- Name tests with `*Test` suffix (e.g., `ApplicationTest`).
- Run locally with `./gradlew test` before opening a PR.

## Commit & Pull Request Guidelines
- No Git history is available in this checkout, so commit conventions are unspecified.
- Suggested default: Conventional Commits (`feat:`, `fix:`, `chore:`) unless the team specifies otherwise.
- PRs should include a short description, steps to test, and any related issue links.
- Include screenshots only if API docs or behavior changes are user-visible.

## Configuration & Security Tips
- Runtime config is in `src/main/resources/application.yaml` and sourced from env vars.
- Example env values live in `.env` (DB and JWT settings); do not commit real secrets.
- Database settings are under `db.config` and expect `DB_CONFIG_*` variables.
- All feature tables must be queried from the company database name stored in `nomempresa.bd` (never from `bd_contabilidad` or `bd_nomina`).
