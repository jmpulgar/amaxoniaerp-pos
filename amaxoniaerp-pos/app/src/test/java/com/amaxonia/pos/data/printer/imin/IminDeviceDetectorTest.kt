package com.amaxonia.pos.data.printer.imin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IminDeviceDetectorTest {
    @Test
    fun returnsTrueWhenManufacturerIsImin() {
        ShadowBuild.setManufacturer("iMin")
        assertTrue(IminDeviceDetector.isIminDevice())
    }

    @Test
    fun returnsTrueWhenManufacturerIsIminLowerCase() {
        ShadowBuild.setManufacturer("imin")
        assertTrue(IminDeviceDetector.isIminDevice())
    }

    @Test
    fun returnsTrueWhenBrandIsImin() {
        ShadowBuild.setManufacturer("Unknown")
        ShadowBuild.setBrand("imin")
        assertTrue(IminDeviceDetector.isIminDevice())
    }

    @Test
    fun returnsTrueWhenModelIsSwift2() {
        ShadowBuild.setManufacturer("Generic")
        ShadowBuild.setBrand("Generic")
        ShadowBuild.setModel("Swift 2")
        assertTrue(IminDeviceDetector.isIminDevice())
    }

    @Test
    fun returnsTrueWhenModelIsI23M01() {
        ShadowBuild.setManufacturer("Generic")
        ShadowBuild.setBrand("Generic")
        ShadowBuild.setModel("I23M01")
        assertTrue(IminDeviceDetector.isIminDevice())
    }

    @Test
    fun returnsFalseWhenDeviceIsGeneric() {
        ShadowBuild.setManufacturer("Samsung")
        ShadowBuild.setBrand("Samsung")
        ShadowBuild.setModel("SM-G998B")
        assertFalse(IminDeviceDetector.isIminDevice())
    }
}
