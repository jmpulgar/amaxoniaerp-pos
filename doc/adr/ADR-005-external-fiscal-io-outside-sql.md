# ADR-005 — I/O fiscal externo fuera de transacciones SQL

- Estado: Aceptado
- Fecha: 2026-08-14

## Contexto

PAC, HKA y otros servicios externos tienen latencia y resultados inciertos. Mantener una transacción SQL abierta durante ese I/O amplía locks y mezcla dos modelos de fallo.

## Decisión

No se ejecuta I/O externo dentro de una transacción SQL. Los workflows se separan en fases cortas: persistir/reservar intención, cerrar transacción, ejecutar I/O, persistir resultado en otra transacción y reconciliar estados inciertos explícitamente.

Mover un workflow existente a este patrón sólo se hará con characterization tests y sin alterar contratos ni comportamiento fiscal.

## Consecuencias

- Menos locks de larga duración.
- Estados inciertos se modelan en vez de ocultarse mediante una transacción imposible de extender al sistema externo.
- PAC/HKA quedan detrás de ports/adapters y fuera de routes.
