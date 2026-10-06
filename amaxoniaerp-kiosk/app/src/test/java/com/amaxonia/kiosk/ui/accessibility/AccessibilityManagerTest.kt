package com.amaxonia.kiosk.ui.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccessibilityManagerTest {
    private lateinit var manager: AccessibilityManager

    @Before
    fun setUp() {
        manager = AccessibilityManager()
    }

    @Test
    fun initialState_hasDefaultAccessibilityValues() {
        val state = manager.state.value
        assertFalse(state.isAccessibleMode)
        assertFalse(state.isHighContrast)
        assertEquals(KioskLanguage.SPANISH, state.language)
    }

    @Test
    fun toggleAccessibleMode_switchesState() {
        manager.toggleAccessibleMode()
        assertTrue(manager.state.value.isAccessibleMode)

        manager.toggleAccessibleMode()
        assertFalse(manager.state.value.isAccessibleMode)
    }

    @Test
    fun setAccessibleMode_updatesStateExplicitly() {
        manager.setAccessibleMode(true)
        assertTrue(manager.state.value.isAccessibleMode)

        manager.setAccessibleMode(false)
        assertFalse(manager.state.value.isAccessibleMode)
    }

    @Test
    fun toggleHighContrast_switchesState() {
        manager.toggleHighContrast()
        assertTrue(manager.state.value.isHighContrast)

        manager.toggleHighContrast()
        assertFalse(manager.state.value.isHighContrast)
    }

    @Test
    fun toggleLanguage_switchesBetweenSpanishAndEnglish() {
        assertEquals(KioskLanguage.SPANISH, manager.state.value.language)

        manager.toggleLanguage()
        assertEquals(KioskLanguage.ENGLISH, manager.state.value.language)

        manager.toggleLanguage()
        assertEquals(KioskLanguage.SPANISH, manager.state.value.language)
    }

    @Test
    fun reset_restoresInitialState() {
        manager.setAccessibleMode(true)
        manager.setHighContrast(true)
        manager.setLanguage(KioskLanguage.ENGLISH)

        manager.reset()

        val state = manager.state.value
        assertFalse(state.isAccessibleMode)
        assertFalse(state.isHighContrast)
        assertEquals(KioskLanguage.SPANISH, state.language)
    }
}
