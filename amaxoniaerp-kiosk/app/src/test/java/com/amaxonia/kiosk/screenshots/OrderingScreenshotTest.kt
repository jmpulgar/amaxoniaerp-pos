package com.amaxonia.kiosk.screenshots

import android.app.Application
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.ui.accessibility.KioskLanguage
import com.amaxonia.kiosk.ui.attract.AdminUnlockActions
import com.amaxonia.kiosk.ui.attract.AttractContent
import com.amaxonia.kiosk.ui.attract.AttractUiState
import com.amaxonia.kiosk.ui.customizer.CustomizerScreen
import com.amaxonia.kiosk.ui.customizer.ProductCustomizerViewModel
import com.amaxonia.kiosk.ui.diningmode.DiningModeScreen
import com.amaxonia.kiosk.ui.diningmode.MODE_DINE_IN
import com.amaxonia.kiosk.ui.idle.IdleWarningDialog
import com.amaxonia.kiosk.ui.menu.MenuContent
import com.amaxonia.kiosk.ui.menu.SuggestionsOverlay
import com.amaxonia.kiosk.ui.review.ReviewScreen
import com.amaxonia.kiosk.ui.review.ReviewViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = KIOSK_QUALIFIERS, sdk = [34], application = Application::class)
class OrderingScreenshotTest {
    @get:Rule
    val shots = KioskScreenshotRule()

    private val noAdmin = AdminUnlockActions({}, {}, {})

    @Test
    fun attract() = attract(Variant.NORMAL)

    @Test
    fun attractHighContrast() = attract(Variant.HIGH_CONTRAST)

    private fun attract(variant: Variant) =
        shots.snap("01_attract", variant, orderingScreen = false) {
            AttractContent(
                uiState = AttractUiState(isLoading = false),
                onStartOrder = {},
                onSecretTap = {},
                onMediaFinished = {},
                adminActions = noAdmin,
                language = KioskLanguage.SPANISH,
                onLanguageSelected = {},
            )
        }

    @Test
    fun diningMode() = diningMode(Variant.NORMAL)

    @Test
    fun diningModeHighContrast() = diningMode(Variant.HIGH_CONTRAST)

    @Test
    fun diningModeLowReach() = diningMode(Variant.LOW_REACH)

    private fun diningMode(variant: Variant) =
        shots.snap("02_dining_mode", variant) {
            DiningModeScreen(currentMode = MODE_DINE_IN, onModeSelected = {}, onBack = {})
        }

    @Test
    fun menu() = menu(Variant.NORMAL)

    @Test
    fun menuHighContrast() = menu(Variant.HIGH_CONTRAST)

    @Test
    fun menuLowReach() = menu(Variant.LOW_REACH)

    private fun menu(variant: Variant) =
        shots.snap("03_menu", variant) {
            MenuContent(
                uiState = ScreenshotFixtures.menuState(),
                diningMode = MODE_DINE_IN,
                onCategorySelected = {},
                onProductClicked = {},
                onRetry = {},
                onViewCart = {},
                onBackToAttract = {},
            )
        }

    @Test
    fun menuEmptyCart() =
        shots.snap("03_menu_empty_cart") {
            MenuContent(
                uiState = ScreenshotFixtures.menuState(cartCount = 0, cartSubtotal = "0"),
                diningMode = MODE_DINE_IN,
                onCategorySelected = {},
                onProductClicked = {},
                onRetry = {},
                onViewCart = {},
                onBackToAttract = {},
            )
        }

    @Test
    fun menuHome() =
        shots.snap("03_menu_home") {
            MenuContent(
                uiState = ScreenshotFixtures.menuState(selectedCategoryId = null),
                diningMode = MODE_DINE_IN,
                onCategorySelected = {},
                onProductClicked = {},
                onRetry = {},
                onViewCart = {},
                onBackToAttract = {},
            )
        }

    @Test
    fun menuSuggestions() =
        shots.snap("03_menu_suggestions") {
            val state = ScreenshotFixtures.menuState()
            MenuContent(
                uiState = state,
                diningMode = MODE_DINE_IN,
                onCategorySelected = {},
                onProductClicked = {},
                onRetry = {},
                onViewCart = {},
                onBackToAttract = {},
            )
            SuggestionsOverlay(
                suggestions = state.items.filter { it.categoryId in AddOnCategories }.take(6),
                currency = state.currency,
                onProductClicked = {},
                onDone = {},
            )
        }

    @Test
    fun customizerReview() {
        val viewModel = ProductCustomizerViewModel(ScreenshotFixtures.comboDobleQueso, OrderGraph())
        val extras = ScreenshotFixtures.comboDobleQueso.modifierGroups.last()
        viewModel.toggleOption(extras, extras.options.first())
        shots.snap("04_customizer_review") {
            CustomizerScreen(viewModel = viewModel, onDismiss = {}, initialStep = ScreenshotFixtures.comboDobleQueso.modifierGroups.size)
        }
    }

    @Test
    fun customizer() = customizer(Variant.NORMAL)

    @Test
    fun customizerHighContrast() = customizer(Variant.HIGH_CONTRAST)

    @Test
    fun customizerLowReach() = customizer(Variant.LOW_REACH)

    private fun customizer(variant: Variant) {
        val viewModel = ProductCustomizerViewModel(ScreenshotFixtures.comboDobleQueso, OrderGraph())
        val extras = ScreenshotFixtures.comboDobleQueso.modifierGroups.last()
        viewModel.toggleOption(extras, extras.options.first())
        shots.snap("04_customizer", variant) {
            CustomizerScreen(viewModel = viewModel, onDismiss = {})
        }
    }

    @Test
    fun review() = review(Variant.NORMAL)

    @Test
    fun reviewHighContrast() = review(Variant.HIGH_CONTRAST)

    @Test
    fun reviewLowReach() = review(Variant.LOW_REACH)

    private fun review(variant: Variant) {
        val viewModel = ReviewViewModel(ScreenshotFixtures.filledOrderGraph())
        shots.snap("05_review", variant) {
            ReviewScreen(viewModel = viewModel, onContinueShopping = {}, onProceedToCheckout = {}, onCancelOrder = {})
        }
    }

    @Test
    fun reviewEmpty() {
        val viewModel = ReviewViewModel(OrderGraph())
        shots.snap("05_review_empty") {
            ReviewScreen(viewModel = viewModel, onContinueShopping = {}, onProceedToCheckout = {}, onCancelOrder = {})
        }
    }

    @Test
    fun idleWarning() =
        shots.snapScreen("13_idle_warning") {
            MenuContent(
                uiState = ScreenshotFixtures.menuState(),
                diningMode = MODE_DINE_IN,
                onCategorySelected = {},
                onProductClicked = {},
                onRetry = {},
                onViewCart = {},
                onBackToAttract = {},
            )
            IdleWarningDialog(remainingSeconds = 12, onContinue = {}, onCancel = {})
        }
}

private val AddOnCategories = setOf(ScreenshotFixtures.SIDES, ScreenshotFixtures.DRINKS)

/** SUNMI K2 24" portrait: 1080 x 1920 px at ~mdpi. */
const val KIOSK_QUALIFIERS = "es-w1080dp-h1920dp-port-mdpi"
