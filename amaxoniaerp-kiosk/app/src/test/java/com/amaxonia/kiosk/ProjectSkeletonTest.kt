package com.amaxonia.kiosk

import com.amaxonia.kiosk.ui.theme.FlowGradient
import com.amaxonia.kiosk.ui.theme.FlowIndigo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProjectSkeletonTest {
    @Test
    fun testBuildConfigFieldsExist() {
        assertNotNull(BuildConfig.APPLICATION_ID)
        assertNotNull(BuildConfig.BUILD_TYPE)
        assertNotNull(BuildConfig.FLAVOR)
        assertNotNull(BuildConfig.DEFAULT_SERVER_URL)
    }

    @Test
    fun testThemeColorDefinition() {
        // The brand gradient starts on the Flow ERP primary indigo.
        assertEquals(FlowIndigo, FlowGradient.first())
    }
}
