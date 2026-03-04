# Repository Guidelines

## Project Structure & Module Organization
This is a single-module Android app. The main module lives in `app/`.
- Kotlin/Compose source: `app/src/main/java/com/amaxonia/pos/...`
- Android resources (layouts, drawables, strings): `app/src/main/res/`
- Unit tests: `app/src/test/java/...`
- Instrumented tests: `app/src/androidTest/java/...`

## Build, Test, and Development Commands
Use the Gradle wrapper from the repo root:
- `./gradlew assembleDebug` builds a debug APK.
- `./gradlew installDebug` installs the debug build on a connected device/emulator.
- `./gradlew test` runs local JVM unit tests in `app/src/test`.
- `./gradlew connectedAndroidTest` runs instrumented tests on a device.
- `./gradlew lint` runs Android Lint (if enabled by the Android plugin).

## Coding Style & Naming Conventions
- Language: Kotlin with Jetpack Compose.
- Indentation: 4 spaces; follow standard Kotlin formatting.
- Naming: classes and composables in `UpperCamelCase`, functions/vars in `lowerCamelCase`.
- Keep UI components and state classes paired in feature folders (e.g., `ui/login`, `ui/products`).
- No repository-wide formatter/linter config is present; use Android Studio “Reformat Code”.

## Testing Guidelines
- Unit tests use JUnit (`testImplementation(libs.junit)`).
- Instrumented tests use AndroidX test runner and Espresso.
- Name tests with clear intent (e.g., `LoginViewModelTest`, `ExampleInstrumentedTest`).
- Run relevant tests before submitting UI or ViewModel changes.

## Commit & Pull Request Guidelines
- No Git history is available in this workspace, so no enforced commit convention is documented.
- Recommended: short, imperative commit subjects (e.g., “Add product form validation”).
- PRs should include a brief description, test commands run, and screenshots for UI changes.

## Configuration & Local Setup
- `local.properties` is present for SDK paths; keep it local and uncommitted.
- The app targets Android SDK 36 and requires Java 11 (see `app/build.gradle.kts`).
