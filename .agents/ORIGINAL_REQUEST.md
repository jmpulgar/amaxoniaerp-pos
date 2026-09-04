# Original User Request

## 2026-08-31T17:30:07Z

Revisión técnica integral e implementación de ajustes, correcciones y mejoras en el módulo de Facturación POS (Android) y Backend Ktor: corrección del error de notas de crédito electrónicas de Panamá (`caja.cod_almacen`), ciclo de vida de facturas en espera / pendientes sin bloquear cierre de caja, filtros de fecha y caja activa en Historial y Selección de Factura, reimpresión de tickets, descarga de PDFs, y reenvío manual idempotente de facturas electrónicas sin duplicación de ventas.

Working directory: D:\PROGRAMMING\Kotlin\Amaxonia
Integrity mode: development

## Requirements

### R1. Facturas en estado "En Espera" y Cierre de Caja
- Asegurar que una factura no quede en estado "En Espera" como estado final de una operación normal.
- Toda factura que quede en "En Espera" debe figurar obligatoriamente en "Facturas Pendientes", con identificación clara de su estado incompleto y permitiendo su eliminación controlada desde dicho flujo.
- La existencia de facturas en estado "En Espera" no debe bloquear ni impedir el cierre de caja.

### R2. Filtros de Fecha en "Seleccionar Factura" (Nota de Crédito) e "Historial de Facturas"
- En "Seleccionar Factura", cargar por defecto únicamente las facturas del día actual.
- Permitir seleccionar rango Desde / Hasta mediante datepickers nativos/adecuados, con validación de rango (Hasta >= Desde) y un período máximo de consulta de 1 mes (bloqueando consultas superiores con mensaje descriptivo).
- En "Historial de Facturas", eliminar definitivamente los campos/filtros de Estatus, Campo y Sucursal (interfaz, DTOs, queries y lógica asociada sin dejar código muerto), e implementar el mismo selector y validación de fechas (Desde/Hasta, max 1 mes, datepicker).
- Ambos filtros deben aplicarse a nivel de consulta en backend/base de datos y no solo como filtrado visual en memoria.

### R3. Filtrado por Caja de la Apertura Activa en Historial de Facturas
- El historial de facturas debe consultar y mostrar exclusivamente las facturas asociadas a la caja vinculada a la apertura de caja actualmente activa (`Apertura de caja -> Caja -> Factura`).
- El filtrado debe ejecutarse a nivel de consulta para impedir la carga o visualización accidental de facturas de otras cajas.

### R4. Corrección de Causa Raíz en Nota de Crédito Electrónica (Panamá)
- Investigar y corregir el error SQL `Unknown column 'caja.cod_almacen' in 'field list'` que ocurre al generar notas de crédito electrónicas en Panamá.
- Identificar la consulta SQL, joins, entidades y repositorios involucrados en Ktor backend y Android POS, corrigiendo el mapeo para obtener el dato real de la base de datos sin hardcodear ni romper flujos de otros países.

### R5. Reimpresión de Tickets y Descarga de PDFs desde Historial
- Permitir la acción de reimprimir ticket para facturas en el historial, reutilizando la información y plantilla original sin crear nuevas operaciones comerciales ni alterar la factura existente.
- Permitir descargar o visualizar el PDF asociado a la factura cuando esté disponible, manejando adecuadamente estados pendientes o de error.

### R6. Reenvío Manual de Facturas Electrónicas y Separación Comercial/Electrónica
- Incorporar en el historial indicadores visuales para facturas con procesamiento electrónico pendiente o fallido (sin CUFE, sin QR, sin número de documento electrónico válido, error de transmisión).
- Implementar la acción "Reenviar factura electrónica" de manera idempotente sobre la factura local existente: verificar estado actual, evitar reenvíos de facturas ya aceptadas, reenviar al proveedor electrónico sin crear una segunda factura comercial y actualizar CUFE, QR y estado con la respuesta obtenida.

### R7. Quality Gates y Compatibilidad Multi-Flavor
- Mantener la integridad de los flujos de facturación de todos los países.
- En `amaxoniaerp-pos` (Java 17, Android SDK 36), deben pasar con éxito: `./gradlew test`, `./gradlew detekt`, `./gradlew ktlintCheck`, `./gradlew lint`, `./gradlew :app:koverVerifyAmaxoniaDebug`, `./gradlew assembleAmaxoniaDebug`, `./gradlew assembleBanescoVenezuelaDebug`, `./gradlew assembleListoerpDebug`.
- En `amaxoniaerp-backend` (Java 21), deben pasar con éxito: `./gradlew test`, `./gradlew detekt`, `./gradlew ktlintCheck`, `./gradlew jacocoTestCoverageVerification`, `./gradlew build`.

## Verification Resources
- Test suites unitarios e instrumentados en `amaxoniaerp-pos` y `amaxoniaerp-backend`.
- Herramientas de análisis estático y cobertura: Detekt, ktlint, Android Lint, Kover, JaCoCo.
- Validación de compilación multi-flavor (Amaxonia, Banesco Venezuela, Listo ERP).

## Acceptance Criteria

### Facturas en Espera y Cierre de Caja
- [ ] Las facturas en "En Espera" se listan en "Facturas Pendientes" y permiten su eliminación cuando corresponda.
- [ ] Una factura en estado "En Espera" no impide ni bloquea el proceso de cierre de caja.

### Filtros de Fecha y Caja
- [ ] "Seleccionar Factura" carga por defecto las facturas de la fecha actual.
- [ ] La consulta por rango de fechas (Desde/Hasta) en "Seleccionar Factura" y en "Historial de Facturas" valida que Hasta >= Desde y restringe el rango a máximo 1 mes en consulta backend.
- [ ] Los campos Estatus, Campo y Sucursal quedan eliminados de la UI y del pipeline de datos de "Historial de Facturas" sin código muerto.
- [ ] "Historial de Facturas" filtra estrictamente por la caja correspondiente a la apertura de caja activa.

### Nota de Crédito Panamá
- [ ] La generación de Nota de Crédito Electrónica para Panamá se completa con éxito sin lanzar `Unknown column 'caja.cod_almacen' in 'field list'`.
- [ ] La corrección no altera ni degrada los flujos de facturación electrónica de otros países.

### Reimpresión, PDF y Reenvío Electrónico
- [ ] La acción de reimpresión de ticket funciona con los datos originales y no crea registros duplicados.
- [ ] La descarga/visualización del PDF de la factura maneja correctamente disponibilidad y errores.
- [ ] Las facturas con emisión electrónica incompleta o fallida son identificables en el historial.
- [ ] El reenvío manual de factura electrónica es idempotente, actualiza CUFE/QR/estado sobre la factura existente y rechaza reenvíos de facturas ya aceptadas sin generar ventas duplicadas.

### Quality Gates & Build
- [ ] Todos los quality gates de Android POS pasan: `test`, `detekt`, `ktlintCheck`, `lint`, `koverVerifyAmaxoniaDebug`, y los 3 `assemble*Debug`.
- [ ] Todos los quality gates de Backend Ktor pasan: `test`, `detekt`, `ktlintCheck`, `jacocoTestCoverageVerification`, `build`.
- [ ] Ninguna regla de cobertura o análisis estático fue relajada ni deshabilitada.
