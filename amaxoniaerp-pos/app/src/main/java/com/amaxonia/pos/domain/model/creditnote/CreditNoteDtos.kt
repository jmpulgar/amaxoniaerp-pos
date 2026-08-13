package com.amaxonia.pos.domain.model.creditnote

import kotlinx.serialization.Serializable

@Serializable
data class CreditNoteSummaryDto(
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
    val fiscalStatus: CreditNoteFiscalStatusDto,
    val fiscalNumber: String = "",
    val printerSerial: String = "",
    val observacion: String = "",
)

@Serializable
data class CreditNotesListResponseDto(
    val data: List<CreditNoteSummaryDto>,
    val total: Long,
)

@Serializable
data class CreditNoteDetailDto(
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
    val fiscalStatus: CreditNoteFiscalStatusDto,
    val fiscalNumber: String = "",
    val printerSerial: String = "",
    val anulaFacturaCompleta: Boolean,
    val lines: List<CreditNoteDetailLineDto>,
    val fiscalDocument: CreditNoteFiscalDocumentDto? = null,
)

@Serializable
data class CreditNoteDetailLineDto(
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
data class CreditNoteSourceInvoiceSummaryDto(
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
data class CreditNoteSourceInvoiceListResponseDto(
    val data: List<CreditNoteSourceInvoiceSummaryDto>,
    val total: Long,
)

@Serializable
data class CreditNoteSourceInvoiceDetailDto(
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
    val tasa: Double? = null,
    val totalBs: Double = 0.0,
    val totalUsd: Double = 0.0,
    val lines: List<CreditNoteSourceInvoiceLineDto>,
)

@Serializable
data class CreditNoteSourceInvoiceLineDto(
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
data class CreateCreditNoteRequestDto(
    val idFactura: String,
    val fecha: String,
    val periodo: String = "",
    val observacion: String = "",
    val detalle: List<CreateCreditNoteLineInputDto>,
    val anular: Boolean = false,
    val devolverStock: Boolean = true,
    val idCajaSecuencia: String,
    val settlementType: CreditNoteSettlementTypeDto = CreditNoteSettlementTypeDto.NINGUNO,
    val idFormaPagoReintegro: Int? = null,
    val devolucionElectronica: Boolean = false,
    val numeroFiscalElectronico: String = "",
)

@Serializable
data class CreateCreditNoteLineInputDto(
    val idDetalleFactura: String,
    val cantidad: Double,
)

@Serializable
data class CreateCreditNoteResponseDto(
    val success: Boolean,
    val id: String,
    val codigo: String,
    val subtotal: Double,
    val impuesto: Double,
    val total: Double,
    val fiscalStatus: CreditNoteFiscalStatusDto,
    val detail: CreditNoteDetailDto,
)

@Serializable
data class ConfirmCreditNoteFiscalRequestDto(
    val codDevolucionFiscal: String,
    val numeroDocumentoFiscal: String = "",
    val printerSerial: String = "",
    val nroz: String = "",
)

@Serializable
data class ConfirmCreditNoteFiscalResponseDto(
    val success: Boolean,
    val id: String,
    val codigo: String,
    val fiscalStatus: CreditNoteFiscalStatusDto,
    val codDevolucionFiscal: String,
    val numeroDocumentoFiscal: String,
    val printerSerial: String,
)

@Serializable
data class CreditNoteFiscalDocumentDto(
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
    val lines: List<CreditNoteFiscalLineDto>,
)

@Serializable
data class CreditNoteFiscalLineDto(
    val description: String,
    val quantity: Double,
    val unitPriceWithoutTax: Double = 0.0,
    val totalWithTax: Double,
    val taxRate: Double,
)

@Serializable
enum class CreditNoteSettlementTypeDto {
    NINGUNO,
    ABONO,
    REINTEGRO,
    CERTIFICADO_REGALO,
}

@Serializable
enum class CreditNoteFiscalStatusDto {
    PENDIENTE,
    INCIERTA,
    RECHAZADA,
    CONFIRMADA,
}

data class CreditNotePrintResult(
    val fiscalNumber: String,
    val printerSerial: String,
)

data class ReceiptPrintResult(
    val fiscalNumber: String,
    val printerSerial: String,
)
