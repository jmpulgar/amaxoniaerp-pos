package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import com.amaxonia.pos.domain.repository.CashCloseContextReader
import com.amaxonia.pos.domain.repository.CompanyIdentity
import com.amaxonia.pos.domain.repository.CreditNoteContextReader
import com.amaxonia.pos.domain.repository.DashboardSessionReader
import com.amaxonia.pos.domain.repository.PaymentSuccessRepository
import com.amaxonia.pos.domain.repository.PosSettingsRepository
import kotlinx.coroutines.flow.Flow

class LocalPosConfigurationRepository(
    private val localStore: LocalStore,
) : DashboardSessionReader,
    CashCloseContextReader,
    CreditNoteContextReader,
    PaymentSuccessRepository,
    PosSettingsRepository {
    override val selectedPrinterType: Flow<PrinterType> = localStore.selectedPrinterTypeFlow()
    override val selectedCountry: Flow<ServerCountry?> = localStore.selectedCountryFlow()
    override val factorySettings: Flow<TheFactorySettings> = localStore.theFactorySettingsFlow()
    override val allowEditPrices: Flow<Boolean> = localStore.allowEditPricesFlow()
    override val allowDiscounts: Flow<Boolean> = localStore.allowDiscountsFlow()

    override suspend fun currentAdminDatabase(): String =
        localStore
            .readCompanySession()
            ?.company
            ?.adminDb
            .orEmpty()

    override suspend fun currentCountryCode(): String = localStore.readSelectedCountry()?.code.orEmpty()

    override suspend fun currentCountry(): ServerCountry? = localStore.readSelectedCountry()

    override suspend fun selectedPrinterType(): PrinterType = localStore.readSelectedPrinterType()

    override suspend fun currentCompany(): CompanyIdentity? =
        localStore.readCompanySession()?.company?.let { company ->
            CompanyIdentity(
                name = company.name,
                rif = company.rif,
                adminDatabase = company.adminDb,
            )
        }

    override suspend fun find(transactionId: String): PaymentSuccessPayload? = localStore.readLastPaymentSuccess(transactionId)

    override suspend fun savePrinterType(printerType: PrinterType) {
        localStore.saveSelectedPrinterType(printerType)
    }

    override suspend fun saveFactorySettings(settings: TheFactorySettings) {
        localStore.saveTheFactorySettings(settings)
    }

    override suspend fun saveAllowEditPrices(enabled: Boolean) {
        localStore.saveAllowEditPrices(enabled)
    }

    override suspend fun saveAllowDiscounts(enabled: Boolean) {
        localStore.saveAllowDiscounts(enabled)
    }
}
