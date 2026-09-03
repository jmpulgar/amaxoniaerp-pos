package com.amaxonia.pos.ui.history

import com.amaxonia.pos.domain.model.ElectronicInvoiceStatus
import com.amaxonia.pos.domain.model.Transaction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Q3: el botón de reenvío FE sólo se muestra para facturas sincronizadas
 * SIN CUFE (pendiente o fallida) — paridad con el filtro del Web.
 */
class FacturaDetalleSheetGatingTest {
    private fun transaction(
        id: String = "F-1",
        electronicStatus: ElectronicInvoiceStatus,
    ) = Transaction(
        id = id,
        invoiceNumber = "001-01182",
        time = "10:00",
        amount = 107.0,
        electronicStatus = electronicStatus,
        dateHeader = "03/09/2026",
    )

    @Test
    fun `factura pendiente sin CUFE es reenviable`() {
        assertTrue(transaction(electronicStatus = ElectronicInvoiceStatus.PENDING).esReenviable())
    }

    @Test
    fun `factura fallida es reenviable`() {
        assertTrue(transaction(electronicStatus = ElectronicInvoiceStatus.FAILED).esReenviable())
    }

    @Test
    fun `factura exitosa con CUFE NO es reenviable`() {
        assertFalse(transaction(electronicStatus = ElectronicInvoiceStatus.SUCCESS).esReenviable())
    }

    @Test
    fun `factura sin estado FE no muestra la accion`() {
        assertFalse(transaction(electronicStatus = ElectronicInvoiceStatus.NONE).esReenviable())
    }

    @Test
    fun `factura offline no sincronizada no es reenviable`() {
        assertFalse(transaction(id = "OFF-local-1", electronicStatus = ElectronicInvoiceStatus.PENDING).esReenviable())
    }

    @Test
    fun `factura sin id no es reenviable`() {
        assertFalse(transaction(id = "", electronicStatus = ElectronicInvoiceStatus.PENDING).esReenviable())
    }
}
