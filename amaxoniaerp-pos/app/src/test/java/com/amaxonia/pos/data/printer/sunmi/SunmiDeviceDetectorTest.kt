package com.amaxonia.pos.data.printer.sunmi

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SunmiDeviceDetectorTest {

    @Test
    fun returnsTrueWhenManufacturerIsSunmi() {
        ShadowBuild.setManufacturer("SUNMI")
        assertTrue(SunmiDeviceDetector.isSunmiDevice())
    }

    @Test
    fun returnsTrueWhenManufacturerIsSunmiLowerCase() {
        ShadowBuild.setManufacturer("sunmi")
        assertTrue(SunmiDeviceDetector.isSunmiDevice())
    }

    @Test
    fun returnsFalseWhenManufacturerIsGeneric() {
        ShadowBuild.setManufacturer("Samsung")
        ShadowBuild.setBrand("Samsung")
        assertFalse(SunmiDeviceDetector.isSunmiDevice())
    }
}
