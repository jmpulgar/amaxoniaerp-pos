Plan de Integración: Facturación Electrónica Panamá con The Factory HKA
Diagnóstico del Estado Actual
Tras analizar los 76 archivos Kotlin del backend, el sistema tiene:
Lo que ya existe y funciona:
- Arquitectura multi-país con Factory Pattern para tablas (FacturasTablePA, ParametrosGeneralesTablePA, etc.)
- FacturasTablePA ya tiene columnas cufe y fechaRecepcionDGI (vacías, nunca se escriben)
- El countryCode viaja en el JWT ("VE" o "PA") y se usa en toda la cadena
- ProcessSaleTransactionalRepository ya diferencia VE/PA al insertar facturas (líneas 552-563)
- CajaFormaPagoTable ya tiene columna FormaPagoFact (el código del catálogo DGI)
- ClientsTable ya tiene dv (dígito verificador), tipoContribuyente, tipoIdentificacionExtranjera
- SalesFacturaDetalleTable tiene _item_piva, _item_montodescuento, _item_preciosiniva, _item_totalconiva, _item_totalsiniva
- Framework: Ktor + Exposed ORM + kotlinx-serialization + Koin (DI) + HikariCP
Lo que NO existe aún:
- Ningún HTTP Client (no hay ktor-client-* en las dependencias) -- crítico para llamar al API de The Factory
- Ninguna lógica de facturación electrónica -- no hay Strategy, Adapter, Builder ni nada relacionado con PAC/DGI/CUFE
- No hay tabla correlativos dedicada -- el correlativo de factura vive en caja.factura_correlativo
- Columnas faltantes en FacturasTablePA -- faltan qr, nroProtocoloAutorizacion, y campos FE de la tabla factura que mencionaste (tipo_documento, NaturalezaOperacion, tipoOperacion, etc.)
- Columnas faltantes en ParametrosGeneralesTablePA -- faltan token_empresa, token_password, direccion_envio, tipoEmision, etc.
- No hay tabla de unidades de medida mapeada en el ORM
Decisión Arquitectónica: Dónde Vive el Nuevo Código
com/amaxoniaerp/
├── core/                          # (existente, no se toca)
├── features/
│   ├── facturas/                  # (existente, no se toca)
│   ├── sales/                     # (existente, no se toca)
│   └── electronicinvoice/         # <<<--- NUEVO FEATURE MODULE
│       ├── domain/
│       │   ├── ElectronicInvoiceStrategy.kt      # Interfaz Strategy
│       │   ├── ElectronicInvoiceModels.kt         # DTOs estandarizados (PacResponse, etc.)
│       │   └── ElectronicInvoiceExceptions.kt     # Excepciones propias
│       ├── application/
│       │   ├── ElectronicInvoiceProcessorFactory.kt  # Factory que decide VE vs PA
│       │   └── PanamaInvoiceProcessor.kt             # Orquestador PA (el Use Case)
│       ├── data/
│       │   ├── ElectronicInvoiceRepository.kt     # Lee DB, escribe CUFE/QR post-envío
│       │   └── ElectronicInvoiceTables.kt         # Tablas extendidas para FE (PA)
│       ├── pac/
│       │   ├── PanamaElectronicInvoiceClient.kt   # Interfaz Port (multi-PAC)
│       │   └── thefactory/
│       │       ├── TheFactoryHkaRestClient.kt     # Adapter concreto (HTTP)
│       │       ├── TheFactoryHkaDtos.kt           # DTOs del payload The Factory
│       │       └── TheFactoryHkaPayloadBuilder.kt # Builder DB -> DTO
│       └── route/
│           └── ElectronicInvoiceRoutes.kt         # Endpoints REST
Justificación: Se crea como un feature module nuevo (electronicinvoice/) siguiendo el patrón existente del proyecto (features/{domain}/data|domain|route). NO se modifica ningún archivo de Venezuela ni del flujo de ventas existente.
PASO 0: Dependencias y Configuración Base
0.1 — Agregar Ktor HTTP Client al proyecto 
Archivo: amaxoniaerp-backend/build.gradle.kts
Agregar al version catalog o directamente:
implementation("io.ktor:ktor-client-core:$ktor_version")
implementation("io.ktor:ktor-client-cio:$ktor_version")      // Engine async
implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
implementation("io.ktor:ktor-client-logging:$ktor_version")
implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
Impacto: Solo agrega dependencias. No modifica código existente.
0.2 — Extender tablas Exposed para campos FE en Panamá
Archivo nuevo: features/electronicinvoice/data/ElectronicInvoiceTables.kt
Crear tablas/vistas de solo-lectura para leer campos FE de la base de datos que el ORM actual no mapea. Esto es necesario porque la tabla factura en la DB de Panamá tiene columnas como tipo_documento, NaturalezaOperacion, tipoOperacion, formatoCAFE, entregaCAFE, envioContenedor, tipoVenta, observacion, etc., que BaseSalesFacturaTable o FacturasTablePA no exponen actualmente.
Enfoque: Crear un object FEFacturaReadTable : Table("factura") de solo-lectura que mapea únicamente las columnas necesarias para FE, sin duplicar ni interferir con las tablas existentes. Mismo patrón para parametros_generales (tokens, tipoEmision, etc.) y caja/sucursal (CodigoSucursalEmisor, puntoFacturacionFiscal).
// Solo para leer campos extra de FE. No se usa para escritura general.
object FEFacturaReadTable : Table("factura") {
    val idFactura = varchar("id_factura", 36)
    val tipoDocumento = varchar("tipo_documento", 5).nullable()
    val naturalezaOperacion = varchar("NaturalezaOperacion", 5).nullable()
    val tipoOperacion = varchar("tipoOperacion", 5).nullable()
    val formatoCAFE = varchar("formatoCAFE", 5).nullable()
    val entregaCAFE = varchar("entregaCAFE", 5).nullable()
    val envioContenedor = varchar("envioContenedor", 5).nullable()
    val tipoVenta = varchar("tipoVenta", 5).nullable()
    val observacion = varchar("observacion", 300).nullable()
    val montoItemsFactura = decimal("montoItemsFactura", 20, 2)
    val ivaTotalFactura = decimal("ivaTotalFactura", 20, 2)
    val totalTotalFactura = decimal("TotalTotalFactura", 20, 2)
    val totalizarDescuentoGlobal = decimal("totalizar_descuento_global", 20, 2)
    val idCliente = varchar("id_cliente", 36)
    val idCaja = varchar("id_caja", 36)
    val fechaFactura = varchar("fechaFactura", 20).nullable()
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", 20).nullable()
    // ... solo los campos que necesitamos para el Builder
    override val primaryKey = PrimaryKey(idFactura)
}
object FEParametrosGeneralesReadTable : Table("parametros_generales") {
    val codEmpresa = integer("cod_empresa")
    val tokenEmpresa = varchar("token_empresa", 500).nullable()
    val tokenPassword = varchar("token_password", 500).nullable()
    val direccionEnvio = varchar("direccion_envio", 500).nullable()
    val tipoEmision = varchar("tipoEmision", 5).nullable()
    val destinoOperacion = varchar("destinoOperacion", 5).nullable()
    val procesoGeneracion = varchar("procesoGeneracion", 5).nullable()
    val codigoSucursalEmisor = varchar("codigoSucursalEmisor", 20).nullable()
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", 10).nullable()
    val fechaInicioContingencia = varchar("fechaInicioContingencia", 30).nullable()
    val motivoContingencia = varchar("motivoContingencia", 300).nullable()
}
object FESucursalReadTable : Table("sucursal") {
    val id = integer("id")
    val codigoSucursalEmisor = varchar("codigo_sucursal_emisor", 20).nullable()
    override val primaryKey = PrimaryKey(id)
}
// Tabla para pagos asociados a factura (iterar formas de pago)
object FECajaNuevaDetalleReadTable : Table("caja_nueva_detalle") {
    val cajaDetalleId = varchar("caja_detalle_id", 36)
    val cajaId = varchar("caja_id", 36)
    val idFormaPago = integer("id_forma_pago").nullable()
    val monto = decimal("monto", 10, 2).nullable()
    override val primaryKey = PrimaryKey(cajaDetalleId)
}
Nota para validación contigo: Necesito confirmar qué columnas FE existen realmente en la tabla factura de la DB de Panamá. Las que listé arriba son las que mencionaste en tu mapeo (tipo_documento, NaturalezaOperacion, etc.). Si alguna no existe en la DB, debemos decidir: ¿Se agregan como columnas nuevas o se reciben como parámetros en la petición al endpoint?
PASO 1: Definición de Interfaces Core (Strategy Pattern)
1.1 — Interfaz Strategy
Archivo: features/electronicinvoice/domain/ElectronicInvoiceStrategy.kt
package com.amaxoniaerp.features.electronicinvoice.domain
import org.jetbrains.exposed.sql.Database
/**
 * Strategy para procesamiento de facturación electrónica por país.
 * Venezuela no requiere envío a PAC (usa impresora fiscal local).
 * Panamá require envío a PAC y recepción de CUFE.
 */
interface ElectronicInvoiceStrategy {
    val countryCode: String
    suspend fun processElectronicInvoice(
        database: Database,
        invoiceId: String,
    ): ElectronicInvoiceResult
}
1.2 — Venezuela "No-Op" Strategy
Archivo: features/electronicinvoice/domain/VenezuelaInvoiceStrategy.kt
class VenezuelaInvoiceStrategy : ElectronicInvoiceStrategy {
    override val countryCode = "VE"
    override suspend fun processElectronicInvoice(
        database: Database,
        invoiceId: String,
    ): ElectronicInvoiceResult {
        // Venezuela usa impresora fiscal local, no envía a PAC.
        // Retorna éxito inmediato; la confirmación fiscal se hace via 
        // PATCH /facturas/{id}/confirmacion-fiscal (flujo existente, NO se toca).
        return ElectronicInvoiceResult.NotApplicable(countryCode)
    }
}
Regla de oro respetada: No se modifica ni una línea del flujo VE existente.
1.3 — Factory
Archivo: features/electronicinvoice/application/ElectronicInvoiceProcessorFactory.kt
class ElectronicInvoiceProcessorFactory(
    private val panamaProcessor: PanamaInvoiceProcessor,
) {
    fun forCountry(countryCode: String): ElectronicInvoiceStrategy =
        when (countryCode.uppercase()) {
            "PA" -> panamaProcessor
            "VE" -> VenezuelaInvoiceStrategy()
            else -> VenezuelaInvoiceStrategy() // fallback seguro
        }
}
PASO 2: Estandarización de PACs para Panamá (Port/Adapter)
2.1 — Interfaz Port (multi-PAC)
Archivo: features/electronicinvoice/pac/PanamaElectronicInvoiceClient.kt
package com.amaxoniaerp.features.electronicinvoice.pac
/**
 * Port: contrato que debe cumplir cualquier PAC de Panamá.
 * Hoy: TheFactoryHKA. Mañana: podría ser otro proveedor.
 */
interface PanamaElectronicInvoiceClient {
    
    suspend fun authenticate(credentials: PacCredentials): Result<PacAuthToken>
    
    suspend fun sendDocument(
        token: PacAuthToken,
        payload: DocumentoElectronicoDto,
    ): Result<PacResponse>
    
    suspend fun downloadPdf(
        token: PacAuthToken,
        cufe: String,
    ): Result<ByteArray>
}
2.2 — DTOs Estandarizados (independientes del PAC)
Archivo: features/electronicinvoice/domain/ElectronicInvoiceModels.kt
// --- Request genérico ---
data class DocumentoElectronicoDto(
    val documento: DocumentoWrapper,
)
// --- Responses estandarizados (no dependen de The Factory) ---
data class PacCredentials(
    val usuario: String,  // token_empresa
    val clave: String,    // token_password
    val baseUrl: String,  // direccion_envio
)
data class PacAuthToken(val token: String, val expiresAt: Long)
data class PacResponse(
    val exitoso: Boolean,
    val codigo: String,
    val mensaje: String,
    val cufe: String?,
    val qr: String?,
    val fechaRecepcionDGI: String?,
    val nroProtocoloAutorizacion: String?,
)
sealed class ElectronicInvoiceResult {
    data class Success(val cufe: String, val qr: String?, val fechaDGI: String?) : ElectronicInvoiceResult()
    data class Failure(val codigo: String, val mensaje: String) : ElectronicInvoiceResult()
    data class NotApplicable(val country: String) : ElectronicInvoiceResult()
}
PASO 3: Implementación del PAC "The Factory HKA" (Adapter)
3.1 — REST Client
Archivo: features/electronicinvoice/pac/thefactory/TheFactoryHkaRestClient.kt
class TheFactoryHkaRestClient(
    private val httpClient: HttpClient,  // Ktor HttpClient inyectado
) : PanamaElectronicInvoiceClient {
    override suspend fun authenticate(credentials: PacCredentials): Result<PacAuthToken> {
        return runCatching {
            val response = httpClient.post("${credentials.baseUrl}/api/Autenticacion") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("usuario" to credentials.usuario, "clave" to credentials.clave))
            }
            
            if (!response.status.isSuccess()) {
                throw PacCommunicationException("Auth fallida: HTTP ${response.status}")
            }
            
            val body = response.body<TheFactoryAuthResponse>()
            PacAuthToken(
                token = body.token ?: throw PacCommunicationException("Token vacío"),
                expiresAt = System.currentTimeMillis() + 3_600_000 // 1h cache
            )
        }
    }
    override suspend fun sendDocument(
        token: PacAuthToken,
        payload: DocumentoElectronicoDto,
    ): Result<PacResponse> {
        return runCatching {
            val response = httpClient.post("${baseUrl}/api/Enviar") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer ${token.token}")
                setBody(payload)
            }
            
            val body = response.body<TheFactoryEnviarResponse>()
            
            PacResponse(
                exitoso = body.codigo == "200" || response.status == HttpStatusCode.OK,
                codigo = body.codigo ?: "",
                mensaje = body.mensaje ?: body.resultado ?: "",
                cufe = body.cufe,
                qr = body.qr,
                fechaRecepcionDGI = body.fechaRecepcionDGI,
                nroProtocoloAutorizacion = body.nroProtocoloAutorizacion,
            )
        }
    }
    override suspend fun downloadPdf(token: PacAuthToken, cufe: String): Result<ByteArray> {
        // Implementar cuando se necesite
        return Result.failure(NotImplementedError("PDF download pendiente"))
    }
}
Notas técnicas:
- Usa Result<T> de Kotlin para error handling sin excepciones descontroladas
- El HttpClient se configura una vez en el módulo Koin con timeouts, retry, logging
- La baseUrl viene de parametros_generales.direccion_envio (demo: https://demointegracion.thefactoryhka.com.pa)
3.2 — DTOs específicos de The Factory (Response)
Archivo: features/electronicinvoice/pac/thefactory/TheFactoryHkaDtos.kt
// Respuesta de /api/Autenticacion
@Serializable
data class TheFactoryAuthResponse(
    val token: String? = null,
    val mensaje: String? = null,
)
// Respuesta de /api/Enviar
@Serializable
data class TheFactoryEnviarResponse(
    val codigo: String? = null,
    val resultado: String? = null,
    val mensaje: String? = null,
    val cufe: String? = null,
    val qr: String? = null,
    val fechaRecepcionDGI: String? = null,
    val nroProtocoloAutorizacion: String? = null,
    val fechaLimite: String? = null,
)
PASO 4: DTOs del Payload y Builder (Data Mapping)
4.1 — Data Classes del Payload (estructura exacta del JSON de envío)
Archivo: features/electronicinvoice/pac/thefactory/TheFactoryHkaPayloadDtos.kt
Aquí se modela la estructura exacta del JSON que espera /api/Enviar. Todos los campos son String? con @JsonInclude(NON_NULL) (o el equivalente en kotlinx-serialization: encodeDefaults = false + defaults null).
@Serializable
data class DocumentoWrapper(
    val documento: DocumentoElectronico,
)
@Serializable
data class DocumentoElectronico(
    val codigoSucursalEmisor: String,
    val tipoSucursal: String? = null,
    val datosTransaccion: DatosTransaccion,
    val listaItems: List<ItemFE>,
    val totalesSubTotales: TotalesSubTotales,
    // Opcionales:
    val serialDispositivo: String? = null,
    // ... otros nodos opcionales (infoLogistica, infoEntrega, etc.)
)
@Serializable
data class DatosTransaccion(
    val tipoEmision: String,
    val tipoDocumento: String,
    val numeroDocumentoFiscal: String,
    val puntoFacturacionFiscal: String,
    val fechaEmision: String,    // ISO format
    val naturalezaOperacion: String,
    val tipoOperacion: String,
    val destinoOperacion: String,
    val formatoCAFE: String,
    val entregaCAFE: String,
    val envioContenedor: String,
    val procesoGeneracion: String,
    val tipoVenta: String,
    val informacionInteres: String? = null,
    val cliente: ClienteFE,
    // Opcionales controlados:
    val fechaInicioContingencia: String? = null,
    val motivoContingencia: String? = null,
    val fechaSalida: String? = null,
    val datosFacturaExportacion: DatosExportacion? = null,
    val listaDocsFiscalReferenciados: List<DocFiscalRef>? = null,
    val listaAutorizadosDescargaFEyEventos: List<AutorizadoDescarga>? = null,
)
@Serializable
data class ClienteFE(
    val tipoClienteFE: String,
    val tipoContribuyente: String,
    val numeroRUC: String,
    val digitoVerificadorRUC: String,
    val razonSocial: String,
    val direccion: String? = null,
    val codigoUbicacion: String? = null,
    val telefono1: String,
    val correoElectronico1: String,
    // Extranjeros:
    val tipoIdentificacion: String? = null,
    val nroIdentificacionExtranjero: String? = null,
    // ... resto de campos opcionales
)
@Serializable
data class ItemFE(
    val descripcion: String,
    val codigo: String,
    val unidadMedida: String = "und",
    val cantidad: String,
    val precioUnitario: String,
    val precioUnitarioDescuento: String? = null,
    val precioItem: String? = null,
    val valorTotal: String,
    val tasaITBMS: String,
    val valorITBMS: String,
    val tasaISC: String? = null,
    val valorISC: String? = null,
    val listaItemOTI: List<ItemOTI>? = null,
    // ... resto opcionales
)
@Serializable
data class TotalesSubTotales(
    val totalPrecioNeto: String,
    val totalITBMS: String,
    val totalISC: String? = null,
    val totalMontoGravado: String? = null,
    val totalDescuento: String? = null,
    val totalFactura: String,
    val totalValorRecibido: String,
    val vuelto: String? = null,
    val tiempoPago: String,
    val nroItems: String,
    val totalTodosItems: String,
    val listaDescBonificacion: List<DescBonificacion>? = null,
    val listaFormaPago: List<FormaPagoFE>,
    val retencion: RetencionFE? = null,
    val listaTotalOTI: List<TotalOTI>? = null,
)
// ... y sub-DTOs: FormaPagoFE, ItemOTI, TotalOTI, DescBonificacion, RetencionFE, etc.
4.2 — Payload Builder (la lógica de negocio pesada)
Archivo: features/electronicinvoice/pac/thefactory/TheFactoryHkaPayloadBuilder.kt
Este es el componente más complejo. Recibe los datos crudos de la DB y aplica todas las reglas de transformación que especificaste.
class TheFactoryHkaPayloadBuilder {
    fun build(context: InvoiceFEContext): DocumentoWrapper {
        return DocumentoWrapper(
            documento = DocumentoElectronico(
                codigoSucursalEmisor = resolveCodigoSucursal(context),
                datosTransaccion = buildDatosTransaccion(context),
                listaItems = buildItems(context),
                totalesSubTotales = buildTotales(context),
            )
        )
    }
    // --- Reglas de negocio ---
    private fun buildCliente(ctx: InvoiceFEContext): ClienteFE {
        val tipoCliente = ctx.cliente.tipoClienteFE.let {
            if (it == "0" || it.isBlank()) "02" else it
        }
        val esExtranjero = tipoCliente == "04"
        val tipoContribuyente = if (tipoCliente == "02") "1" else ctx.cliente.tipoContribuyente
        
        val ruc = if (esExtranjero) "" 
                  else ctx.cliente.identificacion.let { if (it.length < 5) "00000" else it }
        val dv = if (esExtranjero) "" else ctx.cliente.dv
        
        val telefono = ctx.cliente.telefono.let {
            if (it.matches(Regex("\\d{4}-\\d{4}"))) it else "9999-9999"
        }
        val correo = ctx.cliente.correo.let {
            if (it.matches(Regex("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"))) it else "email@correo.com"
        }
        
        return ClienteFE(
            tipoClienteFE = tipoCliente,
            tipoContribuyente = tipoContribuyente,
            numeroRUC = ruc,
            digitoVerificadorRUC = dv,
            razonSocial = ctx.cliente.nombre,
            codigoUbicacion = ctx.cliente.codigoUbicacion,
            telefono1 = telefono,
            correoElectronico1 = correo,
            tipoIdentificacion = if (esExtranjero) "01" else null,
            nroIdentificacionExtranjero = if (esExtranjero) ctx.cliente.identificacion else null,
        )
    }
    private fun buildItems(ctx: InvoiceFEContext): List<ItemFE> {
        return ctx.detalles.map { det ->
            val descripcion = det.descripcion.let { 
                if (it.length < 5) it.padEnd(5, '.') else it 
            }
            val tasaITBMS = mapTasaITBMS(det.piva)
            val precioDescuento = if (det.cantidad > 0) {
                (det.montoDescuento / det.cantidad).formatDecimals(4)
            } else "0.0000"
            
            ItemFE(
                descripcion = descripcion,
                codigo = det.codigo,
                unidadMedida = det.unidadMedida ?: "und",
                cantidad = det.cantidad.formatDecimals(3),
                precioUnitario = det.precioSinIva.formatDecimals(2),
                precioUnitarioDescuento = precioDescuento,
                valorTotal = det.totalConIva.formatDecimals(2),
                tasaITBMS = tasaITBMS,
                valorITBMS = (det.totalConIva - det.totalSinIva).formatDecimals(2),
                // ISC y OTI si aplican
                tasaISC = det.porcentajeISC?.formatDecimals(2),
                valorISC = det.importeISC?.formatDecimals(2),
                listaItemOTI = det.otiList?.map { ItemOTI(it.tasa, it.valor) },
            )
        }
    }
    private fun mapTasaITBMS(piva: Double): String = when {
        piva == 7.0 -> "01"   // 7% ITBMS
        piva == 10.0 -> "02"  // 10% ITBMS
        piva == 15.0 -> "03"  // 15% ITBMS
        else -> "00"          // Exento
    }
    
    // ... buildDatosTransaccion, buildTotales, buildFormasPago, etc.
}
4.3 — Contexto intermedio (DB -> Builder)
Archivo: features/electronicinvoice/data/ElectronicInvoiceRepository.kt
/**
 * Datos extraídos de la DB, listos para el Builder.
 * Desacopla el ORM del proceso de construcción del DTO.
 */
data class InvoiceFEContext(
    val config: FEConfigData,       // parametros_generales
    val factura: FEFacturaData,     // factura (cabecera)
    val cliente: FEClienteData,     // clientes
    val detalles: List<FEDetalleData>,  // factura_detalle
    val formasPago: List<FEFormaPagoData>,  // caja_nueva_detalle + caja_forma_pago
    val sucursal: FESucursalData,   // sucursal
)
class ElectronicInvoiceRepository {
    
    suspend fun loadInvoiceContext(
        database: Database,
        invoiceId: String,
    ): InvoiceFEContext = dbQuery(database) {
        // 1. Leer factura de FEFacturaReadTable
        // 2. Leer cliente de ClientsTable
        // 3. Leer detalles de SalesFacturaDetalleTable
        // 4. Leer formas de pago de FECajaNuevaDetalleReadTable + CajaFormaPagoTable
        // 5. Leer config de FEParametrosGeneralesReadTable
        // 6. Leer sucursal de FESucursalReadTable
        // Retornar InvoiceFEContext compuesto
    }
    suspend fun updateInvoiceWithFEResponse(
        database: Database,
        invoiceId: String,
        cufe: String,
        qr: String?,
        fechaRecepcionDGI: String?,
        nroProtocolo: String?,
    ) = dbQuery(database) {
        FacturasTablePA.update({ FacturasTablePA.idFactura eq invoiceId }) {
            it[FacturasTablePA.cufe] = cufe
            if (fechaRecepcionDGI != null) it[FacturasTablePA.fechaRecepcionDGI] = fechaRecepcionDGI
        }
        // Actualizar qr y nroProtocoloAutorizacion (requiere agregar columnas a FacturasTablePA)
    }
    
    suspend fun incrementNumeroDocumentoFiscal(
        database: Database,
        cajaId: String,
    ) = dbQuery(database) {
        // Incrementar el correlativo fiscal en la tabla correspondiente
    }
}
PASO 5: Orquestación (Panama Invoice Processor)
Archivo: features/electronicinvoice/application/PanamaInvoiceProcessor.kt
class PanamaInvoiceProcessor(
    private val repository: ElectronicInvoiceRepository,
    private val pacClient: PanamaElectronicInvoiceClient,  // Inyectado: TheFactoryHkaRestClient
    private val payloadBuilder: TheFactoryHkaPayloadBuilder,
) : ElectronicInvoiceStrategy {
    override val countryCode = "PA"
    private val logger = LoggerFactory.getLogger(PanamaInvoiceProcessor::class.java)
    override suspend fun processElectronicInvoice(
        database: Database,
        invoiceId: String,
    ): ElectronicInvoiceResult {
        
        // 1. Obtener datos de la DB
        val context = repository.loadInvoiceContext(database, invoiceId)
        
        // 2. Autenticarse con el PAC
        val credentials = PacCredentials(
            usuario = context.config.tokenEmpresa,
            clave = context.config.tokenPassword,
            baseUrl = context.config.direccionEnvio,
        )
        val authResult = pacClient.authenticate(credentials)
        val token = authResult.getOrElse { e ->
            logger.error("Error autenticando con PAC para factura $invoiceId", e)
            return ElectronicInvoiceResult.Failure("AUTH_ERROR", e.message ?: "Error de autenticación")
        }
        
        // 3. Construir el Payload
        val payload = payloadBuilder.build(context)
        
        // 4. Enviar al PAC
        val sendResult = pacClient.sendDocument(token, payload)
        val pacResponse = sendResult.getOrElse { e ->
            logger.error("Error enviando documento al PAC para factura $invoiceId", e)
            return ElectronicInvoiceResult.Failure("SEND_ERROR", e.message ?: "Error de comunicación")
        }
        
        // 5. Procesar respuesta
        if (!pacResponse.exitoso || pacResponse.cufe.isNullOrBlank()) {
            logger.warn("PAC rechazó factura $invoiceId: [${pacResponse.codigo}] ${pacResponse.mensaje}")
            return ElectronicInvoiceResult.Failure(pacResponse.codigo, pacResponse.mensaje)
        }
        
        // 6. Actualizar DB con CUFE, QR, fecha DGI
        repository.updateInvoiceWithFEResponse(
            database = database,
            invoiceId = invoiceId,
            cufe = pacResponse.cufe,
            qr = pacResponse.qr,
            fechaRecepcionDGI = pacResponse.fechaRecepcionDGI,
            nroProtocolo = pacResponse.nroProtocoloAutorizacion,
        )
        
        // 7. Incrementar correlativo fiscal
        repository.incrementNumeroDocumentoFiscal(database, context.factura.cajaId)
        
        logger.info("FE exitosa para factura $invoiceId. CUFE=${pacResponse.cufe}")
        return ElectronicInvoiceResult.Success(
            cufe = pacResponse.cufe,
            qr = pacResponse.qr,
            fechaDGI = pacResponse.fechaRecepcionDGI,
        )
    }
}
PASO 6: Rutas y Registro en Koin
6.1 — Endpoint REST
Archivo: features/electronicinvoice/route/ElectronicInvoiceRoutes.kt
fun Route.electronicInvoiceRoutes(factory: ElectronicInvoiceProcessorFactory) {
    authenticate {
        route("/api/facturacion-electronica") {
            post("/{invoiceId}/enviar") {
                val principal = call.principal<JWTPrincipal>() ?: ...
                val countryCode = principal.getCountryCode() ?: ...
                val adminDb = principal.getAdminDb() ?: ...
                val invoiceId = call.parameters["invoiceId"] ?: ...
                
                val database = DatabaseManager.connectToCompanyDb(countryCode, adminDb)
                val processor = factory.forCountry(countryCode)
                
                when (val result = processor.processElectronicInvoice(database, invoiceId)) {
                    is ElectronicInvoiceResult.Success -> call.respond(HttpStatusCode.OK, result)
                    is ElectronicInvoiceResult.Failure -> call.respond(HttpStatusCode.BadGateway, result)
                    is ElectronicInvoiceResult.NotApplicable -> call.respond(HttpStatusCode.OK, 
                        mapOf("message" to "FE no aplica para ${result.country}"))
                }
            }
        }
    }
}
6.2 — Registro en Routing.kt
Agregar al final de configureRouting():
// Solo agregar estas líneas, no modificar las existentes:
val httpClient = HttpClient(CIO) { /* config */ }
val feRepository = ElectronicInvoiceRepository()
val pacClient = TheFactoryHkaRestClient(httpClient)
val payloadBuilder = TheFactoryHkaPayloadBuilder()
val panamaProcessor = PanamaInvoiceProcessor(feRepository, pacClient, payloadBuilder)
val feFactory = ElectronicInvoiceProcessorFactory(panamaProcessor)
electronicInvoiceRoutes(feFactory)
PASO 7: Extender FacturasTablePA (columnas faltantes)
FacturasTablePA actualmente solo tiene cufe y fechaRecepcionDGI. Necesitamos agregar para almacenar la respuesta completa del PAC:
object FacturasTablePA : BaseFacturasTable() {
    val fechaRecepcionDGI = varchar("fechaRecepcionDGI", 25).nullable()
    val cufe = text("cufe").nullable()
    val qr = text("qr").nullable()                                    // NUEVO
    val nroProtocoloAutorizacion = varchar("nroProtocoloAutorizacion", 100).nullable() // NUEVO
}
SQL necesario en la DB (migración manual):
ALTER TABLE factura ADD COLUMN qr TEXT NULL;
ALTER TABLE factura ADD COLUMN nroProtocoloAutorizacion VARCHAR(100) NULL;
Resumen de Archivos a Crear/Modificar
#	Archivo
1	build.gradle.kts
2	Routing.kt
3	FacturasTable.kt
4	electronicinvoice/domain/ElectronicInvoiceStrategy.kt
5	electronicinvoice/domain/ElectronicInvoiceModels.kt
6	electronicinvoice/domain/ElectronicInvoiceExceptions.kt
7	electronicinvoice/domain/VenezuelaInvoiceStrategy.kt
8	electronicinvoice/application/ElectronicInvoiceProcessorFactory.kt
9	electronicinvoice/application/PanamaInvoiceProcessor.kt
10	electronicinvoice/data/ElectronicInvoiceTables.kt
11	electronicinvoice/data/ElectronicInvoiceRepository.kt
12	electronicinvoice/pac/PanamaElectronicInvoiceClient.kt
13	electronicinvoice/pac/thefactory/TheFactoryHkaRestClient.kt
14	electronicinvoice/pac/thefactory/TheFactoryHkaDtos.kt
15	electronicinvoice/pac/thefactory/TheFactoryHkaPayloadDtos.kt
16	electronicinvoice/pac/thefactory/TheFactoryHkaPayloadBuilder.kt
17	electronicinvoice/route/ElectronicInvoiceRoutes.kt
Archivos de Venezuela modificados: 0 (regla de oro cumplida).
Preguntas Pendientes Antes de Implementar
1. Columnas FE en tabla factura: ¿Las columnas tipo_documento, NaturalezaOperacion, tipoOperacion, formatoCAFE, entregaCAFE, envioContenedor, tipoVenta ya existen en la DB de Panamá? Si no, ¿se agregan como columnas o se reciben en el request del frontend?
2. Tabla parametros_generales: ¿Las columnas token_empresa, token_password, direccion_envio, tipoEmision, destinoOperacion, procesoGeneracion, codigoSucursalEmisor, puntoFacturacionFiscal ya existen en la DB de PA? Si no, ¿debemos crearlas?
3. Tabla de unidades de medida: Mencionas "unidad_medida mapeada desde tabla unidades". ¿Existe una tabla unidades o unidad_medida en la DB? ¿Cómo se vincula con factura_detalle?
4. Campos ISC y OTI: ¿Los campos porcentaje_isc, importe_isc, id_oti, importe_oti existen en factura_detalle? No los veo en SalesFacturaDetalleTable.
5. El envío al PAC: ¿Debe ocurrir automáticamente al crear la factura (dentro de ProcessSaleUseCase) o como paso separado llamado por el frontend después de crear la venta?
6. Correlativos fiscal: ¿El numeroDocumentoFiscal para DGI se toma de caja.factura_correlativo o existe una tabla/columna separada para el correlativo fiscal de Panamá?
7. TipoClienteFE: ¿Viene como columna de la tabla clientes o de la tabla factura? No veo esta columna en ClientsTable. ¿Es cod_tipo_cliente?
8. cliente_codigoUbicacion: ¿De qué columna se toma? No veo esta columna en ClientsTable.
¿Apruebas este plan? ¿Quieres que ajuste algo antes de proceder a la implementación?


Mis respuestas:

1. Columnas FE en tabla factura: Sí, todas esas columnas (tipo_documento, NaturalezaOperacion, tipoOperacion, formatoCAFE, entregaCAFE, envioContenedor, tipoVenta, observacion) ya existen físicamente en la base de datos de Panamá. Mapéalas directamente en tu FEFacturaReadTable.

2. Tabla parametros_generales: Sí, todas esas columnas (token_empresa, token_password, direccion_envio, tipoEmision, etc.) ya existen en la base de datos. Agrégalas a tu FEParametrosGeneralesReadTable.

3. Tabla de unidades de medida: Existe una tabla de unidades de medida en la DB. En el campo unidad_medida de factura_detalle se guarda el ID. Debes hacer el cruce/JOIN para obtener el símbolo (ej. "kg", "m"). Si el cruce falla o viene nulo, usa el fallback estricto "und".

4. Campos ISC y OTI: Sí, los campos porcentaje_isc, importe_isc, id_oti, e importe_oti ya existen físicamente en la tabla factura_detalle de Panamá. Mapéalos en tu vista de lectura para el detalle.

5. El envío al PAC: Debe ocurrir automáticamente al crear la factura, dentro del caso de uso de cierre de venta (ej. ProcessSaleUseCase), como un paso final síncrono. Solo si las banderas de la transacción indican que se debe procesar fiscalmente.

6. Correlativo fiscal: NO se usa el de la caja. Existe una tabla separada llamada correlativos en la base de datos. Se busca el registro donde la columna campo sea igual a 'numeroDocumentoFiscal' y se extrae/incrementa la columna contador.

7. TipoClienteFE: Esta columna viaja como propiedad de la cabecera. Viene directamente en la tabla factura (no en la tabla de clientes), junto con los datos desnormalizados del cliente al momento de la compra.

8. cliente_codigoUbicacion: Igual que el anterior, viaja desnormalizado directamente como una columna en la tabla factura
