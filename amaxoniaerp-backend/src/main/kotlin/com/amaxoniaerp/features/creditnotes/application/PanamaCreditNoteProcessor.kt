package com.amaxoniaerp.features.creditnotes.application

import com.amaxoniaerp.features.creditnotes.domain.PreparedCreditNote
import com.amaxoniaerp.features.electronicinvoice.data.ElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.domain.PacCredentials
import com.amaxoniaerp.features.electronicinvoice.domain.PacResponse
import com.amaxoniaerp.features.electronicinvoice.pac.PanamaElectronicInvoiceClient
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaCreditNotePayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.storage.PanamaCreditNotePdfStorage
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

sealed class PanamaCreditNotePacResult {
    data class Accepted(
        val response: PacResponse,
        val pdfDiagnostic: String? = null,
    ) : PanamaCreditNotePacResult()

    data class Rejected(
        val codigo: String,
        val mensaje: String,
    ) : PanamaCreditNotePacResult()

    data class Uncertain(
        val codigo: String,
        val mensaje: String,
    ) : PanamaCreditNotePacResult()
}

/** Orquesta el envío de una NC PA fuera de la transacción que la reserva. */
class PanamaCreditNoteProcessor(
    private val repository: ElectronicInvoiceRepository,
    private val pacClient: PanamaElectronicInvoiceClient,
    private val payloadBuilder: TheFactoryHkaCreditNotePayloadBuilder,
    private val pdfStorage: PanamaCreditNotePdfStorage? = null,
) {

    private val logger = LoggerFactory.getLogger(PanamaCreditNoteProcessor::class.java)

    suspend fun process(
        database: Database,
        prepared: PreparedCreditNote,
        companyDb: String? = null,
    ): PanamaCreditNotePacResult {
        val context = try {
            repository.loadCreditNoteContext(database, prepared.id, prepared.numeroDocumentoFiscal)
        } catch (e: Exception) {
            logger.error("No se pudo construir el contexto de NC PA {}", prepared.id, e)
            return PanamaCreditNotePacResult.Rejected(
                codigo = "BUILD_CONTEXT",
                mensaje = e.message ?: "No se pudo construir el contexto de la nota de crédito",
            )
        }

        val credentials = PacCredentials(
            usuario = context.invoice.config.tokenEmpresa,
            clave = context.invoice.config.tokenPassword,
            baseUrl = context.invoice.config.api_thefactoryhka,
        )
        val token = pacClient.authenticate(credentials).getOrElse { error ->
            logger.error("Error autenticando la NC PA {}", prepared.id, error)
            return PanamaCreditNotePacResult.Uncertain(
                codigo = "AUTH_ERROR",
                mensaje = "Error de autenticación con el PAC: ${error.message}",
            )
        }

        val payload = try {
            payloadBuilder.build(context)
        } catch (e: Exception) {
            logger.error("Error construyendo payload de NC PA {}", prepared.id, e)
            return PanamaCreditNotePacResult.Rejected(
                codigo = "BUILD_ERROR",
                mensaje = "Error construyendo documento electrónico: ${e.message}",
            )
        }

        val response = pacClient.sendDocument(
            baseUrl = context.invoice.config.api_thefactoryhka,
            token = token,
            payload = payload,
        ).getOrElse { error ->
            logger.error("Error enviando NC PA {}", prepared.id, error)
            return PanamaCreditNotePacResult.Uncertain(
                codigo = "SEND_ERROR",
                mensaje = "Error de comunicación con el PAC: ${error.message}",
            )
        }

        if (!response.exitoso || response.cufe.isNullOrBlank()) {
            logger.warn("PAC rechazó NC PA {}: [{}] {}", prepared.id, response.codigo, response.mensaje)
            return PanamaCreditNotePacResult.Rejected(response.codigo, response.mensaje)
        }

        val pdfDiagnostic = if (pdfStorage == null) {
            null
        } else if (companyDb.isNullOrBlank()) {
            "No se pudo guardar el PDF de la NC: companyDb requerido"
        } else {
            pacClient.downloadPdf(
                baseUrl = context.invoice.config.api_thefactoryhka,
                token = token,
                cufe = response.cufe,
            ).fold(
                onSuccess = { bytes ->
                    pdfStorage.store(
                        companyDb = companyDb,
                        creditNoteId = prepared.id,
                        numeroDocumentoFiscal = prepared.numeroDocumentoFiscal,
                        bytes = bytes,
                    ).exceptionOrNull()?.let { error ->
                        "No se pudo guardar el PDF de la NC: ${error.message}"
                    }
                },
                onFailure = { error ->
                    "No se pudo descargar el PDF de la NC: ${error.message}"
                },
            )
        }

        return PanamaCreditNotePacResult.Accepted(response, pdfDiagnostic)
    }
}
