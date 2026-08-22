package com.amaxoniaerp.features.sales.data

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TASK-101 — Caracterización del boundary monetario de ventas
 * (`ProcessSaleMonetary`): el wire llega como `Double` y se convierte a
 * `BigDecimal` escala 2 HALF_UP; la aplicación de tasa multi-moneda
 * (`toBase`) multiplica en BigDecimal y re-redondea a escala 2 HALF_UP.
 *
 * Estos tests fijan los resultados numéricos ACTUALES (cero cambio): los
 * casos cubren centavos límite (0.01), valores con tercio decimal (2.305,
 * 10.005), montos grandes y la tasa de cambio VE.
 */
class ProcessSaleMonetaryRoundingCharacterizationTest {
    private val tasaVes = BigDecimal("36.50000000")
    private val sinMultiMoneda =
        MonetaryContext(
            countryCode = "PA",
            multiMoneda = "NO",
            tasa = BigDecimal.ONE,
            idTasa = 0,
            monedaBase = 1,
            abrMonedaBase = "USD",
            monedaSecundaria = 1,
            abrMonedaSecundaria = "USD",
            totalRef = 0.0,
            validarStock = "NO",
            defaultTaxRate = 7.0,
            defaultFormaPagoId = 1,
            diasVencimiento = 8,
        )
    private val multiMonedaVes =
        sinMultiMoneda.copy(
            countryCode = "VE",
            multiMoneda = "SI",
            tasa = tasaVes,
            abrMonedaSecundaria = "VES",
        )

    @Test
    fun `toMoney redondea HALF_UP a escala 2`() {
        assertEquals(BigDecimal("0.01"), 0.005.toMoney(), "medio centavo sube (HALF_UP)")
        assertEquals(BigDecimal("2.31"), 2.305.toMoney(), "tercio decimal sube")
        assertEquals(BigDecimal("9.99"), 9.99.toMoney(), "centavos exactos no se alteran")
        assertEquals(BigDecimal("10.01"), 10.005.toMoney(), "frontera 10.005 sube")
        assertEquals(BigDecimal("1234567.89"), 1234567.891.toMoney(), "montos grandes truncables a escala 2")
        assertEquals(BigDecimal("1070000.00"), 1070000.0.toMoney())
    }

    @Test
    fun `toBase sin multimoneda normaliza a escala 2`() {
        assertEquals(BigDecimal("10.01"), sinMultiMoneda.toBase(10.005))
        assertEquals(BigDecimal("9.99"), sinMultiMoneda.toBase(9.99.toMoney()))
    }

    @Test
    fun `toBase con tasa aplica multiplicacion BigDecimal y HALF_UP`() {
        // 0.01 × 36.5 = 0.365 → 0.37 (HALF_UP, no banker's).
        assertEquals(BigDecimal("0.37"), multiMonedaVes.toBase(0.01))
        // 10.01 × 36.5 = 365.365 → 365.37.
        assertEquals(BigDecimal("365.37"), multiMonedaVes.toBase(10.01))
        // 2.30 × 36.5 = 83.95 exacto.
        assertEquals(BigDecimal("83.95"), multiMonedaVes.toBase(2.30))
        // Monto grande: 1234567.891 × 36.5 = 45061728.0215 → 45061728.02.
        assertEquals(BigDecimal("45061728.02"), multiMonedaVes.toBase(1234567.891))
    }

    @Test
    fun `toBase BigDecimal normaliza antes de aplicar tasa`() {
        // 9.985 se normaliza primero a 9.99 y luego × 36.5 = 364.635 → 364.64.
        assertEquals(BigDecimal("364.64"), multiMonedaVes.toBase(BigDecimal("9.985")))
        // Sin normalización previa el producto daría otra escala: se fija el orden actual.
        assertEquals(BigDecimal("365.37"), multiMonedaVes.toBase(BigDecimal("10.01")))
    }
}
