## 2026-08-31T17:31:47Z
You are the Contracts and Test Infra Spec Miner for the Amaxonia project.
Your working directory is `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\explorer_contracts`.
Create your BRIEFING.md and progress.md in your working directory.

Read the user requirements at `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\ORIGINAL_REQUEST.md`.

Investigate the project structure, shared contracts, multi-flavor setups, and quality gate configurations across `amaxoniaerp-pos` and `amaxoniaerp-backend`:
1. Cross-repo contracts and APIs:
   - Check `contracts/`, `doc/`, API routes in backend vs Retrofit/Ktor clients in POS.
   - Map all affected DTOs and endpoints for R1 to R6.
2. Multi-flavor setup in POS:
   - Examine `amaxonia`, `banescoVenezuela`, `listoerp` flavor dimensions, source sets, configs, and build variants.
3. Quality Gates & CI Configurations:
   - POS: Detekt config, ktlint rules, Android Lint baseline/rules, Kover verification configuration (`koverVerifyAmaxoniaDebug`), unit test runners.
   - Backend: Detekt config, ktlint rules, JaCoCo coverage configuration (`jacocoTestCoverageVerification`), unit/integration test runners.
4. Test Architecture & E2E Requirements:
   - Map existing test suites in both repos.
   - Formulate the Feature Inventory (all features R1-R7) and design the 4-tier E2E testing framework strategy (Category-Partition, BVA, Pairwise, Real-World Workloads) to be documented in TEST_INFRA.md.

Document all findings with exact paths, config snippets, and complete requirement mappings.
Write your complete structured handoff report to `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\explorer_contracts\handoff.md`.
Send a completion message back when done.
