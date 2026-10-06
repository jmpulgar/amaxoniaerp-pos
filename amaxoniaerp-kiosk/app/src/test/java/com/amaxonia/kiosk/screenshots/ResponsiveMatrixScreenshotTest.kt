package com.amaxonia.kiosk.screenshots

import android.app.Application
import androidx.compose.runtime.Composable
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCajaDto
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.payment.PaymentMethod
import com.amaxonia.kiosk.ui.accessibility.KioskLanguage
import com.amaxonia.kiosk.ui.admin.AdminMenuDialog
import com.amaxonia.kiosk.ui.attract.AdminUnlockActions
import com.amaxonia.kiosk.ui.attract.AttractContent
import com.amaxonia.kiosk.ui.attract.AttractUiState
import com.amaxonia.kiosk.ui.cajasetup.CajaSetupActions
import com.amaxonia.kiosk.ui.cajasetup.CajaSetupContent
import com.amaxonia.kiosk.ui.cajasetup.CajaSetupUiState
import com.amaxonia.kiosk.ui.customer.CustomerIdScreen
import com.amaxonia.kiosk.ui.customer.CustomerIdViewModel
import com.amaxonia.kiosk.ui.customizer.CustomizerScreen
import com.amaxonia.kiosk.ui.customizer.ProductCustomizerViewModel
import com.amaxonia.kiosk.ui.diningmode.DiningModeScreen
import com.amaxonia.kiosk.ui.diningmode.MODE_DINE_IN
import com.amaxonia.kiosk.ui.idle.IdleWarningDialog
import com.amaxonia.kiosk.ui.login.LoginActions
import com.amaxonia.kiosk.ui.login.LoginContent
import com.amaxonia.kiosk.ui.login.LoginUiState
import com.amaxonia.kiosk.ui.menu.MenuContent
import com.amaxonia.kiosk.ui.payment.OrderNumberScreen
import com.amaxonia.kiosk.ui.payment.PaymentContent
import com.amaxonia.kiosk.ui.payment.PaymentStep
import com.amaxonia.kiosk.ui.payment.PaymentUiState
import com.amaxonia.kiosk.ui.payment.YappyPaymentContent
import com.amaxonia.kiosk.ui.payment.YappyStep
import com.amaxonia.kiosk.ui.payment.YappyUiState
import com.amaxonia.kiosk.ui.paymentmethod.PaymentMethodContent
import com.amaxonia.kiosk.ui.paymentmethod.PaymentMethodStep
import com.amaxonia.kiosk.ui.paymentmethod.PaymentMethodUiState
import com.amaxonia.kiosk.ui.review.ReviewScreen
import com.amaxonia.kiosk.ui.review.ReviewViewModel
import com.amaxonia.kiosk.ui.tabletent.TableTentScreen
import com.amaxonia.kiosk.ui.tabletent.TableTentViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Device matrix: the same screens rendered through the app's root canvas on every kiosk shape we
 * support. The three K2 densities and the 1.3 font scale must produce identical images; the other
 * shapes must look deliberate. Output: `app/screenshots/matrix/<device>/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = KIOSK_QUALIFIERS, sdk = [34], application = Application::class)
class ResponsiveMatrixScreenshotTest {
    @get:Rule
    val shots = KioskScreenshotRule()

    @Test
    @Config(qualifiers = "es-w1080dp-h1920dp-port-mdpi")
    fun k2Mdpi() = matrix("k2_1080x1920_mdpi")

    @Test
    @Config(qualifiers = "es-w720dp-h1280dp-port-hdpi")
    fun k2Hdpi() = matrix("k2_1080x1920_hdpi")

    @Test
    @Config(qualifiers = "es-w540dp-h960dp-port-xhdpi")
    fun k2Xhdpi() = matrix("k2_1080x1920_xhdpi")

    @Test
    @Config(qualifiers = "es-w720dp-h1280dp-port-hdpi", fontScale = 1.3f)
    fun k2FontScale() {
        // The device really runs with the bigger system font; the canvas must ignore it.
        assertEquals(1.3f, shots.compose.activity.resources.configuration.fontScale, 0.001f)
        matrix("k2_1080x1920_hdpi_font130")
    }

    @Test
    @Config(qualifiers = "es-w800dp-h1280dp-port-mdpi")
    fun tabletPortrait() = matrix("portrait_800x1280")

    @Test
    @Config(qualifiers = "es-w1080dp-h2340dp-port-mdpi")
    fun tallPortrait() = matrix("portrait_1080x2340")

    @Test
    @Config(qualifiers = "es-w1920dp-h1080dp-land-mdpi")
    fun landscapeFullHd() = matrix("landscape_1920x1080")

    @Test
    @Config(qualifiers = "es-w1366dp-h768dp-land-mdpi")
    fun landscapeHd() = matrix("landscape_1366x768")

    private fun matrix(device: String) = shots.snapSequence("matrix/$device", screens())

    @Suppress("LongMethod")
    private fun screens(): List<Shot> {
        val customizer = ProductCustomizerViewModel(ScreenshotFixtures.comboDobleQueso, OrderGraph())
        val extras = ScreenshotFixtures.comboDobleQueso.modifierGroups.last()
        customizer.toggleOption(extras, extras.options.first())
        val review = ReviewViewModel(ScreenshotFixtures.filledOrderGraph())
        val tableTent = TableTentViewModel(OrderGraph()).apply { "12".forEach(::onDigit) }
        val customer =
            CustomerIdViewModel(OrderGraph()).apply {
                toggleCustomBilling(true)
                "8-123-4567".forEach(::onKeypadInput)
                onNameChanged("María González")
            }
        val total = Money.fromString("27.43")
        return listOf(
            Shot("01_attract", orderingScreen = false) {
                AttractContent(
                    uiState = AttractUiState(isLoading = false),
                    onStartOrder = {},
                    onSecretTap = {},
                    onMediaFinished = {},
                    adminActions = AdminUnlockActions({}, {}, {}),
                    language = KioskLanguage.SPANISH,
                    onLanguageSelected = {},
                )
            },
            Shot("00_login", orderingScreen = false) {
                LoginContent(
                    uiState = LoginUiState(serverUrl = "https://api.listoerp.app/", username = "cajero1"),
                    actions = LoginActions.NoOp,
                )
            },
            Shot("00_caja_setup", orderingScreen = false) {
                CajaSetupContent(uiState = cajaState(), actions = CajaSetupActions.NoOp)
            },
            Shot("02_dining_mode") {
                DiningModeScreen(currentMode = MODE_DINE_IN, onModeSelected = {}, onBack = {})
            },
            Shot("03_menu") { Menu() },
            Shot("03_menu", variant = Variant.LOW_REACH) { Menu() },
            Shot("04_customizer") { CustomizerScreen(viewModel = customizer, onDismiss = {}) },
            Shot("05_review") {
                ReviewScreen(viewModel = review, onContinueShopping = {}, onProceedToCheckout = {}, onCancelOrder = {})
            },
            Shot("05_review", variant = Variant.LOW_REACH) {
                ReviewScreen(viewModel = review, onContinueShopping = {}, onProceedToCheckout = {}, onCancelOrder = {})
            },
            Shot("06_table_tent") { TableTentScreen(viewModel = tableTent, onConfirmed = {}, onBack = {}) },
            Shot("07_customer_id_invoice") { CustomerIdScreen(viewModel = customer, onCustomerConfirmed = {}, onBack = {}) },
            Shot("08_payment_method") {
                PaymentMethodContent(
                    uiState =
                        PaymentMethodUiState(
                            step = PaymentMethodStep.Ready,
                            total = total,
                            methods = listOf(PaymentMethod.CARD, PaymentMethod.YAPPY),
                        ),
                    onSelect = {},
                    onRetry = {},
                    onBack = {},
                    onBackToOrder = {},
                )
            },
            Shot("09_card_payment") {
                PaymentContent(
                    uiState = PaymentUiState(step = PaymentStep.AwaitingPayment(secondsRemaining = 74), totalAmount = total),
                    onCancel = {},
                    onRetry = {},
                )
            },
            Shot("10_yappy_qr", waitForAsync = true) {
                YappyPaymentContent(
                    uiState =
                        YappyUiState(
                            step =
                                YappyStep.AwaitingScan(
                                    qrHash = "https://yappy.com.pa/qr/7f3a9c1e-27.43-AMAXONIA-KIOSK-K1-000127",
                                    secondsRemaining = 241,
                                    totalSeconds = 300,
                                ),
                            totalAmount = total,
                        ),
                    onLeave = {},
                    onNewCode = {},
                )
            },
            Shot("12_order_number", orderingScreen = false, settleMs = 1_200L) {
                OrderNumberScreen(orderInfo = ScreenshotFixtures.completedOrder(), onFinish = {})
            },
            Shot("13_idle_warning", allWindows = true) {
                Menu()
                IdleWarningDialog(remainingSeconds = 12, onContinue = {}, onCancel = {})
            },
            Shot("14_admin_menu", orderingScreen = false, allWindows = true) {
                AttractContent(
                    uiState = AttractUiState(isLoading = false),
                    onStartOrder = {},
                    onSecretTap = {},
                    onMediaFinished = {},
                    adminActions = AdminUnlockActions({}, {}, {}),
                    language = KioskLanguage.SPANISH,
                    onLanguageSelected = {},
                )
                AdminMenuDialog(
                    isLockTaskActive = true,
                    hasLastOrder = true,
                    sessionLabel = "Caja Kiosco Terraza · Momi Café",
                    onChangeCaja = {},
                    onLogout = {},
                    onTestPrint = {},
                    onReprintLastReceipt = {},
                    onToggleLockTask = {},
                    onDismiss = {},
                )
            },
        )
    }

    @Composable
    private fun Menu() =
        MenuContent(
            uiState = ScreenshotFixtures.menuState(),
            diningMode = MODE_DINE_IN,
            onCategorySelected = {},
            onProductClicked = {},
            onRetry = {},
            onViewCart = {},
            onBackToAttract = {},
        )

    private fun cajaState() =
        CajaSetupUiState(
            companyName = "Momi Café",
            username = "cajero1",
            isLoading = false,
            cajas =
                listOf(
                    KioskCajaDto(idCaja = "c-1", codCaja = "001", descripcion = "Caja Kiosco Entrada", sucursalNombre = "Casa Matriz"),
                    KioskCajaDto(idCaja = "c-2", codCaja = "002", descripcion = "Caja Kiosco Terraza", sucursalNombre = "Casa Matriz"),
                    KioskCajaDto(idCaja = "c-3", codCaja = "010", descripcion = "Caja Principal", sucursalNombre = "Albrook Mall"),
                ),
            selectedCajaId = "c-2",
            prefix = "K2",
        )
}
