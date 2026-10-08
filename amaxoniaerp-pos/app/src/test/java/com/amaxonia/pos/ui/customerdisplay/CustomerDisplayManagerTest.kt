package com.amaxonia.pos.ui.customerdisplay

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.CashCloseContextReader
import com.amaxonia.pos.domain.repository.CompanyIdentity
import com.amaxonia.pos.domain.repository.PosSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomerDisplayManagerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val cartRepository = CartRepository()
    private val fakeSettingsRepository = FakePosSettingsRepository()
    private val fakeCashCloseReader = FakeCashCloseReader()
    private val fakeActiveCajaReader = FakeActiveCajaReader()

    @Test
    fun `initial state reflects single display environment`() {
        val manager = CustomerDisplayManager(
            appContext = context,
            cartRepository = cartRepository,
            settingsRepository = fakeSettingsRepository,
            cashCloseReader = fakeCashCloseReader,
            activeCajaReader = fakeActiveCajaReader,
        )

        assertFalse(manager.isSecondaryDisplayAvailable.value)
        assertFalse(manager.isDisplayEnabled.value)
        assertNull(manager.saleSuccessMessage.value)
        assertNull(manager.findSecondaryDisplay())
    }

    @Test
    fun `showSaleSuccess publishes message and clears after timeout`() = runTest {
        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val manager = CustomerDisplayManager(
            appContext = context,
            cartRepository = cartRepository,
            settingsRepository = fakeSettingsRepository,
            cashCloseReader = fakeCashCloseReader,
            activeCajaReader = fakeActiveCajaReader,
            mainDispatcher = testDispatcher,
        )

        manager.showSaleSuccess("¡Gracias por su compra!")
        testScheduler.runCurrent()
        assertEquals("¡Gracias por su compra!", manager.saleSuccessMessage.value)

        // Advance past SUCCESS_MESSAGE_DURATION_MS (4000L)
        advanceTimeBy(4_500L)
        testScheduler.runCurrent()
        assertNull(manager.saleSuccessMessage.value)
    }

    @Test
    fun `setPaymentProcessing updates processing state and message`() {
        val manager = CustomerDisplayManager(
            appContext = context,
            cartRepository = cartRepository,
            settingsRepository = fakeSettingsRepository,
            cashCloseReader = fakeCashCloseReader,
            activeCajaReader = fakeActiveCajaReader,
        )

        assertFalse(manager.isPaymentProcessing.value)
        assertNull(manager.paymentProcessingMessage.value)

        manager.setPaymentProcessing(true, "Enviando documento a DGI...")
        assertTrue(manager.isPaymentProcessing.value)
        assertEquals("Enviando documento a DGI...", manager.paymentProcessingMessage.value)

        manager.setPaymentProcessing(false)
        assertFalse(manager.isPaymentProcessing.value)
        assertNull(manager.paymentProcessingMessage.value)
    }

    private class FakePosSettingsRepository : PosSettingsRepository {
        val displayEnabledFlow = MutableStateFlow(false)
        override val customerDisplayEnabled: Flow<Boolean> = displayEnabledFlow

        override val selectedPrinterType: Flow<PrinterType> = MutableStateFlow(PrinterType.NONE)
        override val selectedCountry: Flow<ServerCountry?> = MutableStateFlow(null)
        override val factorySettings: Flow<TheFactorySettings> = MutableStateFlow(TheFactorySettings())
        override val allowEditPrices: Flow<Boolean> = MutableStateFlow(true)
        override val allowDiscounts: Flow<Boolean> = MutableStateFlow(true)
        override val noPrinterPdfFormat: Flow<com.amaxonia.pos.domain.model.printer.NoPrinterPdfFormat> =
            MutableStateFlow(com.amaxonia.pos.domain.model.printer.NoPrinterPdfFormat.FACTURA_CARTA)

        override suspend fun currentCountry(): ServerCountry? = null
        override suspend fun savePrinterType(printerType: PrinterType) {}
        override suspend fun saveFactorySettings(settings: TheFactorySettings) {}
        override suspend fun saveAllowEditPrices(enabled: Boolean) {}
        override suspend fun saveAllowDiscounts(enabled: Boolean) {}
        override suspend fun saveCustomerDisplayEnabled(enabled: Boolean) {
            displayEnabledFlow.value = enabled
        }
        override suspend fun saveNoPrinterPdfFormat(format: com.amaxonia.pos.domain.model.printer.NoPrinterPdfFormat) {}
    }

    private class FakeCashCloseReader : CashCloseContextReader {
        override suspend fun currentCountryCode(): String = "PA"
        override suspend fun selectedPrinterType(): PrinterType = PrinterType.NONE
        override suspend fun currentCompany(): CompanyIdentity? = CompanyIdentity("Test Store", "123", "db")
    }

    private class FakeActiveCajaReader : ActiveCajaReader {
        private val _caja = MutableStateFlow<Caja?>(null)
        override val activeCaja: StateFlow<Caja?> = _caja.asStateFlow()
    }
}
