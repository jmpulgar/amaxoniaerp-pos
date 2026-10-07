package com.amaxonia.kiosk.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KioskCanvasTest {
    private fun scale(
        w: Int,
        h: Int,
    ) = KioskCanvas.scaleFor(w, h)!!

    @Test
    fun `reference K2 portrait window maps one design dp to one pixel`() {
        assertEquals(1f, scale(1080, 1920), 0.0001f)
    }

    @Test
    fun `scale depends on pixels only, not on the reported density`() {
        // Same 1080x1920 px panel whether Android calls it mdpi, hdpi or xhdpi.
        assertEquals(scale(1080, 1920), scale(1080, 1920), 0f)
        assertEquals(0.5f, scale(540, 960), 0.0001f)
        assertEquals(2f, scale(2160, 3840), 0.0001f)
    }

    @Test
    fun `portrait uses the tighter axis of the 1080x1920 canvas`() {
        // 800x1280: height is the tighter axis, so the canvas becomes 1200 x 1920 dp.
        assertEquals(1280f / 1920f, scale(800, 1280), 0.0001f)
        // 1080x2340: width is the tighter axis, the canvas is 1080 x 2340 dp.
        assertEquals(1f, scale(1080, 2340), 0.0001f)
    }

    @Test
    fun `landscape windows use the 1920x1080 canvas`() {
        assertEquals(1f, scale(1920, 1080), 0.0001f)
        // 1366x768 is a hair wider than 16:9: height is tighter, canvas ~1921 x 1080 dp.
        assertEquals(768f / 1080f, scale(1366, 768), 0.0001f)
        // 4:3 landscape: width is tighter, canvas becomes 1920 x 1440 dp.
        assertEquals(1024f / 1920f, scale(1024, 768), 0.0001f)
    }

    @Test
    fun `scale is clamped to a sane range`() {
        assertEquals(KioskCanvas.MIN_SCALE, scale(100, 200), 0f)
        assertEquals(KioskCanvas.MAX_SCALE, scale(10_000, 20_000), 0f)
    }

    @Test
    fun `unmeasured window has no scale`() {
        assertNull(KioskCanvas.scaleFor(0, 1920))
        assertNull(KioskCanvas.scaleFor(1080, 0))
    }
}
