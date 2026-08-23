# 03: Apertura atómica — auto-close + inserción + relectura en una fase

**What to build:** Tracer bullet principal. Cuando un cajero abre una caja que tenía sesión previa abierta, el workflow ejecuta auto-close, inserción de la nueva secuencia y relectura de estado en UNA sola fase de transacción: si la apertura falla después del auto-close, ambas operaciones revierten y la sesión anterior sigue abierta (único delta de comportamiento sancionado por la spec). La ruta de apertura cruza el workflow; ambos use cases desaparecen y el auto-close queda como capacidad interna del módulo. La semántica nueva queda caracterizada antes de cerrar el ticket.

**Blocked by:** 02 — Nace CajaSessionWorkflow: cierre y estado cruzan el nuevo seam.

**Status:** completada

- [x] Apertura compuesta en una sola fase transaccional; fallo posterior al auto-close revierte todo
- [x] Ambos use cases de caja eliminados; auto-close interno al workflow
- [x] Dos filas nuevas de caracterización H2: reversión de apertura fallida y auto-close no bloqueado por facturas temporales (semántica concurrente documentada en el KDoc del workflow: requiere índice único, fuera de alcance)
- [x] Reloj de negocio por país, numeración de secuencia y logs de diagnóstico preservados verbatim
- [x] Tests unitarios con fakes cubren: sin sesión previa, con auto-close fallido (no se abre nada), relectura fallida
- [x] Delta de comportamiento listado explícitamente en la descripción del PR (+ hallazgo: SELECT faltante de cod_estatus en lector de inventario, corregido y documentado)
- [x] Gates completos en verde
