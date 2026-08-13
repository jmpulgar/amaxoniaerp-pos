package com.amaxoniaerp.features.creditnotes.domain

import kotlinx.serialization.Serializable

@Serializable
data class CreditNoteSummary(
    val id: String,
    val codigo: String,
    val facturaId: String,
    val facturaCodigo: String,
    val fecha: String,
    val fechaCreacion: String,
    val clienteNombre: String,
    val clienteIdentificacion: String,
    val total: Double,
    val subtotal: Double,
    val impuesto: Double,
    val fiscalStatus: CreditNoteFiscalStatus,
    val fiscalNumber: String = "",
    val printerSerial: String = "",
    val observacion: String = "",
)

@Serializable
data class CreditNotesListResponse(
    val data: List<CreditNoteSummary>,
    val total: Long,
)

@Serializable
data class CreditNoteDetailResponse(
    val id: String,
    val codigo: String,
    val facturaId: String,
    val facturaCodigo: String,
    val fecha: String,
    val periodo: String,
    val observacion: String,
    val clienteNombre: String,
    val clienteIdentificacion: String,
    val subtotal: Double,
    val impuesto: Double,
    val total: Double,
    val fiscalStatus: CreditNoteFiscalStatus,
    val fiscalNumber: String = "",
    val printerSerial: String = "",
    val anulaFacturaCompleta: Boolean,
    val lines: List<CreditNoteDetailLine>,
    val fiscalDocument: CreditNoteFiscalDocument? = null,
)

@Serializable
data class CreditNoteDetailLine(
    val id: String,
    val idDetalleFactura: String,
    val idItem: Int,
    val descripcion: String,
    val codigo: String,
    val referencia: String,
    val cantidad: Double,
    val precioSinIva: Double,
    val descuentoPorcentaje: Double,
    val descuentoMonto: Double,
    val pIva: Double,
    val totalSinIva: Double,
    val totalConIva: Double,
)

@Serializable
data class CreditNoteSourceInvoiceSummary(
    val id: String,
    val codigo: String,
    val codigoFiscal: String,
    val numeroDocumentoFiscal: String,
    val fecha: String,
    val clienteNombre: String,
    val clienteIdentificacion: String,
    val total: Double,
    val remainingAmount: Double,
    val items: Int,
    val moneda: String,
)

@Serializable
data class CreditNoteSourceInvoiceListResponse(
    val data: List<CreditNoteSourceInvoiceSummary>,
    val total: Long,
)

@Serializable
data class CreditNoteSourceInvoiceDetailResponse(
    val id: String,
    val codigo: String,
    val codigoFiscal: String,
    val numeroDocumentoFiscal: String,
    val fecha: String,
    val clienteId: String,
    val clienteNombre: String,
    val clienteIdentificacion: String,
    val clienteDireccion: String,
    val clienteTelefono: String,
    val codVendedor: Int,
    val totalOriginal: Double,
    val subtotalOriginal: Double,
    val impuestoOriginal: Double,
    val remainingAmount: Double,
    val moneda: String,
    val tasa: Double?,
    val totalBs: Double,
    val totalUsd: Double,
    val lines: List<CreditNoteSourceInvoiceLine>,
)

@Serializable
data class CreditNoteSourceInvoiceLine(
    val idDetalleFactura: String,
    val idItem: Int,
    val descripcion: String,
    val codigo: String,
    val referencia: String,
    val cantidadOriginal: Double,
    val cantidadDevuelta: Double,
    val cantidadDisponible: Double,
    val precioSinIva: Double,
    val descuentoPorcentaje: Double,
    val descuentoMontoTotal: Double,
    val pIva: Double,
    val totalSinIvaOriginal: Double,
    val totalConIvaOriginal: Double,
    val totalSinIvaDisponible: Double,
    val totalConIvaDisponible: Double,
    val almacen: Int,
)

@Serializable
data class CreateCreditNoteRequest(
    val idFactura: String,
    val fecha: String,
    val periodo: String = "",
    val observacion: String = "",
    val detalle: List<CreateCreditNoteLineInput>,
    val anular: Boolean = false,
    val devolverStock: Boolean = true,
    val idCajaSecuencia: String,
    val settlementType: CreditNoteSettlementType = CreditNoteSettlementType.NINGUNO,
    val idFormaPagoReintegro: Int? = null,
    val devolucionElectronica: Boolean = false,
    val numeroFiscalElectronico: String = "",
)

@Serializable
data class CreateCreditNoteLineInput(
    val idDetalleFactura: String,
    val cantidad: Double,
)

@Serializable
data class CreateCreditNoteResponse(
    val success: Boolean,
    val id: String,
    val codigo: String,
    val subtotal: Double,
    val impuesto: Double,
    val total: Double,
    val fiscalStatus: CreditNoteFiscalStatus,
    val detail: CreditNoteDetailResponse,
    val fiscalMessage: String? = null,
)

/** Datos de una NC PA reservada antes de llamar al PAC. */
data class PreparedCreditNote(
    val id: String,
    val codigo: String,
    val numeroDocumentoFiscal: String,
)

@Serializable
data class ConfirmCreditNoteFiscalRequest(
    val codDevolucionFiscal: String,
    val numeroDocumentoFiscal: String = "",
    val printerSerial: String = "",
    val nroz: String = "",
)

@Serializable
data class ConfirmCreditNoteFiscalResponse(
    val success: Boolean,
    val id: String,
    val codigo: String,
    val fiscalStatus: CreditNoteFiscalStatus,
    val codDevolucionFiscal: String,
    val numeroDocumentoFiscal: String,
    val printerSerial: String,
)

@Serializable
data class CreditNoteFiscalDocument(
    val creditNoteId: String,
    val creditNoteCode: String,
    val date: String,
    val customerName: String,
    val customerIdentifier: String,
    val customerAddress: String,
    val customerPhone: String,
    val originalInvoiceCode: String,
    val originalFiscalNumber: String,
    val originalInvoiceDate: String,
    val printerSerial: String,
    val comment: String,
    val lines: List<CreditNoteFiscalLine>,
)

@Serializable
data class CreditNoteFiscalLine(
    val description: String,
    val quantity: Double,
    val unitPriceWithoutTax: Double,
    val totalWithTax: Double,
    val taxRate: Double,
)

@Serializable
enum class CreditNoteSettlementType {
    NINGUNO,
    ABONO,
    REINTEGRO,
    CERTIFICADO_REGALO,
}

@Serializable
enum class CreditNoteFiscalStatus {
    PENDIENTE,
    INCIERTA,
    RECHAZADA,
    CONFIRMADA,
}
