package com.amaxoniaerp.features.electronicinvoice.domain

import kotlinx.serialization.Serializable

// ─── Resultado del procesamiento FE ──────────────────────────────────────────

/**
 * Resultado sellado del procesamiento de facturación electrónica.
 * Permite al llamador manejar los tres escenarios posibles de forma exhaustiva.
 */
sealed class ElectronicInvoiceResult {
    /**
     * El PAC aceptó el documento.
     *
     * **FASE 2 (corrección Punto 1):** los campos繁荣 PA y VE están separados:
     * - Panamá usa `cufe`, `qr`, `fechaRecepcionDGI`, `nroProtocoloAutorizacion`;
     * - Venezuela usa `numeroDocumentoFiscal`, `numeroControlThka` provenientes
     *   de `resultado.numeroDocumento` / `resultado.numeroControl` del PAC HKA.
     *
     * Cada país persiste y expone **solo sus propios campos**. NUNCA se usan
     * `cufe`/`qr` como transporte temporal para VE ni viceversa.
     */
    @Serializable
    data class Success(
        // ─── Panamá (CAFE/DGI): intactos - FASE 2 Punto 6 ──────────────
        val cufe: String? = null,
        val qr: String? = null,
        val fechaRecepcionDGI: String? = null,
        val nroProtocoloAutorizacion: String? = null,
        val fechaLimite: String? = null,
        /**
         * ─── Venezuela digital (HKA): campos propios - FASE 2 Punto 1 ──
         * `resultado.numeroDocumento` retornado por HKA VE. Null en PA.
         */
        val numeroDocumentoFiscal: String? = null,
        /** `resultado.numeroControl` retornado por HKA VE. Null en PA. */
        val numeroControlThka: String? = null,
    ) : ElectronicInvoiceResult()

    /** El PAC rechazó el documento o hubo un error de comunicación. */
    @Serializable
    data class Failure(
        val codigo: String,
        val mensaje: String,
    ) : ElectronicInvoiceResult()

    /** La facturación electrónica no aplica para este país (ej. sin PAC). */
    @Serializable
    data class NotApplicable(
        val country: String,
    ) : ElectronicInvoiceResult()

    /**
     * El tipo de documento no está soportado por la implementación actual.
     *
     * FASE 1: solo se emite tipoDocumento "01" (factura). Notas de crédito / débito
     * y demás tipos retornan este resultado SIN llamar al PAC.
     */
    @Serializable
    data class UnsupportedDocumentType(
        val country: String,
        val tipoDocumento: String,
    ) : ElectronicInvoiceResult()

    /**
     * La factura ya contiene un número fiscal persistido (idempotencia).
     *
     * Se retorna SIN llamar al PAC cuando la factura recargada de la DB ya tiene
     * número fiscal / número de control válidos.
     */
    @Serializable
    data class AlreadyIssued(
        val country: String,
        val numeroDocumentoFiscal: String,
        val numeroControl: String? = null,
    ) : ElectronicInvoiceResult()

    /**
     * Resultado incierto. Se produce ante timeout o respuesta ambigua del PAC:
     * el documento PUEDE haber sido creado por el PAC, pero no se recibió
     * confirmación. NO se persiste ningún número inventado, NO se marca como
     * exitoso. El proceso superior decide el siguiente paso (humano / reintento).
     */
    @Serializable
    data class Uncertain(
        val country: String,
        val codigo: String,
        val mensaje: String,
        val transaccionId: String? = null,
    ) : ElectronicInvoiceResult()
}

// ─── DTOs estandarizados (independientes del PAC) ────────────────────────────

/**
 * Credenciales para autenticarse contra un PAC de Panamá.
 * Se extraen de `parametros_generales`.
 */
data class PacCredentials(
    val usuario: String,
    val clave: String,
    val baseUrl: String,
)

/**
 * Token JWT obtenido del endpoint de autenticación del PAC.
 */
data class PacAuthToken(
    val token: String,
    val expiresAt: Long = 0L,
)

/**
 * Respuesta estandarizada de cualquier PAC de Panamá.
 * Abstrae los campos específicos de cada proveedor a un formato común.
 */
data class PacResponse(
    val exitoso: Boolean,
    val codigo: String,
    val mensaje: String,
    val cufe: String? = null,
    val qr: String? = null,
    val fechaRecepcionDGI: String? = null,
    val nroProtocoloAutorizacion: String? = null,
    val fechaLimite: String? = null,
)

// ─── Contexto de datos extraídos de la DB para construir el payload ──────────

/**
 * Contexto completo de una factura, listo para ser transformado en un payload
 * de facturación electrónica. Desacopla el ORM del Builder.
 */
data class InvoiceFEContext(
    val config: FEConfigData,
    val factura: FEFacturaData,
    val cliente: FEClienteData,
    val detalles: List<FEDetalleData>,
    val formasPago: List<FEFormaPagoData>,
    val retencion: FERetencionData?,
    val montoCancelar: Double?,
    val codigoSucursalEmisor: String,
    val puntoFacturacionFiscal: String,
    val vuelto: Double?,
)

/** Datos de configuración PAC desde `parametros_generales`. */
data class FEConfigData(
    val tokenEmpresa: String,
    val tokenPassword: String,
    val api_thefactoryhka: String,
    val tipoEmision: String,
    val destinoOperacion: String,
    val procesoGeneracion: String,
    val codigoSucursalEmisorFallback: String,
    val puntoFacturacionFiscalFallback: String,
    val fechaInicioContingencia: String?,
    val motivoContingencia: String?,
    val tipoFacturacion: Int,
)

/** Datos de cabecera de la factura. */
data class FEFacturaData(
    val idFactura: String,
    val codFactura: String,
    val numeroDocumentoFiscal: String,
    val fechaFactura: String?,
    val tipoDocumento: String,
    val naturalezaOperacion: String,
    val tipoOperacion: String,
    val formatoCAFE: String,
    val entregaCAFE: String,
    val envioContenedor: String,
    val tipoVenta: String,
    val tipoFactura: String,
    val observacion: String?,
    val montoItemsFactura: Double,
    val ivaTotalFactura: Double,
    val totalTotalFactura: Double,
    val totalizarDescuentoGlobal: Double,
    val cajaId: String,
)

/** Datos del cliente desnormalizados desde la factura. */
data class FEClienteData(
    val tipoClienteFE: String,
    val tipoContribuyente: String,
    val identificacion: String,
    val dv: String,
    val nombre: String,
    val codigoUbicacion: String?,
    val telefono: String?,
    val correo: String?,
    val direccion: String?,
    val paisIso: String,
    val paisExtranjeroIso: String?,
)

/** Datos de una línea de detalle de la factura. */
data class FEDetalleData(
    val descripcion: String,
    val codigo: String,
    val unidadMedida: String?,
    val codigoCPBS: String?,
    val codigoCPBSAbrev: String?,
    val cantidad: Double,
    val precioSinIva: Double,
    val montoDescuento: Double,
    val piva: Double,
    val totalSinIva: Double,
    val totalConIva: Double,
    val porcentajeIsc: Double?,
    val importeIsc: Double?,
    val idOti: Int?,
    val importeOti: Double?,
)

data class FERetencionData(
    val codigoRetencion: String,
    val montoRetencion: Double,
)

/** Datos de una forma de pago asociada a la factura. */
data class FEFormaPagoData(
    val siglas: String?,
    val formaPagoFact: String?,
    val descripcion: String,
    val monto: Double,
    val esCash: Boolean = false,
    val cambio: Double = 0.0,
)
