package com.amaxonia.erp.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

object KeyboardHelper {

    fun findActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) return currentContext
            currentContext = currentContext.baseContext
        }
        return null
    }

    /**
     * Fuerza la visualización del teclado virtual en pantalla (IME),
     * superando la supresión automática de Android cuando hay periféricos
     * o lectores de códigos de barras (HID) conectados en terminales POS.
     */
    @Suppress("DEPRECATION")
    fun forceShow(view: View) {
        val context = view.context
        val activity = findActivity(context)

        runCatching {
            // 1. Enfoque moderno con WindowInsetsControllerCompat (Android 11 / API 30+)
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.ime())
            }

            // 2. InputMethodManager seguro
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * Oculta el teclado virtual de manera explícita.
     */
    fun hide(view: View) {
        val context = view.context
        val activity = findActivity(context)
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.hide(WindowInsetsCompat.Type.ime())
        }
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * Determina si se detecta un teclado físico o lector de código de barras HID conectado.
     */
    fun hasHardwareKeyboard(context: Context): Boolean {
        val config = context.resources.configuration
        return config.keyboard != Configuration.KEYBOARD_NOKEYS ||
            config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    }

    /**
     * Verifica si el ajuste del sistema para mostrar teclado virtual con teclado físico está activo.
     */
    fun isVirtualKeyboardEnabledWithHardware(context: Context): Boolean {
        return runCatching {
            Settings.Secure.getInt(
                context.contentResolver,
                "show_ime_with_hard_keyboard",
                0,
            ) == 1
        }.getOrDefault(false)
    }
}

/**
 * Modificador para Jetpack Compose que asegura que el teclado virtual en pantalla se despliegue
 * forzosamente cada vez que el campo de texto reciba foco o sea pulsado por el usuario,
 * evitando que el sistema operativo lo suprima en Android 11 o en terminales POS.
 */
fun Modifier.forceShowKeyboardOnTouch(): Modifier = composed {
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current

    this
        .onFocusChanged { state ->
            if (state.isFocused) {
                keyboardController?.show()
                KeyboardHelper.forceShow(view)
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        keyboardController?.show()
                        KeyboardHelper.forceShow(view)
                    }
                }
            }
        }
}
