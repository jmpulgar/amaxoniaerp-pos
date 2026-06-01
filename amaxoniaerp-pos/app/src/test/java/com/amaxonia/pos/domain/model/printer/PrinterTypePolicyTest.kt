package com.amaxonia.pos.domain.model.printer

import com.amaxonia.pos.domain.model.SchemaType
import com.amaxonia.pos.domain.model.ServerCountry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterTypePolicyTest {
    private val panama = ServerCountry("PA", "Panamá", "https://example.com", "", SchemaType.TYPE_A)
    private val venezuela = ServerCountry("VE", "Venezuela", "https://example.com", "", SchemaType.TYPE_B)

    @Test
    fun sunmiAppearsOnlyForPanama() {
        assertTrue(PrinterType.SUNMI_V2 in PrinterTypePolicy.availablePrinterTypes(panama))
        assertFalse(PrinterType.SUNMI_V2 in PrinterTypePolicy.availablePrinterTypes(venezuela))
    }

    @Test(expected = InvalidPosConfigurationException::class)
    fun sunmiCannotBeSavedOutsidePanama() {
        PrinterTypePolicy.validate(venezuela, PrinterType.SUNMI_V2)
    }

    @Test
    fun invalidSunmiIsCoercedToNoneWhenCountryChanges() {
        assertEquals(PrinterType.NONE, PrinterTypePolicy.coerce(venezuela, PrinterType.SUNMI_V2))
    }
}
