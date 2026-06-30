# Plan de Integracion: Facturacion Electronica Panama con The Factory HKA

## Estado: COMPLETADO - Compilacion exitosa

## Arquitectura

```
com/amaxoniaerp/features/electronicinvoice/
    domain/
        ElectronicInvoiceStrategy.kt       - Interfaz Strategy (multi-pais)
        ElectronicInvoiceModels.kt         - DTOs estandarizados (PacResponse, Result, etc.)
        ElectronicInvoiceExceptions.kt     - Excepciones propias del modulo
        VenezuelaInvoiceStrategy.kt        - No-op para VE (no modifica codigo VE)
    application/
        ElectronicInvoiceProcessorFactory.kt  - Factory que decide VE vs PA
        PanamaInvoiceProcessor.kt             - Orquestador del flujo PA
    data/
        ElectronicInvoiceTables.kt         - Tablas Exposed de solo-lectura para FE
        ElectronicInvoiceRepository.kt     - Lee DB + escribe CUFE/QR post-envio
    pac/
        PanamaElectronicInvoiceClient.kt   - Interfaz Port (multi-PAC)
        thefactory/
            TheFactoryHkaRestClient.kt     - Adapter concreto REST
            TheFactoryHkaDtos.kt           - DTOs respuesta The Factory
            TheFactoryHkaPayloadDtos.kt    - Estructura exacta del JSON de envio
            TheFactoryHkaPayloadBuilder.kt - Builder: DB -> DTO (logica de negocio)
    route/
        ElectronicInvoiceRoutes.kt         - Endpoints REST
```

## Archivos Modificados (existentes)

| Archivo | Cambio |
|---------|--------|
| `gradle/libs.versions.toml` | Agregar ktor-client libs |
| `build.gradle.kts` | Agregar dependencias ktor-client |
| `FacturasTable.kt` | Agregar columnas `qr`, `nroProtocoloAutorizacion` a FacturasTablePA |
| `Routing.kt` | Registrar electronicInvoiceRoutes |
| `ProcessSaleUseCase.kt` | Invocar FE como paso final sincrono para PA |

## Archivos de Venezuela Modificados: CERO

## Patrones de Diseno Aplicados

- **Strategy Pattern**: ElectronicInvoiceStrategy (VE no-op vs PA real)
- **Adapter/Port Pattern**: PanamaElectronicInvoiceClient (interfaz) + TheFactoryHkaRestClient (impl)
- **Builder Pattern**: TheFactoryHkaPayloadBuilder (DB entities -> JSON payload)
- **Factory Pattern**: ElectronicInvoiceProcessorFactory (decide estrategia por pais)

## Decisiones Tecnicas

1. **HTTP Client**: Ktor Client con CIO engine (async, misma version que el server)
2. **Serialization**: kotlinx-serialization-json (consistente con el resto del proyecto)
3. **Tablas de solo-lectura**: FEFacturaReadTable, FEParametrosReadTable, etc. No duplican tablas existentes
4. **Autenticacion PAC**: JWT via /api/Autenticacion antes de cada envio
5. **Correlativos**: Tabla `correlativos` donde campo='numeroDocumentoFiscal', incremento atomico
6. **Envio automatico**: Integrado en ProcessSaleUseCase como paso final sincrono solo para PA

## Progreso

- [x] PASO 0: Dependencias ktor-client
- [x] PASO 0.2: ElectronicInvoiceTables.kt
- [x] PASO 1: Strategy + VenezuelaInvoiceStrategy + Factory
- [x] PASO 2: PanamaElectronicInvoiceClient + DTOs estandarizados
- [x] PASO 3: TheFactoryHkaRestClient + TheFactoryHkaDtos
- [x] PASO 4.1: TheFactoryHkaPayloadDtos.kt
- [x] PASO 4.2: TheFactoryHkaPayloadBuilder.kt
- [x] PASO 4.5: ElectronicInvoiceRepository.kt
- [x] PASO 5: PanamaInvoiceProcessor.kt
- [x] PASO 6: ElectronicInvoiceRoutes.kt + Routing.kt
- [x] PASO 7: FacturasTablePA columnas extras
- [x] PASO 8: Integrar en ProcessSaleUseCase
- [x] Verificar compilacion (BUILD SUCCESSFUL)

## Migracion SQL requerida en BD Panama

```sql
ALTER TABLE factura ADD COLUMN qr TEXT NULL;
ALTER TABLE factura ADD COLUMN nroProtocoloAutorizacion VARCHAR(100) NULL;
```
