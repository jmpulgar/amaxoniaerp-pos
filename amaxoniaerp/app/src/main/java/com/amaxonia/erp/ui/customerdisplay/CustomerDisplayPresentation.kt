package com.amaxonia.erp.ui.customerdisplay

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
import com.amaxonia.erp.ui.theme.PosTheme
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class CustomerDisplayPresentation(
    outerContext: Context,
    display: Display,
    private val stateFlow: StateFlow<CustomerDisplayState>,
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

        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        window?.apply {
            // Desactiva el foco para que los escáneres de códigos de barra (USB/HID)
            // siempre escriban en la pantalla del cajero.
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            decorView.let { decor ->
                decor.setViewTreeLifecycleOwner(this@CustomerDisplayPresentation)
                decor.setViewTreeSavedStateRegistryOwner(this@CustomerDisplayPresentation)
                decor.setViewTreeViewModelStoreOwner(this@CustomerDisplayPresentation)
            }
        }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@CustomerDisplayPresentation)
            setViewTreeSavedStateRegistryOwner(this@CustomerDisplayPresentation)
            setViewTreeViewModelStoreOwner(this@CustomerDisplayPresentation)

            setContent {
                val state by stateFlow.collectAsState()
                PosTheme {
                    CustomerFacingScreen(state = state)
                }
            }
        }

        setContentView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
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
