# 02: Nace CajaSessionWorkflow — cierre y estado cruzan el nuevo seam

**What to build:** Primer tracer bullet del módulo profundo de sesión de caja. Un cajero cierra su caja y consulta su estado exactamente igual que antes desde el POS, pero detrás del endpoint las guardas de negocio ("la secuencia ya se encuentra cerrada", "existen facturas temporales pendientes por procesar", "secuencia no encontrada") viven ahora dentro del nuevo workflow de sesión, compuesto sobre las primitivas transaccionales bajo una fase única. La ruta HTTP de cierre y el GET de estado consumen el workflow; el método pass-through del use case de cierre desaparece. Su capacidad de auto-close sobrevive temporalmente porque la apertura aún lo necesita.

**Blocked by:** 01 — Extraer primitivas transaccionales del repositorio de caja.

**Status:** completada

- [x] El workflow expone cierre y estado; la interfaz completa queda documentada como 3 operaciones (abrir llega en el ticket siguiente)
- [x] Guardas de negocio ejecutándose en el workflow, fuera de la capa de datos
- [x] Tests unitarios con fakes en memoria (sin H2 ni Ktor): ruta feliz, ya cerrada, facturas temporales bloqueantes, secuencia no encontrada, pliegue monetario preservado
- [x] Suite de caracterización verde sin modificaciones (mismas aserciones; único cambio mecánico: el punto de entrada de cierre apunta al workflow)
- [x] Mensajes de error y mapeos HTTP byte-idénticos a los actuales
- [x] Gates completos en verde
