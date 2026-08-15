# ADR-001 — Constructor DI manual y composition root

- Estado: Aceptado
- Fecha: 2026-08-14

## Contexto

El backend contiene Koin como dependencia, pero el grafo efectivo también se construye manualmente. Android expone `DependencyContainer` ampliamente. Mantener dos estrategias de resolución hace implícitas las dependencias y dificulta probar fronteras.

## Decisión

La estrategia canónica es constructor DI manual. Un composition root explícito crea adapters, repositories, use cases/application services y entrypoints. Las clases reciben dependencias por constructor; no buscan servicios globalmente.

Koin no se extiende a nuevos componentes y podrá retirarse cuando una TASK posterior demuestre que no forma parte del grafo requerido. `DependencyContainer` se mantiene sólo como transición y debe converger hacia el composition root, sin nuevos accesos desde feature UI.

## Consecuencias

- Dependencias visibles y sustituibles en tests.
- Routing/UI dejan de ser service locators progresivamente.
- No se cambia comportamiento ni se obliga a introducir interfaces triviales.
