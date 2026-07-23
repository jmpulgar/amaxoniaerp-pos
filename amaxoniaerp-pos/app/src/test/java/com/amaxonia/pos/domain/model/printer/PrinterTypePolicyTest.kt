package com.amaxonia.pos.domain.model.printer

import com.amaxonia.pos.domain.model.SchemaType
import com.amaxonia.pos.domain.model.ServerCountry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterTypePolicyTest {
    private val panama = ServerCountry("PA", "Panamá", "https://example.com", "", SchemaType.TYPE_A)
    private val venezuela = ServerCountry("VE", "Venezuela", "https://example.com", "", SchemaType.TYPE_B)

    @Test
    fun sunmiAppearsForPanamaAndVenezuela() {
        assertTrue(PrinterType.SUNMI_V2 in PrinterTypePolicy.availablePrinterTypes(panama))
        assertTrue(PrinterType.SUNMI_V2 in PrinterTypePolicy.availablePrinterTypes(venezuela))
    }

    @Test
    fun sunmiCanBeSavedForVenezuela() {
        PrinterTypePolicy.validate(venezuela, PrinterType.SUNMI_V2)
    }

    @Test
    fun sunmiIsPreservedForVenezuela() {
        assertEquals(PrinterType.SUNMI_V2, PrinterTypePolicy.coerce(venezuela, PrinterType.SUNMI_V2))
    }
}
