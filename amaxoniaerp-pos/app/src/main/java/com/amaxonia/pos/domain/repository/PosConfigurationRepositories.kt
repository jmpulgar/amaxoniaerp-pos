package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import kotlinx.coroutines.flow.Flow

data class CompanyIdentity(
    val name: String,
    val rif: String,
    val adminDatabase: String,
)

interface ProductSessionReader {
    suspend fun currentAdminDatabase(): String
}

interface DashboardSessionReader : ProductSessionReader {
    suspend fun currentCountry(): ServerCountry?
}

interface CashCloseContextReader {
    suspend fun currentCountryCode(): String

    suspend fun selectedPrinterType(): PrinterType

    suspend fun currentCompany(): CompanyIdentity?
}

interface CreditNoteContextReader {
    suspend fun currentCountryCode(): String

    suspend fun selectedPrinterType(): PrinterType
}

interface PaymentSuccessRepository {
    suspend fun find(transactionId: String): PaymentSuccessPayload?
}

interface PosSettingsRepository {
    val selectedPrinterType: Flow<PrinterType>
    val selectedCountry: Flow<ServerCountry?>
    val factorySettings: Flow<TheFactorySettings>
    val allowEditPrices: Flow<Boolean>
    val allowDiscounts: Flow<Boolean>

    suspend fun currentCountry(): ServerCountry?

    suspend fun savePrinterType(printerType: PrinterType)

    suspend fun saveFactorySettings(settings: TheFactorySettings)

    suspend fun saveAllowEditPrices(enabled: Boolean)

    suspend fun saveAllowDiscounts(enabled: Boolean)
}
