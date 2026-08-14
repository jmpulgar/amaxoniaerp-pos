@file:Suppress("MagicNumber", "UnusedPrivateMember", "LongMethod")

package com.amaxonia.pos.ui.payment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.ui.theme.PosTheme

/**
 * Visual regression surface for the Cobro screen.
 *
 * The variants exercise every layout break-point and the most relevant content combinations so we
 * can confirm — at design time — that:
 *  - the `Contado` / `Crédito` badge is gone from the header;
 *  - the financial breakdown (Subtotal / Descuento / Impuesto / Total) is always visible;
 *  - `Cobrar $ XX.XX` never wraps nor overflows on any width, including POS-small (320dp).
 *
 * Render via Android Studio's "Split"/"Design" pane or `./gradlew :app:assembleAmaxoniaDebug`.
 */
@Preview(name = "Header · total corto · 320dp POS", showBackground = true, widthDp = 320)
@Preview(name = "Header · total corto · 600dp", showBackground = true, widthDp = 600)
@Composable
private fun PaymentHeaderShortTotalPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(320.dp)) {
            HeaderPreviewContent(state = previewState(totalAmount = 12.5, method = PaymentMethod.CASH))
        }
    }
}

@Preview(name = "Header · total largo sin wrap · 320dp POS", showBackground = true, widthDp = 320)
@Composable
private fun PaymentHeaderLongTotalPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(320.dp)) {
            HeaderPreviewContent(
                state = previewState(totalAmount = 9_876_543.21, method = PaymentMethod.CASH),
            )
        }
    }
}

@Preview(name = "Selector · efectivo seleccionado", showBackground = true, widthDp = 360)
@Composable
private fun PaymentSelectorCashPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(360.dp)) {
            SelectorPreviewContent(selected = PaymentMethod.CASH)
        }
    }
}

@Preview(name = "Selector · tarjeta/otro seleccionado", showBackground = true, widthDp = 360)
@Composable
private fun PaymentSelectorNonCashPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(360.dp)) {
            SelectorPreviewContent(selected = PaymentMethod.NON_CASH)
        }
    }
}

@Preview(name = "CTA · efectivo · total corto · 320dp", showBackground = true, widthDp = 320)
@Composable
private fun PrimaryCtaCashShortPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(320.dp)) {
            CtaPreviewContent(state = previewState(totalAmount = 65.0, method = PaymentMethod.CASH))
        }
    }
}

@Preview(name = "CTA · efectivo · total largo · 320dp sin wrap", showBackground = true, widthDp = 320)
@Composable
private fun PrimaryCtaCashLongPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(320.dp)) {
            CtaPreviewContent(
                state = previewState(totalAmount = 123_456.78, method = PaymentMethod.CASH),
            )
        }
    }
}

@Preview(name = "Tarjeta / Otro · multi-forma · 600dp", showBackground = true, widthDp = 600)
@Composable
private fun NonCashMultiMethodPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(600.dp)) {
            NonCashPreviewContent(
                state =
                    previewState(
                        totalAmount = 250.0,
                        method = PaymentMethod.NON_CASH,
                        methods =
                            listOf(
                                FormaPago(
                                    1,
                                    siglas = "TDC",
                                    descripcion = "Visa",
                                    activo = 1,
                                    pos = 1,
                                    grupo = 1,
                                    orden = 1,
                                    tipoMoneda = "BASE",
                                ),
                                FormaPago(
                                    2,
                                    siglas = "TDD",
                                    descripcion = "Mastercard Débito",
                                    activo = 1,
                                    pos = 1,
                                    grupo = 1,
                                    orden = 2,
                                    tipoMoneda = "BASE",
                                ),
                                FormaPago(
                                    3,
                                    siglas = "TR",
                                    descripcion = "Transferencia ACH",
                                    activo = 1,
                                    pos = 1,
                                    grupo = 1,
                                    orden = 3,
                                    tipoMoneda = "BASE",
                                ),
                                FormaPago(
                                    4,
                                    siglas = "ZELLE",
                                    descripcion = "Zelle",
                                    activo = 1,
                                    pos = 1,
                                    grupo = 1,
                                    orden = 4,
                                    tipoMoneda = "BASE",
                                ),
                            ),
                    ),
            )
        }
    }
}

@Preview(name = "Tarjeta / Otro · multi-forma · 900dp tablet", showBackground = true, widthDp = 900)
@Composable
private fun NonCashMultiMethodTabletPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(900.dp)) {
            NonCashPreviewContent(
                state =
                    previewState(
                        totalAmount = 8_750.50,
                        method = PaymentMethod.NON_CASH,
                        methods =
                            listOf(
                                FormaPago(
                                    1,
                                    siglas = "TDC",
                                    descripcion = "Visa",
                                    activo = 1,
                                    pos = 1,
                                    grupo = 1,
                                    orden = 1,
                                    tipoMoneda = "BASE",
                                ),
                                FormaPago(
                                    2,
                                    siglas = "TDD",
                                    descripcion = "Mastercard Débito",
                                    activo = 1,
                                    pos = 1,
                                    grupo = 1,
                                    orden = 2,
                                    tipoMoneda = "BASE",
                                ),
                                FormaPago(
                                    3,
                                    siglas = "TR",
                                    descripcion = "Transferencia ACH",
                                    activo = 1,
                                    pos = 1,
                                    grupo = 1,
                                    orden = 3,
                                    tipoMoneda = "BASE",
                                ),
                                FormaPago(
                                    4,
                                    siglas = "ZELLE",
                                    descripcion = "Zelle",
                                    activo = 1,
                                    pos = 1,
                                    grupo = 1,
                                    orden = 4,
                                    tipoMoneda = "BASE",
                                ),
                            ),
                    ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview-only helpers — they exercise the production composables directly so a
// designer sees exactly what shipping code renders.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeaderPreviewContent(state: PaymentState) {
    Column {
        PaymentHeader(state = state, modifier = Modifier.padding(16.dp))
        FinancialBreakdown(
            snapshot = state.financialSnapshot,
            totalFallback = state.totalAmountMoney,
            taxLabel = state.effectiveTaxLabel,
            isMultiCurrency = false,
            tasa = 0.0,
        )
    }
}

@Composable
private fun SelectorPreviewContent(selected: PaymentMethod) {
    Column(modifier = Modifier.padding(16.dp)) {
        PaymentMethodSelectorRow(
            selectedMethod = selected,
            cashEnabled = true,
            nonCashEnabled = true,
            onSelect = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CtaPreviewContent(state: PaymentState) {
    Column(modifier = Modifier.padding(16.dp)) {
        PrimaryCtaButton(
            state = state,
            warningScale = 1f,
            isInsufficient = false,
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NonCashPreviewContent(state: PaymentState) {
    Column(modifier = Modifier.padding(16.dp)) {
        state.formasPagoTarjetaOtro.forEach { forma ->
            NonCashRow(
                forma = forma,
                value = state.nonCashAmountsInput[forma.idFormaPago].orEmpty(),
                pendingAmount = state.nonCashPendingText,
                canUseCredit = state.canUseCredit,
                onValueChange = {},
                onUseExactAmount = {},
            )
        }
        PrimaryCtaButton(
            state = state,
            warningScale = 1f,
            isInsufficient = false,
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun previewState(
    totalAmount: Double,
    method: PaymentMethod,
    methods: List<FormaPago> =
        listOf(
            FormaPago(1, siglas = "CASH", descripcion = "Efectivo", activo = 1, pos = 1, grupo = 1, orden = 1, tipoMoneda = "BASE"),
        ),
): PaymentState {
    val tax = (totalAmount - totalAmount / 1.16)
    val snapshot =
        SaleFinancialSnapshot(
            subtotalGross = totalAmount / 1.16,
            itemDiscounts = 0.0,
            subtotalNet = totalAmount / 1.16,
            tax = tax,
            total = totalAmount,
        )
    return PaymentState(
        totalAmount = totalAmount,
        selectedMethod = method,
        formasPago = methods,
        financialSnapshot = snapshot,
        taxLabel = "IVA",
    )
}
