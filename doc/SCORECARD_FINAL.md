# Scorecard final 10/10 (FASE 13)

Evaluación contra `doc/PLAN_ARCHITECTURE_10_10.md` §8. Regla aplicada: se
marca sólo lo demostrado por tests/gates o cierres formales de fase.

## Arquitectura

- [x] 0 architecture-test violations — `CompositionBoundaryArchitectureTest`
      (Android) + `FeatureDependencyArchitectureTest`/`TenantSeamArchitectureTest`
      (backend) verdes; sondas negativas verificadas (FASE 11).
- [x] 0 direct `DependencyContainer` usages desde feature UI — ídem.
- [x] 1 Android composition strategy — composition root único (FASE 5).
- [x] 1 backend composition strategy — DI manual vía `composition/AppDependencies`.
- [x] 1 tenant seam — `CompanyRequestContext`; resolución congelada en core/tenant.
- [x] 0 duplicated tenant validation — `TenantSeamArchitectureTest`.
- [x] 0 external HTTP inside SQL transaction — flujos staged preservados
      (cierre FASE 3; `CreditNotePanamaStagedFlowTest`).
- [x] 0 business-heavy Ktor routes — normalización FASE 4 + contrato de errores.
- [x] todos los features conformes a su archetype — cierre FASE 4.

## Consistencia

- [x] naming uniforme — detekt/ktlint en verde ambos lados.
- [x] errors uniforme — `ErrorCategory` + StatusPages central (contraste
      documentado de los 500 legacy en tests de caracterización).
- [x] logging uniforme — CallId/correlation plugin; prefijos por flujo ([FE]).
- [x] feature structure uniforme según archetype — FASE 4.
- [x] AGENTS/ARCHITECTURE actuales — AGENTS por repo vigentes;
      ARCHITECTURE.md política canónica; runbooks nuevos (FASE 13).
- [x] DTO/domain boundaries documentados — `doc/MONEY_INVENTORY.md`,
      `contracts/README.md`.
- [x] money policy cumplida — boundary BigDecimal + caracterización
      (deuda residual clasificada pendiente de decisión: caja backend,
      resúmenes, carrito display; ver inventario).

## Calidad/testing

- [x] Android Detekt PASS sin baseline debt (baseline eliminada FASE 7).
- [x] Android ktlint PASS.
- [x] Backend Detekt PASS (weighted = 0 desde FASE 7).
- [x] Backend ktlint PASS.
- [x] Android unit tests PASS (3 flavors).
- [x] Backend tests PASS.
- [x] all debug flavors compile (Amaxonia/BanescoVenezuela/Listoerp).
- [x] contract tests PASS — `contracts/` consumidos por ambos lados (FASE 9).
- [x] migration tests PASS — Room 10→17 (`AppDatabaseMigrationTest`).
- [x] idempotency matrix PASS — FASE 10 TASK-102 (8 casos cubiertos o
      documentados).
- [x] multi-country matrix PASS — PA / VE digital / VE HKA-20 (FASE 9/10).
- [x] coverage targets PASS — Kover Android + JaCoCo backend (ratchets).
- [ ] **CI required on main** — NO demostrable: workflows presentes pero la
      protección de rama fue ANULADA (TASK-024 fuera de alcance, decisión del
      usuario). Único ítem abierto del scorecard.
- [x] no ignored/flaky tests — suite verde en repetidas corridas; sin @Ignore.
- [x] no suppressions usadas para esconder deuda — excepciones únicas son las
      sancionadas del Payment cerrado (`PaymentScreen`, `DashboardScreen`),
      documentadas e intocables por regla.

## Regresión funcional (preservación verificada por tests)

- [x] PA preserved — fixtures + procesador + ticket formatter.
- [x] VE digital preserved — `VenezuelaInvoiceStrategyTest`, matriz contratos.
- [x] VE HKA-20 preserved — `ProcessSaleUseCaseSelectionTest`, RUNBOOK_HKA.
- [x] contado preserved — fixtures contado + CreditDecision.
- [x] crédito/CxC preserved — credit-sale/partial-collection fixtures,
      `ProcessSaleCreditTest`, redondeo CxC parcial.
- [x] mesas preserved — fixtures mesas, `MesasRoutesValidationTest`.
- [x] offline preserved — cola durable + sincronizador (RUNBOOK_OFFLINE_SYNC).
- [x] fiscal preserved — colas fiscales, PAC/HKA matrices (RUNBOOK_PAC/HKA).
- [x] gateway preserved — callbacks duplicados/tardíos idempotentes.
- [x] idempotency preserved — matriz completa TASK-102.

## Veredicto

Arquitectura: **PASS** · Consistencia: **PASS** ·
Calidad/testing: **PASS condicionado** (único pendiente: exigencia de CI en
main por branch protection anulada — decisión de producto, no técnica).
