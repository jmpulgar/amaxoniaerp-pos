package com.amaxonia.erp.ui.customerdisplay

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.ui.pos.CartItem
import com.amaxonia.erp.ui.pos.CompletedSaleInfo
import com.amaxonia.erp.ui.pos.PosCartSummary
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CustomerDisplayManager(
    private val appContext: Context,
    private val localStore: LocalStore,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    companion object {
        private const val TAG = "CustomerDisplayManager"
        private const val SUCCESS_DURATION_MS = 5_000L
    }

    private val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentActivity: Activity? = null
    private var currentPresentation: CustomerDisplayPresentation? = null
    private var managerScope: CoroutineScope? = null
    private var observeJob: Job? = null
    private var successTimerJob: Job? = null

    private val _state = MutableStateFlow(CustomerDisplayState())
    val state: StateFlow<CustomerDisplayState> = _state.asStateFlow()

    private val _isSecondaryDisplayAvailable = MutableStateFlow(false)
    val isSecondaryDisplayAvailable: StateFlow<Boolean> = _isSecondaryDisplayAvailable.asStateFlow()

    private val _isDisplayEnabled = MutableStateFlow(false)
    val isDisplayEnabled: StateFlow<Boolean> = _isDisplayEnabled.asStateFlow()

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.d(TAG, "Display added: $displayId")
            updateDisplayAvailability()
            refreshPresentation()
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.d(TAG, "Display removed: $displayId")
            updateDisplayAvailability()
            if (currentPresentation?.display?.displayId == displayId) {
                dismissPresentation()
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            Log.d(TAG, "Display changed: $displayId")
            updateDisplayAvailability()
            val secondary = findSecondaryDisplay()
            if (secondary == null) {
                dismissPresentation()
            } else if (currentPresentation?.display?.displayId != secondary.displayId) {
                refreshPresentation()
            }
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
            localStore.customerDisplayEnabledFlow().collectLatest { enabled ->
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

    fun updateCompanyInfo(companyName: String, countryCode: String, branchName: String) {
        _state.update { current ->
            current.copy(
                companyName = companyName,
                countryCode = countryCode,
                branchName = branchName,
            )
        }
    }

    fun updateCart(
        cart: List<CartItem>,
        summary: PosCartSummary,
        client: Client?,
        isProcessingSale: Boolean,
        completedSaleInfo: CompletedSaleInfo?,
        branchName: String = "",
    ) {
        _state.update { current ->
            current.copy(
                cartItems = cart,
                subtotal = summary.subtotal,
                tax = summary.tax,
                total = summary.total,
                clientName = client?.name?.takeIf { it != "CONSUMIDOR FINAL" && it.isNotBlank() },
                isPaymentProcessing = isProcessingSale,
                paymentProcessingMessage = if (isProcessingSale) "Validando y autorizando pago..." else null,
                completedSaleInfo = completedSaleInfo,
                branchName = branchName.ifBlank { current.branchName },
            )
        }
    }

    fun showSaleSuccess(info: CompletedSaleInfo) {
        _state.update { it.copy(completedSaleInfo = info, isPaymentProcessing = false) }
        successTimerJob?.cancel()
        val scope = managerScope ?: CoroutineScope(mainDispatcher + SupervisorJob()).also { managerScope = it }
        successTimerJob = scope.launch {
            delay(SUCCESS_DURATION_MS)
            _state.update { it.copy(completedSaleInfo = null) }
        }
    }

    fun findSecondaryDisplay(): Display? {
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val validPresentationDisplay = presentationDisplays.firstOrNull { display ->
            display.isValid && display.state != Display.STATE_OFF
        }
        if (validPresentationDisplay != null) {
            return validPresentationDisplay
        }
        // Fallback: only select a non-default display if it is valid, powered on, and explicitly has FLAG_PRESENTATION.
        // Never select unconfigured/unconnected HDMI or virtual display ports on Rockchip RK3568 devices.
        return displayManager.displays.firstOrNull { display ->
            display.displayId != Display.DEFAULT_DISPLAY &&
                display.isValid &&
                display.state != Display.STATE_OFF &&
                (display.flags and Display.FLAG_PRESENTATION != 0)
        }
    }

    private fun updateDisplayAvailability() {
        val secondary = findSecondaryDisplay()
        _isSecondaryDisplayAvailable.value = (secondary != null)
    }

    fun refreshPresentation() {
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
                stateFlow = _state,
            )
            presentation.show()
            currentPresentation = presentation
            Log.i(TAG, "Customer display presentation launched on display ${secondaryDisplay.displayId} (${secondaryDisplay.name})")
        }.onFailure { error ->
            Log.e(TAG, "Failed to launch customer display presentation: ${error.message}", error)
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
                Log.w(TAG, "Error dismissing presentation: ${error.message}")
            }
        }
        currentPresentation = null
    }
}
