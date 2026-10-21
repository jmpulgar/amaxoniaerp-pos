package com.amaxonia.pos.data.printer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.readActiveCajaForToday
import com.amaxonia.pos.data.local.readCompanySession
import com.amaxonia.pos.data.printer.panama.PanamaCreditNoteTicketFormatter
import com.amaxonia.pos.data.printer.pdf.TicketPdfGenerator
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.sales.EmpresaPrintDto
import com.amaxonia.pos.domain.repository.PrinterProvider
import com.amaxonia.pos.domain.usecase.creditnote.PrintCreditNoteTicketUseCase
import java.io.File

class DefaultCreditNoteTicketPrinter(
    private val printerProvider: PrinterProvider,
    private val localStore: LocalStore,
    private val contextProvider: () -> Context,
    private val formatter: PanamaCreditNoteTicketFormatter = PanamaCreditNoteTicketFormatter(),
) : PrintCreditNoteTicketUseCase {
    override suspend fun invoke(detail: CreditNoteDetailDto): Result<Unit> {
        val company = localStore.readCompanySession()?.company
        val caja = localStore.readActiveCajaForToday()
        val empresaPrint =
            company?.let {
                EmpresaPrintDto(
                    nombre = it.name.ifBlank { DEFAULT_COMPANY_NAME },
                    ruc = it.rif.takeIf(String::isNotBlank),
                    tienda = caja?.sucursalNombre ?: caja?.sucursalCodigo,
                    caja = caja?.descripcion ?: caja?.codCaja,
                )
            }
        val ticket = formatter.format(detail, empresaPrint, caja?.descripcion)
        val printer = printerProvider.getActiveTicketPrinter()

        return if (printer != null) {
            printWithPhysicalPrinter(printer, ticket)
        } else {
            generateAndOpenPdf(detail, ticket)
        }
    }

    private suspend fun printWithPhysicalPrinter(
        printer: com.amaxonia.pos.domain.model.printer.TicketPrinter,
        ticket: TicketDocument,
    ): Result<Unit> =
        when (val result = printer.printTicket(ticket)) {
            PrintResult.Success -> Result.success(Unit)
            is PrintResult.Error -> Result.failure(IllegalStateException(result.message, result.cause))
        }

    private fun generateAndOpenPdf(
        detail: CreditNoteDetailDto,
        ticket: TicketDocument,
    ): Result<Unit> =
        runCatching {
            val context = contextProvider()
            val pdfBytes = TicketPdfGenerator.generatePdf(ticket)
            val noteCode = detail.codigo.ifBlank { detail.id }
            val cleanName = noteCode.replace('/', '_').replace('\\', '_')
            val pdfFile =
                File(context.cacheDir, "ticket_nc_$cleanName.pdf").apply {
                    writeBytes(pdfBytes)
                }
            try {
                val uri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        pdfFile,
                    )
                val intent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // PDF guardado en caché aunque no haya visor de PDF disponible
            } catch (_: IllegalArgumentException) {
                // FileProvider no registrado (ej. entornos de prueba unitaria)
            }
        }

    private companion object {
        const val DEFAULT_COMPANY_NAME = "AMAXONIA"
    }
}
