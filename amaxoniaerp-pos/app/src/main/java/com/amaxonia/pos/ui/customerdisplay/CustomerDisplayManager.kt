package com.amaxonia.pos.ui.customerdisplay

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.CashCloseContextReader
import com.amaxonia.pos.domain.repository.PosSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CustomerDisplayManager(
    private val appContext: Context,
    private val cartRepository: CartRepository,
    private val settingsRepository: PosSettingsRepository,
    private val cashCloseReader: CashCloseContextReader,
    private val activeCajaReader: ActiveCajaReader,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    companion object {
        private const val TAG = "CustomerDisplayManager"
        private const val SUCCESS_MESSAGE_DURATION_MS = 4_000L
    }

    private val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentActivity: Activity? = null
    private var currentPresentation: CustomerDisplayPresentation? = null
    private var managerScope: CoroutineScope? = null
    private var observeJob: Job? = null
    private var successTimerJob: Job? = null

    private val _isSecondaryDisplayAvailable = MutableStateFlow(false)
    val isSecondaryDisplayAvailable: StateFlow<Boolean> = _isSecondaryDisplayAvailable.asStateFlow()

    private val _isDisplayEnabled = MutableStateFlow(false)
    val isDisplayEnabled: StateFlow<Boolean> = _isDisplayEnabled.asStateFlow()

    private val _saleSuccessMessage = MutableStateFlow<String?>(null)
    val saleSuccessMessage: StateFlow<String?> = _saleSuccessMessage.asStateFlow()

    private val _isPaymentProcessing = MutableStateFlow(false)
    val isPaymentProcessing: StateFlow<Boolean> = _isPaymentProcessing.asStateFlow()

    private val _paymentProcessingMessage = MutableStateFlow<String?>(null)
    val paymentProcessingMessage: StateFlow<String?> = _paymentProcessingMessage.asStateFlow()

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            SafeLog.d(TAG, "Display added: $displayId")
            updateDisplayAvailability()
            refreshPresentation()
        }

        override fun onDisplayRemoved(displayId: Int) {
            SafeLog.d(TAG, "Display removed: $displayId")
            updateDisplayAvailability()
            refreshPresentation()
        }

        override fun onDisplayChanged(displayId: Int) {
            SafeLog.d(TAG, "Display changed: $displayId")
            updateDisplayAvailability()
            refreshPresentation()
        }
    }

    fun start(activity: Activity) {
        currentActivity = activity
        managerScope?.cancel()
        val scope = CoroutineScope(mainDispatcher + SupervisorJob())
        managerScope = scope

        displayManager.registerDisplayListener(displayListener, mainHandler)
        updateDisplayAvailability()

        observeJob = scope.launch {
            settingsRepository.customerDisplayEnabled.collectLatest { enabled ->
                _isDisplayEnabled.value = enabled
                refreshPresentation()
            }
        }
    }

    fun stop() {
        displayManager.unregisterDisplayListener(displayListener)
        observeJob?.cancel()
        observeJob = null
        successTimerJob?.cancel()
        successTimerJob = null
        managerScope?.cancel()
        managerScope = null
        dismissPresentation()
        currentActivity = null
    }

    fun showSaleSuccess(message: String = "¡Muchas gracias por su compra!") {
        _saleSuccessMessage.value = message
        successTimerJob?.cancel()
        val scope = managerScope ?: CoroutineScope(mainDispatcher + SupervisorJob()).also { managerScope = it }
        successTimerJob = scope.launch {
            delay(SUCCESS_MESSAGE_DURATION_MS)
            _saleSuccessMessage.value = null
        }
    }

    fun setPaymentProcessing(isProcessing: Boolean, message: String? = null) {
        _isPaymentProcessing.value = isProcessing
        _paymentProcessingMessage.value = if (isProcessing) message ?: "Validando cobro..." else null
    }

    fun findSecondaryDisplay(): Display? {
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (presentationDisplays.isNotEmpty()) {
            return presentationDisplays[0]
        }
        return displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }

    private fun updateDisplayAvailability() {
        val secondary = findSecondaryDisplay()
        _isSecondaryDisplayAvailable.value = (secondary != null)
    }

    private fun refreshPresentation() {
        val activity = currentActivity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            dismissPresentation()
            return
        }

        val secondaryDisplay = findSecondaryDisplay()
        if (secondaryDisplay == null || !_isDisplayEnabled.value) {
            dismissPresentation()
            return
        }

        if (currentPresentation?.display?.displayId == secondaryDisplay.displayId && currentPresentation?.isShowing == true) {
            return
        }

        dismissPresentation()

        runCatching {
            val presentation = CustomerDisplayPresentation(
                outerContext = activity,
                display = secondaryDisplay,
                cartRepository = cartRepository,
                cashCloseReader = cashCloseReader,
                activeCajaReader = activeCajaReader,
                saleSuccessMessageFlow = _saleSuccessMessage,
                isPaymentProcessingFlow = _isPaymentProcessing,
                paymentProcessingMessageFlow = _paymentProcessingMessage,
            )
            presentation.show()
            currentPresentation = presentation
            SafeLog.i(TAG, "Customer display presentation launched on display ${secondaryDisplay.displayId} (${secondaryDisplay.name})")
        }.onFailure { error ->
            SafeLog.e(TAG, "Failed to launch customer display presentation: ${error.message}")
            currentPresentation = null
        }
    }

    private fun dismissPresentation() {
        currentPresentation?.let { presentation ->
            runCatching {
                if (presentation.isShowing) {
                    presentation.dismiss()
                }
            }.onFailure { error ->
                SafeLog.w(TAG, "Error dismissing presentation: ${error.message}")
            }
        }
        currentPresentation = null
    }
}
