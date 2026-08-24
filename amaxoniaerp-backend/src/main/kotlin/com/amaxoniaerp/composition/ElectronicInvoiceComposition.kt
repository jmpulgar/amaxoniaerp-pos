package com.amaxoniaerp.composition

import com.amaxoniaerp.features.creditnotes.application.CreditNoteService
import com.amaxoniaerp.features.creditnotes.application.PanamaCreditNoteProcessor
import com.amaxoniaerp.features.creditnotes.data.CreditNoteRepository
import com.amaxoniaerp.features.electronicinvoice.application.ElectronicInvoiceProcessorFactory
import com.amaxoniaerp.features.electronicinvoice.application.PanamaInvoiceProcessor
import com.amaxoniaerp.features.electronicinvoice.application.VenezuelaInvoiceStrategy
import com.amaxoniaerp.features.electronicinvoice.data.ElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.data.VenezuelaElectronicInvoiceRepository
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaCreditNotePayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaPayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.TheFactoryHkaRestClient
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaPayloadBuilder
import com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela.VenezuelaHkaRestClient
import com.amaxoniaerp.features.electronicinvoice.storage.FileSystemPanamaCreditNotePdfStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private const val HTTP_REQUEST_TIMEOUT_MS = 30_000L

fun buildFeHttpClient(): HttpClient =
    HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    encodeDefaults = false
                    explicitNulls = false
                    ignoreUnknownKeys = true
                    prettyPrint = false
                },
            )
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        engine {
            requestTimeout = HTTP_REQUEST_TIMEOUT_MS
        }
    }

class FeDependencies(
    val feFactory: ElectronicInvoiceProcessorFactory,
    val panamaProcessor: PanamaInvoiceProcessor,
    val feRepository: ElectronicInvoiceRepository,
    val pacClient: TheFactoryHkaRestClient,
    val payloadBuilder: TheFactoryHkaPayloadBuilder,
)

fun buildElectronicInvoiceDependencies(feHttpClient: HttpClient): FeDependencies {
    // FacturaciÃ³n ElectrÃ³nica PanamÃ¡ - HTTP Client + PAC + Strategy
    val feRepository = ElectronicInvoiceRepository()
    val pacClient = TheFactoryHkaRestClient(feHttpClient)
    val payloadBuilder = TheFactoryHkaPayloadBuilder()
    val panamaProcessor = PanamaInvoiceProcessor(feRepository, pacClient, payloadBuilder)

    // FacturaciÃ³n ElectrÃ³nica Venezuela (The Factory HKA FE).
    // Activate cuando parametros_generales.tipo_facturacion == 5; usa el mismo
    // HttpClient (con TLS+timeouts+hostname verification ya configurados).
    val veRepository = VenezuelaElectronicInvoiceRepository()
    val veHkaClient = VenezuelaHkaRestClient(feHttpClient)
    val vePayloadBuilder = VenezuelaHkaPayloadBuilder()
    val venezuelaProcessor =
        VenezuelaInvoiceStrategy(
            repository = veRepository,
            hkaClient = veHkaClient,
            payloadBuilder = vePayloadBuilder,
        )
    val feFactory = ElectronicInvoiceProcessorFactory(panamaProcessor, venezuelaProcessor)
    return FeDependencies(feFactory, panamaProcessor, feRepository, pacClient, payloadBuilder)
}

class CreditNoteDependencies(
    val creditNoteService: CreditNoteService,
)

fun buildCreditNoteDependencies(
    fe: FeDependencies,
    dataBasePath: String?,
): CreditNoteDependencies {
    val creditNoteRepository = CreditNoteRepository()
    val creditNoteProcessor =
        PanamaCreditNoteProcessor(
            repository = fe.feRepository,
            pacClient = fe.pacClient,
            payloadBuilder = TheFactoryHkaCreditNotePayloadBuilder(fe.payloadBuilder),
            pdfStorage =
                dataBasePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::FileSystemPanamaCreditNotePdfStorage),
        )
    return CreditNoteDependencies(CreditNoteService(creditNoteRepository, creditNoteProcessor))
}
