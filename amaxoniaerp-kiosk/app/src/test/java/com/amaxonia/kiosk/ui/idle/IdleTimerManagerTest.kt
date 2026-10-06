package com.amaxonia.kiosk.ui.idle

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdleTimerManagerTest {
    @Test
    fun initialState_isNotEnabledAndWarningNotVisible() {
        val manager = IdleTimerManager(idleTimeoutSeconds = 60, warningTimeoutSeconds = 20)
        val state = manager.state.value

        assertFalse(state.isEnabled)
        assertFalse(state.isWarningVisible)
        assertEquals(20, state.remainingSeconds)
    }

    @Test
    fun timerDisabled_doesNotTickOrTriggerWarning() =
        runTest {
            var timedOut = false
            val manager =
                IdleTimerManager(
                    idleTimeoutSeconds = 5,
                    warningTimeoutSeconds = 3,
                    onTimeoutExpired = { timedOut = true },
                )

            manager.start(this)
            advanceTimeBy(10_000)

            assertFalse(manager.state.value.isWarningVisible)
            assertFalse(timedOut)
            manager.stop()
        }

    @Test
    fun timerEnabled_idleTimeoutTriggersWarningDialog() =
        runTest {
            val manager =
                IdleTimerManager(
                    idleTimeoutSeconds = 5,
                    warningTimeoutSeconds = 3,
                )

            manager.start(this)
            manager.setEnabled(true)

            // Advance 4 seconds (1 second remaining on idle)
            advanceTimeBy(4000)
            runCurrent()
            assertFalse(manager.state.value.isWarningVisible)

            // Advance 1 more second (idle timeout reached)
            advanceTimeBy(1000)
            runCurrent()
            assertTrue(manager.state.value.isWarningVisible)
            assertEquals(3, manager.state.value.remainingSeconds)

            manager.stop()
        }

    @Test
    fun userActivity_resetsIdleCountdown() =
        runTest {
            val manager =
                IdleTimerManager(
                    idleTimeoutSeconds = 5,
                    warningTimeoutSeconds = 3,
                )

            manager.start(this)
            manager.setEnabled(true)

            // Advance 4 seconds
            advanceTimeBy(4000)
            runCurrent()
            assertFalse(manager.state.value.isWarningVisible)

            // User interacts with screen -> resets countdown to 5s
            manager.onUserActivity()

            // Advance 4 more seconds -> still not timed out because reset
            advanceTimeBy(4000)
            runCurrent()
            assertFalse(manager.state.value.isWarningVisible)

            manager.stop()
        }

    @Test
    fun continueOrdering_dismissesWarningAndResetsIdleTimer() =
        runTest {
            val manager =
                IdleTimerManager(
                    idleTimeoutSeconds = 3,
                    warningTimeoutSeconds = 3,
                )

            manager.start(this)
            manager.setEnabled(true)

            // Reach warning
            advanceTimeBy(3000)
            runCurrent()
            assertTrue(manager.state.value.isWarningVisible)

            // Click continue
            manager.continueOrdering()
            runCurrent()
            assertFalse(manager.state.value.isWarningVisible)

            // Verify timer is running fresh again
            advanceTimeBy(2000)
            runCurrent()
            assertFalse(manager.state.value.isWarningVisible)

            advanceTimeBy(1000)
            runCurrent()
            assertTrue(manager.state.value.isWarningVisible)

            manager.stop()
        }

    @Test
    fun warningCountdownExpiry_triggersOnTimeoutExpired() =
        runTest {
            var timedOut = false
            val manager =
                IdleTimerManager(
                    idleTimeoutSeconds = 3,
                    warningTimeoutSeconds = 3,
                    onTimeoutExpired = { timedOut = true },
                )

            manager.start(this)
            manager.setEnabled(true)

            // Advance to warning (3s)
            advanceTimeBy(3000)
            runCurrent()
            assertTrue(manager.state.value.isWarningVisible)
            assertFalse(timedOut)

            // Advance through warning (3s)
            advanceTimeBy(3000)
            runCurrent()
            assertFalse(manager.state.value.isWarningVisible)
            assertTrue(timedOut)

            manager.stop()
        }

    @Test
    fun cancelOrder_triggersOnTimeoutExpiredAndDismissesWarning() =
        runTest {
            var timedOut = false
            val manager =
                IdleTimerManager(
                    idleTimeoutSeconds = 5,
                    warningTimeoutSeconds = 5,
                    onTimeoutExpired = { timedOut = true },
                )

            manager.start(this)
            manager.setEnabled(true)
            advanceTimeBy(5000)
            runCurrent()
            assertTrue(manager.state.value.isWarningVisible)

            manager.cancelOrder()
            runCurrent()
            assertFalse(manager.state.value.isWarningVisible)
            assertTrue(timedOut)

            manager.stop()
        }

    @Test
    fun setEnabledFalse_hidesWarningAndDisablesWatcher() =
        runTest {
            val manager =
                IdleTimerManager(
                    idleTimeoutSeconds = 3,
                    warningTimeoutSeconds = 3,
                )

            manager.start(this)
            manager.setEnabled(true)
            advanceTimeBy(3000)
            runCurrent()
            assertTrue(manager.state.value.isWarningVisible)

            manager.setEnabled(false)
            runCurrent()
            assertFalse(manager.state.value.isEnabled)
            assertFalse(manager.state.value.isWarningVisible)

            manager.stop()
        }
}
