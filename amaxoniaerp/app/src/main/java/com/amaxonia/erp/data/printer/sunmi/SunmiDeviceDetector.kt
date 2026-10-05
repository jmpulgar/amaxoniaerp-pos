package com.amaxonia.erp.data.printer.sunmi

import android.os.Build

object SunmiDeviceDetector {
    /**
     * Determines whether the current device is a Sunmi terminal with an integrated thermal printer.
     */
    fun isSunmiDevice(): Boolean =
        Build.MANUFACTURER.equals("SUNMI", ignoreCase = true) ||
            Build.BRAND.equals("SUNMI", ignoreCase = true)
}
