package com.amaxoniaerp.features.cobranza.domain

import com.amaxoniaerp.core.database.formatCorrelativo
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CobranzaRulesTest {
    private fun bd(value: String) = BigDecimal(value)

    private fun request(
        facturas: List<Double>,
        pagos: List<Double>,
    ) = CreateCobranzaRequest(
        idCobranza = "6f1c2c1e-0000-4000-8000-000000000001",
        idCliente = "cli-1",
        idCaja = "caja-1",
        idSucursal = 1,
        facturas = facturas.mapIndexed { i, monto -> CobranzaFacturaInput("fact-$i", monto) },
        pagos = pagos.mapIndexed { i, monto -> CobranzaPagoInput(idFormaPago = i + 1, monto = monto) },
    )

    @Test
    fun `reparte varias formas de pago sobre varias facturas en orden y al centavo`() {
        val allocations = CobranzaRules.allocate(listOf(bd("10.00"), bd("5.50")), listOf(bd("7.25"), bd("8.25")))

        assertEquals(
            listOf(
                PaymentAllocation(0, 0, bd("7.25")),
                PaymentAllocation(0, 1, bd("2.75")),
                PaymentAllocation(1, 1, bd("5.50")),
            ),
            allocations,
        )
        // Cada factura recibe exactamente su monto y cada pago se consume completo.
        assertEquals(bd("10.00"), allocations.filter { it.facturaIndex == 0 }.sumOf { it.monto })
        assertEquals(bd("8.25"), allocations.filter { it.pagoIndex == 1 }.sumOf { it.monto })
    }

    @Test
    fun `un unico pago cubre varias facturas`() {
        val allocations = CobranzaRules.allocate(listOf(bd("1.10"), bd("2.20"), bd("3.30")), listOf(bd("6.60")))
        assertEquals(listOf(bd("1.10"), bd("2.20"), bd("3.30")), allocations.map { it.monto })
        assertTrue(allocations.all { it.pagoIndex == 0 })
    }

    @Test
    fun `rechaza cuando pagos y facturas no suman lo mismo`() {
        val error = assertFailsWith<CobranzaValidationException> { CobranzaRules.validate(request(listOf(10.0), listOf(9.99))) }
        assertTrue(error.message.contains("debe ser igual"))
    }

    @Test
    fun `rechaza facturas repetidas y montos no positivos`() {
        val repetida =
            request(listOf(5.0, 5.0), listOf(10.0)).let {
                it.copy(facturas = it.facturas.map { f -> f.copy(idFactura = "misma") })
            }
        assertFailsWith<CobranzaValidationException> { CobranzaRules.validate(repetida) }
        assertFailsWith<CobranzaValidationException> { CobranzaRules.validate(request(listOf(0.0), listOf(0.0))) }
        assertFailsWith<CobranzaValidationException> { CobranzaRules.validate(request(listOf(5.0), emptyList())) }
    }

    @Test
    fun `acepta un cobro valido evitando residuos de Double`() {
        CobranzaRules.validate(request(listOf(0.1, 0.2), listOf(0.3)))
    }

    @Test
    fun `las formas de pago de credito no sirven para cobrar`() {
        assertTrue(CobranzaRules.isCreditSigla(" cxc "))
        assertTrue(CobranzaRules.isCreditSigla("CREDITO"))
        assertTrue(!CobranzaRules.isCreditSigla("EF"))
        assertTrue(!CobranzaRules.isCreditSigla(null))
    }

    @Test
    fun `formatea correlativos como el web`() {
        assertEquals("000025", formatCorrelativo(25, "000000"))
        assertEquals("0000-00000123", formatCorrelativo(123, "0000-00000000"))
        assertEquals("1234567", formatCorrelativo(1234567, "00000"))
    }
}
