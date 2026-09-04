# BRIEFING — 2026-08-31T17:41:00Z

## Mission
Design, write, and execute comprehensive 4-tier E2E tests for features F01 through F10 covering backend and POS repos, and generate TEST_READY.md.

## 🔒 My Identity
- Archetype: test writer
- Roles: specialist, qa
- Working directory: D:\PROGRAMMING\Kotlin\Amaxonia\.agents\test_writer_e2e
- Original parent: bdb03428-3c80-4d10-ac23-887615aa5628
- Milestone: E2E Test Suite Creation (F01-F10)

## 🔒 Key Constraints
- Tier 1: Feature Coverage (≥5 test cases per feature covering happy-path isolated behaviors for F01..F10)
- Tier 2: Boundary & Corner Cases (≥5 test cases per feature covering limits, 0 days, 31 days, >31 days reject, leap years, empty strings, null active caja, missing CUFE, zero amounts)
- Tier 3: Cross-Feature Pairwise Combinations (≥10 interaction tests across countries, flavors, payment methods, actions)
- Tier 4: Real-World Workloads & Scenarios (≥5 realistic workflows: Panama Cashier Full Day, Offline & Recovery, Electronic Invoice Failure & Resend, History Date Bounds & Navigation, Multi-Flavor Brand Integrity)
- Must write test code only — no implementation modifications
- All tests must pass with `./gradlew test` in both repos
- Create TEST_READY.md at workspace root when complete

## Current Parent
- Conversation ID: bdb03428-3c80-4d10-ac23-887615aa5628
- Updated: 2026-08-31T17:41:00Z

## Task Summary
- **What to build**: 4-Tier E2E test suites in backend and pos covering F01..F10, boundary cases, cross-feature combinations, and real-world workflows.
- **Success criteria**: All tests compile and pass via Gradle test runners; TEST_READY.md generated; handoff.md populated.
- **Interface contracts**: `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\explorer_contracts\handoff.md` and `PROJECT.md`
- **Code layout**: `amaxoniaerp-backend` and `amaxoniaerp-pos` test packages.

## Loaded Skills
- Source: `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\skills\kotlin-specialist\SKILL.md`
  - Local copy: `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\test_writer_e2e\skills\kotlin-specialist.md`
  - Core methodology: Kotlin multiplatform, coroutines, sealed types, clean DSL design
- Source: `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\skills\android-kotlin\SKILL.md`
  - Local copy: `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\test_writer_e2e\skills\android-kotlin.md`
  - Core methodology: Android testing, Coroutines, MockK, Robolectric / JUnit testing
- Source: `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\skills\tdd\SKILL.md`
  - Local copy: `D:\PROGRAMMING\Kotlin\Amaxonia\.agents\test_writer_e2e\skills\tdd.md`
  - Core methodology: Red-green-refactor, behavior-based test writing, edge case prioritization

## Quality Status
- **Build/test result**: Pending initial inspection and test authoring
- **Lint status**: 0 violations
- **Tests added/modified**: 0

## Key Decisions Made
- Partition tests logically between Backend API/Integration E2E tests and POS ViewModel/Repository/Robolectric UI E2E tests.

## Artifact Index
- D:\PROGRAMMING\Kotlin\Amaxonia\TEST_READY.md — Final test readiness summary
- D:\PROGRAMMING\Kotlin\Amaxonia\.agents\test_writer_e2e\handoff.md — Handoff report
