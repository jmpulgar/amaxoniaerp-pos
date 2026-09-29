package com.amaxonia.pos.ui.customerdisplay

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.CashCloseContextReader
import com.amaxonia.pos.ui.theme.PosTheme
import kotlinx.coroutines.flow.StateFlow

class CustomerDisplayPresentation(
    outerContext: Context,
    display: Display,
    private val cartRepository: CartRepository,
    private val cashCloseReader: CashCloseContextReader,
    private val activeCajaReader: ActiveCajaReader,
    private val saleSuccessMessageFlow: StateFlow<String?>,
    private val isPaymentProcessingFlow: StateFlow<Boolean>,
    private val paymentProcessingMessageFlow: StateFlow<String?>,
) : Presentation(outerContext, display),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val customViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = customViewModelStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize state registry and lifecycle
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // Configure window: full screen, touch enabled for scrolling, not focusable to prevent capturing keyboard/scanner
        window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            decorView.let { decor ->
                decor.setViewTreeLifecycleOwner(this@CustomerDisplayPresentation)
                decor.setViewTreeSavedStateRegistryOwner(this@CustomerDisplayPresentation)
                decor.setViewTreeViewModelStoreOwner(this@CustomerDisplayPresentation)
            }
        }

        // Host Jetpack Compose content
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@CustomerDisplayPresentation)
            setViewTreeSavedStateRegistryOwner(this@CustomerDisplayPresentation)
            setViewTreeViewModelStoreOwner(this@CustomerDisplayPresentation)
            setContent {
                PosTheme {
                    CustomerFacingScreen(
                        cartRepository = cartRepository,
                        cashCloseReader = cashCloseReader,
                        activeCajaReader = activeCajaReader,
                        saleSuccessMessageFlow = saleSuccessMessageFlow,
                        isPaymentProcessingFlow = isPaymentProcessingFlow,
                        paymentProcessingMessageFlow = paymentProcessingMessageFlow,
                    )
                }
            }
        }

        setContentView(composeView)
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun dismiss() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        customViewModelStore.clear()
        super.dismiss()
    }
}
