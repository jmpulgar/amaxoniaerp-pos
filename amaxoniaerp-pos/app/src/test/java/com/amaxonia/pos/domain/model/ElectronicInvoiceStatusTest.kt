package com.amaxonia.pos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Q3: criterio FE unificado con el Web. EXITOSA exige CUFE (el backend PA
 * expone `codigoFiscal` = cufe); sin CUFE la factura es PENDIENTE o FALLIDA
 * y DEBE seguir disponible para reenvío aunque tenga número fiscal asignado.
 */
class ElectronicInvoiceStatusTest {
    @Test
    fun `con CUFE es exitosa`() {
        val estado =
            resolveElectronicInvoiceStatus(
                estatus = "Pagado",
                transactionStatus = TransactionStatus.PAID,
                codigoFiscal = "CUFE-ABC123",
            )

        assertEquals(ElectronicInvoiceStatus.SUCCESS, estado)
    }

    @Test
    fun `numero fiscal sin CUFE NO es exitosa queda pendiente para reenvio`() {
        val estado =
            resolveElectronicInvoiceStatus(
                estatus = "Pagado",
                transactionStatus = TransactionStatus.PAID,
                codigoFiscal = "",
            )

        assertEquals(ElectronicInvoiceStatus.PENDING, estado)
    }

    @Test
    fun `estatus Fallida sin CUFE es fallida`() {
        val estado =
            resolveElectronicInvoiceStatus(
                estatus = "Fallida",
                transactionStatus = TransactionStatus.PAID,
                codigoFiscal = "",
            )

        assertEquals(ElectronicInvoiceStatus.FAILED, estado)
    }

    @Test
    fun `factura anulada sin CUFE no muestra badge`() {
        val estado =
            resolveElectronicInvoiceStatus(
                estatus = "Anulada",
                transactionStatus = TransactionStatus.CANCELLED,
                codigoFiscal = "",
            )

        assertEquals(ElectronicInvoiceStatus.NONE, estado)
    }

    @Test
    fun `fecha DGI sola no es exitosa`() {
        val estado =
            resolveElectronicInvoiceStatus(
                estatus = "Pagado",
                transactionStatus = TransactionStatus.PENDING,
                codigoFiscal = "",
            )

        assertEquals(ElectronicInvoiceStatus.NONE, estado)
    }
}
