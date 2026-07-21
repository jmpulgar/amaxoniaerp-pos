# Plan mínimo para llevar el POS Android a 10/10

**Fecha:** 2026-07-20  
**Alcance principal:** `amaxoniaerp-pos/`  
**Base auditada:** `174bc1889d5c7905dce3e40b5d86e8f365c2a426`

## 1. Objetivo

Llevar el frontend Android del POS a un nivel práctico de **10/10 operativo**, entendido como un sistema confiable, verificable y apto para producción, con todos los gates definidos en este documento aprobados mediante evidencia.

El objetivo no es alcanzar una perfección teórica ni eliminar toda la deuda técnica. El objetivo es cerrar los riesgos que puedan afectar:

- Dinero.
- Integridad de ventas.
- Duplicación de cobros o facturas.
- Fiscalización.
- Seguridad.
- Recuperación después de fallos.
- Separación entre tenants.
- Publicación y operación del release.

## 2. Estado y regla de salida

- Línea base histórica de la auditoría: **3.2/10; no apto para producción**.
- La puntuación histórica no se recalcula después de cada ítem.
- La salida depende exclusivamente de los gates binarios definidos en este documento.
- No se reabre la auditoría completa durante la ejecución.
- Solo se trabaja el siguiente ítem no aprobado.
- No se libera a producción hasta cerrar los bloqueantes confirmados, los riesgos de release y las pruebas externas obligatorias.
- Los riesgos P2/P3 que no afecten dinero, fiscalidad, seguridad o recuperación pueden quedar documentados con responsable y fecha.

## 3. Principios obligatorios

1. Cada venta debe tener una identidad canónica durable antes de producir cualquier efecto externo.
2. Un timeout nunca debe iniciar automáticamente un segundo cobro sin reconciliar el primero.
3. Online, offline, retry, HTTP 409, reinicio y process death deben conservar la misma identidad de venta.
4. Una operación pendiente de un tenant nunca debe procesarse con el contexto o las credenciales de otro.
5. Un documento fiscal emitido nunca debe reimprimirse automáticamente por un retry.
6. Ningún callback HKA no autenticado o no reconciliado debe aprobar un pago.
7. Dos workers no deben poder reclamar y procesar la misma fila simultáneamente.
8. Las migraciones deben conservar datos e invariantes desde todas las versiones desplegadas.
9. Los cálculos y contratos monetarios críticos deben usar representación decimal explícita.
10. Ningún release debe publicarse sin validación firmada, minificada, reproducible y observable.

## 4. Decisión previa de modelo y esquema

Antes de implementar el ítem 1, se debe definir el modelo final compartido para:

- Identidad canónica de venta.
- Tenant.
- Estado de sincronización backend.
- Estado del pago gateway.
- Estado fiscal.
- Reconciliación.
- Leases de workers.
- Información necesaria para recuperación después de process death.

Los cambios de Room derivados de los ítems 1 al 6 deben consolidarse en una sola nueva versión de base de datos siempre que sea técnicamente seguro.

No se deben crear migraciones sucesivas por cada ítem si el modelo final puede definirse antes de comenzar.

## 5. Backlog mínimo, en orden

| # | Cambio indispensable | Riesgo que cierra | Criterio binario de aceptación | Coste | Estado (Código) |
| ---: | --- | --- | --- | --- | --- |
| 1 | Hacer `idFactura` la identidad durable y única desde antes del primer efecto externo; reutilizarla en online, offline, timeout, retry y process death. | PAY-001, OFF-001 | Un timeout posterior al commit y un reinicio nunca crean otra venta ni otro identificador. | M | **APROBADO** |
| 2 | Reconciliar HTTP 409 y timeout consultando por `idFactura`; el backend debe devolver o permitir obtener la venta existente. | INT-BE-001 | Dos solicitudes concurrentes con el mismo identificador convergen en una sola factura y el POS termina en estado confirmado o reconciliado. | M | **APROBADO** |
| 3 | Persistir el tenant en el ledger y en todas las colas; cada worker opera exclusivamente con el tenant de la fila. | TEN-001 | Una venta pendiente del tenant A no puede enviarse al tenant B después de cambiar de sesión. | M | **APROBADO** |
| 4 | Convertir el claim de cada worker en una operación atómica con lease. | CON-001 | Dos workers concurrentes procesan una fila una sola vez. | S | **APROBADO** |
| 5 | Implementar estados fiscales explícitos: `NOT_APPLICABLE`, `PENDING_PRINT`, `PRINTED_PENDING_CONFIRM`, `CONFIRMED` y `FAILED`; persistir el resultado fiscal antes de confirmar con backend. | FIS-001, FIS-002 | Un crash después de imprimir reanuda la confirmación y nunca reimprime automáticamente. | L | **APROBADO** |
| 6 | Persistir el intento HKA antes de abrir RapidPay y validar el callback mediante nonce, origen, firma o mecanismo oficialmente soportado. Si HKA no ofrece autenticación suficiente, reconciliar el resultado mediante una consulta durable antes de aprobar. | HKA-001, HKA-002 | Un intent falso nunca aprueba un pago; un callback legítimo después de process death queda asociado o reconciliado mediante el mecanismo oficial disponible. | L | **NO INICIADO** |
| 7 | Probar migraciones Room desde las versiones 10, 11, 12 y 13 hacia la versión final resultante, usando datos representativos de los ítems 1 al 6. | MIG-001 | Todas las rutas soportadas migran sin pérdida y conservan identificadores, tenant, estados, índices e invariantes. | M | **APROBADO** |
| 8 | Corregir la prueba de locale y eliminar `Double` únicamente de cálculos, persistencia y contratos monetarios críticos; usar representación decimal y redondeo explícito por moneda. | MONEY-001 | La caracterización, serialización y redondeo son iguales en los locales soportados y la suite unitaria queda 100 % verde. | M | **PARCIAL** |
| 9 | Rotar la clave de firma expuesta, validar el SDK HKA con páginas de 16 KB y compilar releases minificados de ambos flavors. | SEC-001, REL-001 | La clave anterior deja de ser válida o reutilizable; lint, alineación, firma y smoke test de release quedan aprobados. | M + proveedor | **BLOQUEADO EXTERNAMENTE** |
| 10 | Ejecutar la matriz física y activar telemetría mínima para ventas ambiguas, duplicados, retries, fiscalización y HKA. | TEST-001, OBS-001 | No quedan bloqueantes críticos abiertos; las alertas y el runbook se prueban en un piloto controlado. | M + hardware | **BLOQUEADO EXTERNAMENTE** |

`S`, `M` y `L` expresan esfuerzo relativo. Ningún ítem debe absorber refactors cosméticos, upgrades o deuda ajena a su criterio de aceptación.

## 6. Pruebas exactas por paquete

### Ítem 1 — Identidad canónica

- Backend fake confirma la venta y después produce timeout.
- El usuario reintenta.
- La aplicación se reinicia.
- Se verifica un solo `idFactura`.
- Se verifica un solo efecto remoto.
- Se verifica que el ledger converge a un estado terminal correcto.

### Ítem 2 — Reconciliación backend

- Enviar dos requests simultáneos con el mismo `idFactura`.
- Verificar una sola factura creada.
- Verificar que la segunda respuesta sea reconciliable.
- Verificar que el POS consulte o recupere la venta existente.
- Verificar que no quede en retry infinito.

### Ítem 3 — Seguridad por tenant

- Crear una venta offline en el tenant A.
- Cerrar sesión o cambiar al tenant B.
- Ejecutar el worker.
- Verificar que no exista tráfico de A usando credenciales o endpoints de B.
- Verificar que la fila permanezca aislada hasta disponer del contexto correcto.

### Ítem 4 — Claims atómicos

- Lanzar dos ejecuciones concurrentes por cada tipo de worker.
- Verificar un solo claim exitoso.
- Verificar un solo efecto externo.
- Verificar expiración y recuperación del lease.

### Ítem 5 — Saga fiscal

Simular cortes:

1. Antes de imprimir.
2. Durante la impresión.
3. Después de imprimir y antes de persistir.
4. Después de persistir y antes de confirmar.
5. Durante la confirmación backend.
6. Después de confirmar.

En cada escenario se debe verificar:

- Transición válida.
- Estado durable.
- Ninguna reimpresión automática.
- Reconciliación explícita cuando el resultado sea ambiguo.

### Ítem 6 — HKA RapidPay

- Intent externo falsificado.
- Callback legítimo.
- Recreación de `Activity`.
- Muerte completa del proceso.
- Timeout.
- Resultado aprobado.
- Resultado rechazado.
- Callback duplicado.
- Callback tardío.

Se debe verificar que:

- Un intent no autenticado nunca aprueba.
- Un intento durable puede recuperarse.
- Un resultado ambiguo no se convierte en aprobación automática.
- Un callback legítimo converge a un estado terminal.

### Ítem 7 — Migraciones Room

Crear bases sembradas en v10, v11, v12 y v13 con:

- Ventas confirmadas.
- Ventas pendientes.
- Ventas offline.
- Estados fiscales.
- Intentos HKA.
- Tenants distintos.
- Leases activos y expirados.

Después de migrar se deben comprobar:

- Filas.
- Claves.
- Índices.
- Restricciones.
- Defaults.
- Identidad de venta.
- Tenant.
- Estados.
- Integridad referencial.

### Ítem 8 — Dinero y locales

Validar como mínimo:

- `es_VE`.
- `es_CO`.
- `en_US`.
- Valores límite.
- Redondeo fiscal.
- Serialización backend.
- Persistencia Room.
- Impresión.
- Comparaciones.
- Reintentos.

No se debe usar igualdad aproximada para valores monetarios críticos.

### Ítem 9 — Release

Ejecutar para ambos flavors:

- Lint de release.
- Compilación release.
- Minificación R8.
- Resource shrinking.
- Validación de reglas ProGuard.
- Validación de firma.
- Inspección de librerías nativas.
- Compatibilidad con páginas de 16 KB.
- Instalación del artefacto.
- Smoke test del artefacto firmado y minificado.

### Ítem 10 — Matriz física y operación

Usar:

- Backend controlado.
- Dispositivo Android físico.
- Impresora fiscal.
- HKA RapidPay real o sandbox oficial.
- Artefacto release firmado y minificado.

Probar:

- Pago aprobado.
- Pago rechazado.
- Red intermitente.
- Timeout.
- Reinicio de Activity.
- Muerte de proceso.
- Callback tardío.
- Callback duplicado.
- Crash antes y después de imprimir.
- Reconciliación.
- Cambio de tenant.
- Alertas.
- Runbook operativo.

## 7. Gates para declarar 10/10

- [ ] Ítems 1 al 10 aprobados y revisados.
- [ ] Compilación 100 % verde en CI.
- [ ] Unit tests 100 % verdes en CI.
- [ ] Detekt 100 % verde en CI.
- [ ] Ktlint 100 % verde únicamente si ya está configurado como gate del proyecto.
- [ ] Android lint 100 % verde para las variantes requeridas.
- [ ] Pruebas instrumentadas críticas 100 % verdes en emuladores y APIs soportadas.
- [ ] Migraciones desde todas las versiones desplegadas 100 % verdes.
- [ ] Releases firmados y minificados de ambos flavors compilados e instalados.
- [ ] Smoke test aprobado sobre los artefactos reales de release.
- [ ] SDK HKA compatible con páginas de 16 KB.
- [ ] Clave de firma anterior revocada, rotada o técnicamente inutilizable.
- [ ] Hardware fiscal y HKA aprobados con logs, tickets y dumps de base anonimizados.
- [ ] Piloto controlado sin P0/P1 abiertos.
- [ ] Alertas verificadas para ventas ambiguas, duplicados, retries, fiscalización y callbacks HKA.
- [ ] Runbook operativo probado.
- [ ] Riesgos P2/P3 restantes documentados con responsable y fecha.
- [ ] Ningún riesgo pendiente afecta dinero, fiscalidad, seguridad o recuperación.

## 8. Protocolo de ejecución de consumo mínimo

1. Ejecutar un solo ítem por turno, citando únicamente su número.
2. No reenviar la auditoría ni este documento completo.
3. Consultar CodeGraph una sola vez si está disponible.
4. Si CodeGraph no está disponible, usar búsqueda textual, referencias del lenguaje y lectura selectiva.
5. Leer únicamente archivos y símbolos implicados.
6. Escribir primero una prueba que demuestre el fallo.
7. Aplicar después el cambio mínimo que haga pasar la prueba.
8. Durante los ítems 1 al 8, ejecutar solo las pruebas y análisis del módulo o clase afectados.
9. Ejecutar la matriz completa después de los ítems 6, 9 y 10.
10. Reutilizar un único backend fake.
11. Reutilizar fixtures de Room.
12. Reutilizar helpers de concurrencia, process death y reconciliación.
13. No hacer refactors, upgrades ni documentación fuera del criterio activo.
14. No introducir nuevas herramientas de calidad salvo que sean necesarias para un gate definido.
15. Detenerse cuando el criterio binario del ítem esté aprobado.
16. Informar cualquier dependencia externa antes de implementar un workaround especulativo.

La respuesta de cada turno debe contener únicamente:

- Archivos modificados.
- Pruebas agregadas o modificadas.
- Comandos ejecutados.
- Resultados.
- Criterio de aceptación: aprobado o no aprobado.
- Único bloqueo restante.

## 9. Prompt corto para cada ejecución

```text
Implementa el ítem N de docs/auditoria-produccion-pos-2026-07-20.md.

Trabaja únicamente dentro de amaxoniaerp-pos/, salvo que el ítem requiera explícitamente validar o modificar un contrato backend.

Aplica el cambio mínimo. Escribe primero la prueba que demuestre el fallo. No realices trabajo fuera del criterio de aceptación activo.

Devuelve únicamente:
- archivos modificados;
- pruebas;
- comandos ejecutados;
- resultados;
- criterio aprobado/no aprobado;
- único bloqueo restante.
```

## 10. Validaciones que requieren recursos externos

| Recurso | Evidencia obligatoria |
| --- | --- |
| Backend controlado | Requests y responses correlacionados por `idFactura`, consulta de reconciliación y prueba concurrente. |
| Android físico | Video y logs de process death, reconexión, callback tardío y smoke release. |
| Impresora fiscal | Tickets, estado persistido y logs de cortes antes, durante y después de imprimir. |
| HKA RapidPay | Resultado aprobado y rechazado, callback frío, callback duplicado y rechazo de callback falso. |
| Firma | Evidencia de rotación, revocación o inutilización de la clave anterior. |
| Proveedor HKA | AAR compatible con páginas de 16 KB y documentación del mecanismo oficial de callback o reconciliación. |
| Observabilidad | Eventos, alertas y runbook probados con correlación por venta. |

## 11. Orden exacto de ejecución

1. Definir el modelo final de persistencia y estados.
2. Ítem 1 — Identidad canónica.
3. Ítem 2 — Reconciliación backend.
4. Ítem 3 — Seguridad por tenant.
5. Ítem 4 — Claims atómicos.
6. Ítem 5 — Saga fiscal.
7. Ítem 6 — HKA seguro y recuperable.
8. Ítem 7 — Migraciones finales.
9. Ítem 8 — Dinero y locales.
10. Ítem 9 — Release, firma y SDK.
11. Ítem 10 — Hardware, piloto y observabilidad.

## 12. Paralelización permitida

Solo pueden ejecutarse en paralelo cuando no modifiquen el mismo modelo o esquema:

- La coordinación con el proveedor HKA puede comenzar desde el inicio.
- La rotación de firma puede comenzar en paralelo con los ítems 1 al 8.
- El diseño de telemetría puede comenzar después de estabilizar los estados canónicos.
- La preparación del entorno físico puede comenzar desde el inicio.
- La implementación de los ítems 1 al 6 no debe paralelizarse si comparten entidades, DAOs, migraciones o máquinas de estado.

## 13. Decisión de release

**Bloquear producción.**

### QA interno

Después de aprobar los ítems 1 al 9:

- Se permite únicamente QA interno.
- Debe usarse un artefacto release firmado y minificado.
- No se permite exposición a usuarios reales.
- No se considera piloto.

### Piloto controlado

Solo se permite después de:

- Completar las pruebas físicas críticas del ítem 10.
- Validar HKA y fiscalización con hardware real.
- Activar telemetría mínima.
- Probar alertas y runbook.
- Confirmar que no existen P0/P1 abiertos.

### Producción

Producción requiere:

- Ítem 10 completo.
- Todos los gates en verde.
- Evidencia almacenada.
- Aprobación formal de QA.
- Release firmado y minificado validado.
- Cero P0/P1 abiertos.
- Observabilidad y recuperación operativas.