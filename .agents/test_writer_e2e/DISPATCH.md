## 2026-08-31T17:41:11Z
You are the E2E Test Writer (Test Writer E2E Track) for the Amaxonia project.
Your working directory is `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\test_writer_e2e`.
Create your BRIEFING.md and progress.md in your working directory.

Read the user requirements at `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\ORIGINAL_REQUEST.md`.
Read the project plan at `D:\PROGRAMMING\Kotlin\Amaxonia\PROJECT.md`.
Read the test infrastructure guide at `D:\PROGRAMMING\Kotlin\Amaxonia\TEST_INFRA.md`.
Read the contracts report at `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\explorer_contracts\handoff.md`.

Your objective is to design, write, and execute comprehensive 4-tier E2E tests for features F01 through F10:
- Tier 1: Feature Coverage (≥5 test cases per feature covering happy-path isolated behaviors)
- Tier 2: Boundary & Corner Cases (≥5 test cases per feature covering limits, 0 days, 31 days, >31 days reject, leap years, empty strings, null active caja, missing CUFE, zero amounts)
- Tier 3: Cross-Feature Pairwise Combinations (≥10 interaction tests across countries, flavors, payment methods, actions)
- Tier 4: Real-World Workloads & Scenarios (≥5 realistic workflows: Panama Cashier Full Day, Offline & Recovery, Electronic Invoice Failure & Resend, History Date Bounds & Navigation, Multi-Flavor Brand Integrity)

Write tests in appropriate test directories in `amaxoniaerp-backend` (e.g. `src/test/kotlin/com/amaxoniaerp/e2e/...` or existing feature test packages) and `amaxoniaerp-pos` (e.g. `app/src/test/java/com/amaxonia/pos/e2e/...`).
Ensure all tests run with standard Gradle test runners (`./gradlew test` in both repos).

When all 4 tiers of tests are written and verified, create `TEST_READY.md` at workspace root `D:\PROGRAMMING\Kotlin\Amaxonia\TEST_READY.md` following the template in `PROJECT.md`.

Write your complete structured handoff report to `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\test_writer_e2e\handoff.md`.
Send a completion message back when done.
