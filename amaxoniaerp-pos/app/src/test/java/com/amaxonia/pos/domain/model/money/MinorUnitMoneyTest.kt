package com.amaxonia.pos.domain.model.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

/**
 * Boundary-conversion regression suite for [MinorUnitMoney].
 *
 * The defining invariant: any value whose residue against its HALF_EVEN-rounded
 * scale-2 form is strictly less than 0.5 minor units (0.005 at scale=2) MUST
 * convert losslessly — no overflow, no throw — because banker's rounding is
 * idempotent in that interval. Values at or beyond half a minor unit are
 * genuinely ambiguous and MUST throw [MoneyOverflowException].
 *
 * Background: a production crash (auditoria MONEY-001, 2026-07-22) was caused
 * by an over-strict `discarded.signum() != 0` check that rejected IEEE-754
 * noise like 6.8999999999999995. The fix hard-codes the provably-lossless
 * band. These tests pin the band.
 */
class MinorUnitMoneyTest {
    @Test
    fun `IEEE-754 noise below half-unit rounds losslessly`() {
        // The original production crash: 6.90 accumulated as 6.8999999999999995.
        // Residue 5e-16, far below 0.005 — must produce 690 without throwing.
        assertEquals(690L, MinorUnitMoney.fromDoubleAsMinor(6.8999999999999995))
        assertEquals(690L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("6.8999999999999995")))
    }

    @Test
    fun `classic 0_1 + 0_2 residue converts correctly`() {
        // 0.1 + 0.2 in IEEE-754 = 0.30000000000000004 — residue 4e-17.
        assertEquals(30L, MinorUnitMoney.fromDoubleAsMinor(0.1 + 0.2))
        assertEquals(30L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal.valueOf(0.1 + 0.2)))
    }

    @Test
    fun `clean scale-2 values pass through`() {
        assertEquals(0L, MinorUnitMoney.fromDoubleAsMinor(0.0))
        assertEquals(0L, MinorUnitMoney.fromDoubleAsMinor(null))
        assertEquals(0L, MinorUnitMoney.fromDoubleAsMinor(Double.NaN))
        assertEquals(0L, MinorUnitMoney.fromDoubleAsMinor(Double.POSITIVE_INFINITY))
        assertEquals(690L, MinorUnitMoney.fromDoubleAsMinor(6.90))
        assertEquals(690L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("6.90")))
        assertEquals(100L, MinorUnitMoney.fromDoubleAsMinor(1.00))
        assertEquals(99999999999L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("999999999.99")))
    }

    @Test
    fun `small sub-cent residues below threshold are tolerated`() {
        // 0.0009 residue: rounds to 0.00 (HALF_EVEN) but the value is below
        // 0.005 so we tolerate it.
        assertEquals(0L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("0.0049")))
        assertEquals(1L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("0.0099")))
        // 0.001 and 0.009 round to 0.00 and 0.01 respectively.
        assertEquals(0L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("0.001")))
        assertEquals(1L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("0.009")))
    }

    @Test
    fun `negative values mirror positive behaviour`() {
        assertEquals(-690L, MinorUnitMoney.fromDoubleAsMinor(-6.8999999999999995))
        assertEquals(-30L, MinorUnitMoney.fromDoubleAsMinor(-(0.1 + 0.2)))
        assertEquals(0L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("-0.004")))
    }

    @Test
    fun `material residue at exactly half-unit throws`() {
        // The threshold of 0.5 minor units (0.005 at scale=2) is the boundary
        // where HALF_EVEN must make a choice — the caller must authorize it
        // explicitly. By construction of HALF_EVEN rounding at scale N,
        // |value - rounded| is ALWAYS in [0, 0.5*10^-N], so this boundary is
        // the only place the materiality guard can fire. The guard exists to
        // flag the midpoint as a decision the caller should make explicitly
        // (e.g. via a prior setScale at a higher scale with a documented
        // mode), not to catch larger residues — those cannot exist.
        //
        // 1.005 → HALF_EVEN rounds to 1.00 (toward even). residue 0.005.
        assertThrows(MoneyOverflowException::class.java) {
            MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("1.005"))
        }
        // 0.025 → HALF_EVEN rounds to 0.02 (toward even). residue 0.005.
        assertThrows(MoneyOverflowException::class.java) {
            MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("0.025"))
        }
        // 1.235 → HALF_EVEN rounds to 1.24 (toward even). residue 0.005.
        assertThrows(MoneyOverflowException::class.java) {
            MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("1.235"))
        }
        // 6.895 → HALF_EVEN rounds to 6.90. residue 0.005.
        assertThrows(MoneyOverflowException::class.java) {
            MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("6.895"))
        }
        // 6.885 → HALF_EVEN rounds to 6.88 (toward even). residue 0.005.
        assertThrows(MoneyOverflowException::class.java) {
            MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("6.885"))
        }
    }

    @Test
    fun `sub-half-unit residues are silently absorbed without throwing`() {
        // Anything strictly below 0.005 residue is provably idempotent under
        // HALF_EVEN. These must NOT throw even though they have 3+ decimals.
        //   6.899 → rounds to 6.90, residue 0.001 < 0.005.
        //   7.001 → rounds to 7.00, residue 0.001 < 0.005.
        //   1.999 → rounds to 2.00, residue 0.001 < 0.005.
        //   0.004 → rounds to 0.00, residue 0.004 < 0.005.
        //   6.897 → rounds to 6.90, residue 0.003 < 0.005.
        //   6.894 → rounds to 6.89, residue 0.004 < 0.005.
        assertEquals(690L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("6.899")))
        assertEquals(700L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("7.001")))
        assertEquals(200L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("1.999")))
        assertEquals(0L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("0.004")))
        assertEquals(690L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("6.897")))
        assertEquals(689L, MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("6.894")))
    }

    @Test
    fun `round-trips losslessly through toMoney for scale-2 inputs`() {
        val cases = listOf(0L, 1L, 99L, 100L, 690L, 10000L, Long.MAX_VALUE / 10)
        cases.forEach { minor ->
            val money = MinorUnitMoney.toMoney(minor)
            assertEquals(minor, MinorUnitMoney.toMinor(money))
        }
    }

    @Test
    fun `DOWN rounding mode refuses material residue for off-midpoint values`() {
        // With RoundingMode.DOWN, the residue can exceed the HALF_EVEN band
        // because DOWN truncates toward zero regardless of magnitude. The
        // guard fires because the discarded fraction is material (>= 0.005).
        // 6.899 with DOWN → 6.89, residue 0.009 > 0.005 → throws.
        assertThrows(MoneyOverflowException::class.java) {
            MinorUnitMoney.fromBigDecimalAsMinor(
                BigDecimal("6.899"),
                roundingMode = java.math.RoundingMode.DOWN,
            )
        }
    }

    @Test
    fun `int64 overflow throws`() {
        // Long.MAX_VALUE = 9_223_372_036_854_775_807. At scale=2, that holds
        // up to ~92_233_720_368_547_758.07 in base units. Anything beyond must
        // throw to prevent truncation in setScale/movePointRight.
        assertThrows(MoneyOverflowException::class.java) {
            MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("100000000000000000"))
        }
        assertThrows(MoneyOverflowException::class.java) {
            MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("-100000000000000000"))
        }
        // Boundary just inside Int64 range — still lossless.
        assertEquals(
            9_223_372_036_854_775_800L,
            MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("92233720368547758.00")),
        )
    }

    @Test
    fun `custom rounding mode HALF_UP still respects materiality threshold`() {
        // The threshold is independent of mode — it gates on raw residue, not
        // on rounding direction.
        assertEquals(
            690L,
            MinorUnitMoney.fromBigDecimalAsMinor(BigDecimal("6.8999999999999995"), roundingMode = java.math.RoundingMode.HALF_UP),
        )
    }
}
