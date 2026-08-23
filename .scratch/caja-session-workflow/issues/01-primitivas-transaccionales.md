# 01: Extraer primitivas transaccionales del repositorio de caja

**What to build:** Prefactor puro que hace posible la sesión de caja como módulo profundo: las operaciones que hoy están incrustadas dentro de los métodos públicos del repositorio de caja quedan disponibles como primitivas con alcance de transacción (leer fila de secuencia, contar facturas temporales pendientes, escribir cierre con reescritura de sus detalles, resolver el siguiente código de secuencia, insertar apertura). El comportamiento observable no cambia ni un bit: mismos mensajes de error, mismas respuestas HTTP, misma suite de caracterización pasando intacta. Ningún consumidor externo nota la diferencia.

**Blocked by:** None (can start immediately).

**Status:** completada

- [x] Las primitivas existen con alcance de transacción explícito y son componibles bajo una transacción del llamador
- [x] La suite de caracterización de apertura/cierre pasa sin ninguna modificación (comportamiento congelado)
- [x] Rutas HTTP, payloads y mensajes de error idénticos a los actuales
- [x] Gates completos en verde: test, detekt, ktlint, build y ratchet de cobertura JaCoCo
- [x] Diff revisado con respuesta "NO" a ¿cambia alguna decisión de negocio?
