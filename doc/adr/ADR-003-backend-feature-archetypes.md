# ADR-003 — Archetypes de features backend

- Estado: Aceptado
- Fecha: 2026-08-14

## Contexto

Los features tienen distinta complejidad. Forzar `application/` a cada CRUD produciría capas ceremoniales, pero workflows complejos no deben dejar negocio dentro de Ktor routes.

## Decisión

Se adoptan tres archetypes:

1. Query/CRUD simple: `route -> repository` es válido si no hay lógica no trivial.
2. Workflow/business operation: `route -> application -> domain ports -> adapters`.
3. External integration: `application/domain port -> adapter -> PAC/HKA/API`.

Un feature usa un módulo profundo cuando coordina múltiples dependencias, invariantes, idempotencia/retry, DB + I/O externo, variantes por país o lógica que merece tests independientes del framework.

## Consecuencias

- Consistencia por complejidad, no por cantidad artificial de capas.
- Routes complejas deben adelgazar progresivamente.
- PAC/HKA no se invocan directamente desde routes.
