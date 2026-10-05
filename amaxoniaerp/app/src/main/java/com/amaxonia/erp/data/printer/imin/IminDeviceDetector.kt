package com.amaxonia.erp.data.printer.imin

import android.os.Build

object IminDeviceDetector {
    /**
     * Determines whether the current device is an iMin terminal with an integrated thermal printer
     * (such as the Swift 2 / Model I23M01).
     */
    fun isIminDevice(): Boolean =
        Build.MANUFACTURER.contains("imin", ignoreCase = true) ||
            Build.BRAND.contains("imin", ignoreCase = true) ||
            Build.MODEL.contains("I23M01", ignoreCase = true) ||
            Build.MODEL.contains("Swift", ignoreCase = true)
}
