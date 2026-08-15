# ADR-006 — Composición de features Android

- Estado: Aceptado
- Fecha: 2026-08-14

## Contexto

Varias pantallas consumen `DependencyContainer` directamente, lo que convierte la UI en un service locator y hace desigual la composición entre features. Payment ya fue refactorizado y está cerrado.

## Decisión

La UI depende de domain y recibe ViewModels/dependencias desde un composition root explícito. `DependencyContainer` no se expande y debe dejar de consumirse directamente desde feature UI en TASK posteriores. `data` no se importa desde UI y `domain` no importa `data`/`ui`.

No se obliga a modularización Gradle inmediata ni a interfaces sin invariantes. Payment no será rediseñado dentro del primer bloque.

## Consecuencias

- Composición testeable y consistente.
- Architecture tests futuros podrán imponer las direcciones de dependencia.
- La migración es incremental y preserva comportamiento.
