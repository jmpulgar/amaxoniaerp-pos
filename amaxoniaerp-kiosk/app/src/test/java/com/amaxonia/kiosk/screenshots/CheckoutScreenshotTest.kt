package com.amaxonia.kiosk.screenshots

import android.app.Application
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.payment.PaymentMethod
import com.amaxonia.kiosk.ui.customer.CustomerIdScreen
import com.amaxonia.kiosk.ui.customer.CustomerIdViewModel
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
import com.amaxonia.kiosk.ui.tabletent.TableTentScreen
import com.amaxonia.kiosk.ui.tabletent.TableTentViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = KIOSK_QUALIFIERS, sdk = [34], application = Application::class)
class CheckoutScreenshotTest {
    @get:Rule
    val shots = KioskScreenshotRule()

    private val total = Money.fromString("27.43")
    private val yappyQr = "https://yappy.com.pa/qr/7f3a9c1e-27.43-AMAXONIA-KIOSK-K1-000127"

    @Test
    fun tableTent() = tableTent(Variant.NORMAL)

    @Test
    fun tableTentHighContrast() = tableTent(Variant.HIGH_CONTRAST)

    @Test
    fun tableTentLowReach() = tableTent(Variant.LOW_REACH)

    private fun tableTent(variant: Variant) {
        val viewModel = TableTentViewModel(OrderGraph())
        viewModel.onDigit('1')
        viewModel.onDigit('2')
        shots.snap("06_table_tent", variant) {
            TableTentScreen(viewModel = viewModel, onConfirmed = {}, onBack = {})
        }
    }

    @Test
    fun customerId() = customerId(Variant.NORMAL, custom = false)

    @Test
    fun customerIdHighContrast() = customerId(Variant.HIGH_CONTRAST, custom = false)

    @Test
    fun customerIdCustom() = customerId(Variant.NORMAL, custom = true)

    @Test
    fun customerIdCustomLowReach() = customerId(Variant.LOW_REACH, custom = true)

    private fun customerId(
        variant: Variant,
        custom: Boolean,
    ) {
        val viewModel = CustomerIdViewModel(OrderGraph())
        if (custom) {
            viewModel.toggleCustomBilling(true)
            "8-123-4567".forEach(viewModel::onKeypadInput)
            viewModel.onNameChanged("María González")
        }
        shots.snap(if (custom) "07_customer_id_invoice" else "07_customer_id", variant) {
            CustomerIdScreen(viewModel = viewModel, onCustomerConfirmed = {}, onBack = {})
        }
    }

    @Test
    fun paymentMethod() = paymentMethod(Variant.NORMAL)

    @Test
    fun paymentMethodHighContrast() = paymentMethod(Variant.HIGH_CONTRAST)

    @Test
    fun paymentMethodLowReach() = paymentMethod(Variant.LOW_REACH)

    private fun paymentMethod(variant: Variant) =
        shots.snap("08_payment_method", variant) {
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
        }

    @Test
    fun cardPayment() = cardPayment(Variant.NORMAL)

    @Test
    fun cardPaymentHighContrast() = cardPayment(Variant.HIGH_CONTRAST)

    @Test
    fun cardPaymentLowReach() = cardPayment(Variant.LOW_REACH)

    private fun cardPayment(variant: Variant) =
        shots.snap("09_card_payment", variant) {
            PaymentContent(
                uiState = PaymentUiState(step = PaymentStep.AwaitingPayment(secondsRemaining = 74), totalAmount = total),
                onCancel = {},
                onRetry = {},
            )
        }

    @Test
    fun cardDeclined() =
        shots.snap("09_card_declined") {
            PaymentContent(
                uiState = PaymentUiState(step = PaymentStep.Declined("Fondos insuficientes"), totalAmount = total),
                onCancel = {},
                onRetry = {},
            )
        }

    @Test
    fun yappy() = yappy(Variant.NORMAL)

    @Test
    fun yappyHighContrast() = yappy(Variant.HIGH_CONTRAST)

    @Test
    fun yappyLowReach() = yappy(Variant.LOW_REACH)

    private fun yappy(variant: Variant) =
        shots.snap("10_yappy_qr", variant, waitForAsync = true) {
            YappyPaymentContent(
                uiState =
                    YappyUiState(
                        step = YappyStep.AwaitingScan(qrHash = yappyQr, secondsRemaining = 241, totalSeconds = 300),
                        totalAmount = total,
                    ),
                onLeave = {},
                onNewCode = {},
            )
        }

    @Test
    fun yappySuccess() =
        shots.snap("11_yappy_success") {
            YappyPaymentContent(
                uiState =
                    YappyUiState(
                        step = YappyStep.Success(ScreenshotFixtures.completedOrder()),
                        totalAmount = total,
                    ),
                onLeave = {},
                onNewCode = {},
            )
        }

    @Test
    fun orderNumber() = orderNumber(Variant.NORMAL)

    @Test
    fun orderNumberHighContrast() = orderNumber(Variant.HIGH_CONTRAST)

    private fun orderNumber(variant: Variant) =
        shots.snap("12_order_number", variant, orderingScreen = false, settleMs = 1_200L) {
            OrderNumberScreen(orderInfo = ScreenshotFixtures.completedOrder(), onFinish = {})
        }

    @Test
    fun orderNumberPrintFailed() =
        shots.snap("12_order_number_print_failed", orderingScreen = false, settleMs = 1_200L) {
            OrderNumberScreen(
                orderInfo = ScreenshotFixtures.completedOrder(tableTent = null),
                onFinish = {},
                receiptPrintFailed = true,
            )
        }
}
