package com.amaxonia.kiosk.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.amaxonia.kiosk.ui.accessibility.AccessibilityActions
import com.amaxonia.kiosk.ui.accessibility.AccessibilityState
import com.amaxonia.kiosk.ui.accessibility.KioskChrome
import com.amaxonia.kiosk.ui.theme.AmaxoniaKioskTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Visual variants rendered for each screen. */
enum class Variant(
    val suffix: String,
    val highContrast: Boolean,
    val accessible: Boolean,
) {
    NORMAL("", false, false),
    HIGH_CONTRAST("_hc", true, false),
    LOW_REACH("_baja", false, true),
}

private const val SETTLE_MS = 2_500L
private const val ASYNC_WAIT_MS = 50L
private const val ASYNC_POLLS = 40

private val roborazziActive: Boolean
    get() =
        listOf("roborazzi.test.record", "roborazzi.test.verify", "roborazzi.test.compare")
            .any { System.getProperty(it) == "true" }

/**
 * Renders kiosk screens inside the real app chrome (accessibility strip + "pantalla baja" container)
 * and writes PNGs to `app/screenshots/`. Skipped unless a Roborazzi task (record/verify/compare) runs.
 */
class KioskScreenshotRule : TestRule {
    val compose: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity> =
        createAndroidComposeRule<ComponentActivity>()

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        val guarded =
            object : Statement() {
                override fun evaluate() {
                    Assume.assumeTrue("Screenshot tests only run under Roborazzi tasks", roborazziActive)
                    base.evaluate()
                }
            }
        return compose.apply(guarded, description)
    }

    /** Renders [content] in the kiosk frame, lets entrance animations settle and captures it. */
    @Suppress("LongParameterList")
    fun snap(
        name: String,
        variant: Variant = Variant.NORMAL,
        orderingScreen: Boolean = true,
        settleMs: Long = SETTLE_MS,
        waitForAsync: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        render(variant, orderingScreen, content)
        settle(settleMs, waitForAsync)
        compose.onRoot().captureRoboImage("screenshots/$name${variant.suffix}.png")
    }

    /** Like [snap] but captures every window, so dialogs are included. */
    @OptIn(ExperimentalRoborazziApi::class)
    fun snapScreen(
        name: String,
        variant: Variant = Variant.NORMAL,
        orderingScreen: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        render(variant, orderingScreen, content)
        settle(SETTLE_MS, waitForAsync = false)
        captureScreenRoboImage("screenshots/$name${variant.suffix}.png")
    }

    private fun render(
        variant: Variant,
        orderingScreen: Boolean,
        content: @Composable () -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AmaxoniaKioskTheme(highContrast = variant.highContrast) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KioskChrome(
                        state = AccessibilityState(isAccessibleMode = variant.accessible, isHighContrast = variant.highContrast),
                        isOrderingScreen = orderingScreen,
                        actions = AccessibilityActions({}, {}, {}),
                        content = content,
                    )
                }
            }
        }
    }

    private fun settle(
        settleMs: Long,
        waitForAsync: Boolean,
    ) {
        compose.mainClock.advanceTimeBy(settleMs)
        if (waitForAsync) {
            // Work on background dispatchers (QR rendering) completes in real time.
            repeat(ASYNC_POLLS) {
                Thread.sleep(ASYNC_WAIT_MS)
                compose.mainClock.advanceTimeByFrame()
            }
        }
        compose.mainClock.advanceTimeBy(settleMs)
    }
}
