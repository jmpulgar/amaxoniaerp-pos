package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.repository.SalesRepository

class ConfirmFiscalDocumentUseCase(
    private val salesRepository: SalesRepository,
) {
    suspend operator fun invoke(
        invoiceId: String,
        fiscalNumber: String,
        printerSerial: String,
    ): Result<Unit> =
        salesRepository
            .confirmFacturaFiscal(
                facturaId = invoiceId,
                payload =
                    ConfirmFacturaFiscalRequestDto(
                        numeroDocumentoFiscal = fiscalNumber,
                        impresoraSerial = printerSerial,
                    ),
            ).map { }
}
