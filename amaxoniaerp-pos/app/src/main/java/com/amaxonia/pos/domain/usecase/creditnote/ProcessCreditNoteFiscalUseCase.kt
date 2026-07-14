package com.amaxonia.pos.domain.usecase.creditnote

import com.amaxonia.pos.domain.model.creditnote.ConfirmCreditNoteFiscalRequestDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.repository.CreditNoteContextReader
import com.amaxonia.pos.domain.repository.CreditNoteFiscalConfirmationRepository
import com.amaxonia.pos.domain.repository.PrinterProvider

data class CreditNoteFiscalProcessingResult(
    val detail: CreditNoteDetailDto,
    val errorMessage: String? = null,
)

class ProcessCreditNoteFiscalUseCase(
    private val confirmationRepository: CreditNoteFiscalConfirmationRepository,
    private val printerProvider: PrinterProvider,
    private val contextReader: CreditNoteContextReader,
) {
    suspend operator fun invoke(
        detail: CreditNoteDetailDto,
        force: Boolean = false,
    ): CreditNoteFiscalProcessingResult {
        val canProcess =
            (detail.fiscalStatus != CreditNoteFiscalStatusDto.CONFIRMADA || force) &&
                shouldProcessFiscal()
        val document = detail.fiscalDocument
        val printer = if (canProcess && document != null) printerProvider.getActivePrinter() else null
        return if (document == null || printer == null) {
            CreditNoteFiscalProcessingResult(detail)
        } else {
            printer.printCreditNote(document).fold(
                onSuccess = { printResult ->
                    confirmationRepository
                        .confirmFiscal(
                            id = detail.id,
                            payload =
                                ConfirmCreditNoteFiscalRequestDto(
                                    codDevolucionFiscal = printResult.fiscalNumber,
                                    numeroDocumentoFiscal = printResult.fiscalNumber,
                                    printerSerial = printResult.printerSerial,
                                ),
                        ).fold(
                            onSuccess = { confirmation ->
                                CreditNoteFiscalProcessingResult(
                                    detail.copy(
                                        fiscalStatus = CreditNoteFiscalStatusDto.CONFIRMADA,
                                        fiscalNumber = confirmation.codDevolucionFiscal,
                                        printerSerial = confirmation.printerSerial,
                                    ),
                                )
                            },
                            onFailure = { error ->
                                CreditNoteFiscalProcessingResult(
                                    detail,
                                    error.message
                                        ?: "La impresión fiscal salió bien, pero no se pudo confirmar en el backend",
                                )
                            },
                        )
                },
                onFailure = { error ->
                    CreditNoteFiscalProcessingResult(
                        detail,
                        error.message ?: "No se pudo procesar la nota de crédito fiscal",
                    )
                },
            )
        }
    }

    private suspend fun shouldProcessFiscal(): Boolean =
        contextReader.currentCountryCode() == VENEZUELA_CODE &&
            contextReader.selectedPrinterType() == PrinterType.THE_FACTORY_HKA

    private companion object {
        const val VENEZUELA_CODE = "VE"
    }
}
