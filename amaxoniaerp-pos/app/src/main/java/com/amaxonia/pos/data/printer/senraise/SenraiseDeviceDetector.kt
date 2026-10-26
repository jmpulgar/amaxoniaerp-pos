package com.amaxonia.pos.data.printer.senraise

import android.os.Build

object SenraiseDeviceDetector {
    /**
     * Determines whether the current device is a Senraise terminal with an integrated thermal printer
     * (such as the H10S).
     */
    fun isSenraiseDevice(): Boolean =
        Build.MANUFACTURER.contains("senraise", ignoreCase = true) ||
            Build.BRAND.contains("senraise", ignoreCase = true) ||
            Build.MODEL.contains("H10", ignoreCase = true) ||
            Build.DEVICE.contains("H10", ignoreCase = true) ||
            Build.PRODUCT.contains("H10", ignoreCase = true) ||
            Build.DISPLAY.contains("H10S", ignoreCase = true)
}
