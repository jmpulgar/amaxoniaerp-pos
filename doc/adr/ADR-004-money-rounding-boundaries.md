# ADR-004 — Boundaries de dinero y redondeo

- Estado: Aceptado
- Fecha: 2026-08-14

## Contexto

POS y backend atraviesan UI, JSON, persistencia y fronteras fiscales. Un refactor puede introducir regresiones aun cuando sólo cambie dónde se convierte o redondea un monto.

## Decisión

Dinero conserva representación, escala, orden de operaciones y modo de redondeo del comportamiento vigente. El redondeo se realiza en boundaries explícitos y no se dispersa por UI/routes/adapters. Nuevas reglas monetarias no usan `Double` de forma implícita.

Un refactor arquitectónico no cambia impuestos, descuentos, totales, crédito/CxC ni formatos fiscales/HTTP. Cualquier cambio de fórmula requiere iniciativa funcional separada.

## Consecuencias

- Las migraciones de fronteras exigen characterization tests monetarios.
- DTO/domain/persistence adapters deben hacer conversiones explícitas.
- La arquitectura no se usa para justificar cambios de cálculo.
