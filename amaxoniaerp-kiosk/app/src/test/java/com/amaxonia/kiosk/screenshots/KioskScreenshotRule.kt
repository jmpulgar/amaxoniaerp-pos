package com.amaxonia.kiosk.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.amaxonia.kiosk.ui.accessibility.AccessibilityActions
import com.amaxonia.kiosk.ui.accessibility.AccessibilityState
import com.amaxonia.kiosk.ui.accessibility.KioskChrome
import com.amaxonia.kiosk.ui.theme.AmaxoniaKioskTheme
import com.amaxonia.kiosk.ui.theme.KioskScaledCanvas
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** One screen of a [KioskScreenshotRule.snapSequence]. */
@Suppress("LongParameterList")
class Shot(
    val name: String,
    val variant: Variant = Variant.NORMAL,
    val orderingScreen: Boolean = true,
    val settleMs: Long = SETTLE_MS,
    val waitForAsync: Boolean = false,
    val allWindows: Boolean = false,
    val content: @Composable () -> Unit,
)

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

internal const val SETTLE_MS = 2_500L
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

    /**
     * Renders every [shots] entry in turn inside one activity (one test per device configuration)
     * and writes them to `app/screenshots/[dir]/`. Dialog shots capture every window.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    fun snapSequence(
        dir: String,
        shots: List<Shot>,
    ) {
        var current by mutableStateOf(shots.first())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            key(current.name) {
                KioskFrame(current.variant, current.orderingScreen, current.content)
            }
        }
        shots.forEach { shot ->
            compose.runOnUiThread {
                current = shot
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeByFrame()
            settle(shot.settleMs, shot.waitForAsync)
            val path = "screenshots/$dir/${shot.name}${shot.variant.suffix}.png"
            if (shot.allWindows) captureScreenRoboImage(path) else compose.onRoot().captureRoboImage(path)
        }
    }

    private fun render(
        variant: Variant,
        orderingScreen: Boolean,
        content: @Composable () -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent { KioskFrame(variant, orderingScreen, content) }
    }

    @Composable
    private fun KioskFrame(
        variant: Variant,
        orderingScreen: Boolean,
        content: @Composable () -> Unit,
    ) {
        // Same root canvas as MainActivity: the design canvas is scaled onto the window.
        KioskScaledCanvas {
            AmaxoniaKioskTheme(highContrast = variant.highContrast) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KioskChrome(
                        state = AccessibilityState(isAccessibleMode = variant.accessible, isHighContrast = variant.highContrast),
                        isOrderingScreen = orderingScreen,
                        actions = AccessibilityActions({}, {}),
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
