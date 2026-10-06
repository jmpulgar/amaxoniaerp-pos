package com.amaxonia.kiosk.ui.idle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val DEFAULT_IDLE_TIMEOUT_SECONDS = 60
const val DEFAULT_WARNING_TIMEOUT_SECONDS = 20
private const val TICK_INTERVAL_MILLIS = 1000L

data class IdleTimerState(
    val isWarningVisible: Boolean = false,
    val remainingSeconds: Int = DEFAULT_WARNING_TIMEOUT_SECONDS,
    val isEnabled: Boolean = false,
)

class IdleTimerManager(
    private val idleTimeoutSeconds: Int = DEFAULT_IDLE_TIMEOUT_SECONDS,
    private val warningTimeoutSeconds: Int = DEFAULT_WARNING_TIMEOUT_SECONDS,
    var onTimeoutExpired: (() -> Unit)? = null,
) {
    private val _state =
        MutableStateFlow(
            IdleTimerState(remainingSeconds = warningTimeoutSeconds),
        )
    val state: StateFlow<IdleTimerState> = _state.asStateFlow()

    private var idleSecondsRemaining = idleTimeoutSeconds
    private var warningSecondsRemaining = warningTimeoutSeconds
    private var tickerJob: Job? = null

    fun start(scope: CoroutineScope) {
        tickerJob?.cancel()
        tickerJob =
            scope.launch {
                while (isActive) {
                    delay(TICK_INTERVAL_MILLIS)
                    onTick()
                }
            }
    }

    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            idleSecondsRemaining = idleTimeoutSeconds
            warningSecondsRemaining = warningTimeoutSeconds
            _state.update {
                it.copy(
                    isWarningVisible = false,
                    remainingSeconds = warningTimeoutSeconds,
                    isEnabled = false,
                )
            }
            return
        }

        idleSecondsRemaining = idleTimeoutSeconds
        warningSecondsRemaining = warningTimeoutSeconds
        _state.update {
            it.copy(
                isWarningVisible = false,
                remainingSeconds = warningTimeoutSeconds,
                isEnabled = true,
            )
        }
    }

    fun onUserActivity() {
        if (!_state.value.isEnabled || _state.value.isWarningVisible) {
            return
        }
        idleSecondsRemaining = idleTimeoutSeconds
    }

    fun continueOrdering() {
        idleSecondsRemaining = idleTimeoutSeconds
        warningSecondsRemaining = warningTimeoutSeconds
        _state.update {
            it.copy(
                isWarningVisible = false,
                remainingSeconds = warningTimeoutSeconds,
            )
        }
    }

    fun cancelOrder() {
        idleSecondsRemaining = idleTimeoutSeconds
        warningSecondsRemaining = warningTimeoutSeconds
        _state.update {
            it.copy(
                isWarningVisible = false,
                remainingSeconds = warningTimeoutSeconds,
            )
        }
        onTimeoutExpired?.invoke()
    }

    fun reset() {
        idleSecondsRemaining = idleTimeoutSeconds
        warningSecondsRemaining = warningTimeoutSeconds
        _state.update {
            it.copy(
                isWarningVisible = false,
                remainingSeconds = warningTimeoutSeconds,
            )
        }
    }

    private fun onTick() {
        if (!_state.value.isEnabled) {
            return
        }

        if (!_state.value.isWarningVisible) {
            tickIdle()
        } else {
            tickWarning()
        }
    }

    private fun tickIdle() {
        idleSecondsRemaining--
        if (idleSecondsRemaining <= 0) {
            warningSecondsRemaining = warningTimeoutSeconds
            _state.update {
                it.copy(
                    isWarningVisible = true,
                    remainingSeconds = warningSecondsRemaining,
                )
            }
        }
    }

    private fun tickWarning() {
        warningSecondsRemaining--
        if (warningSecondsRemaining <= 0) {
            _state.update {
                it.copy(
                    isWarningVisible = false,
                    remainingSeconds = 0,
                )
            }
            idleSecondsRemaining = idleTimeoutSeconds
            warningSecondsRemaining = warningTimeoutSeconds
            onTimeoutExpired?.invoke()
        } else {
            _state.update {
                it.copy(remainingSeconds = warningSecondsRemaining)
            }
        }
    }
}
