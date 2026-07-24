package com.amaxonia.pos.ui.common

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.amaxonia.pos.core.device.DeviceClass
import com.amaxonia.pos.core.device.deviceClassFor

val LocalDeviceClass =
    compositionLocalOf<DeviceClass> {
        error("LocalDeviceClass not provided - wrap content with CompositionLocalProvider")
    }

@Composable
fun rememberDeviceClass(): DeviceClass {
    val smallestScreenWidthDp = LocalConfiguration.current.smallestScreenWidthDp
    return remember(smallestScreenWidthDp) { deviceClassFor(smallestScreenWidthDp) }
}

@Composable
fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
