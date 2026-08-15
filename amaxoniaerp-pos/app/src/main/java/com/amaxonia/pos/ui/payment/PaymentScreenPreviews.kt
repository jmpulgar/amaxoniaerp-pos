@file:Suppress("MagicNumber", "UnusedPrivateMember", "LongMethod", "TooManyFunctions")

package com.amaxonia.pos.ui.payment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.ui.theme.PosTheme

/**
 * Visual regression surface for the Cobro screen.
 *
 * Renders the *production* layout composables (the same ones wired inside [PaymentScreen])
 * at every target Android size — 320dp through 480dp wide, plus landscape and tablet portrait —
 * so a designer can confirm in Android Studio's "Design" pane that:
 *  - `Cobrar $XX.XX` never wraps nor overflows (AdaptiveAmountText shrinks to fit);
 *  - the financial breakdown (Subtotal / Descuento / Impuesto / Total) keeps correct proportions;
 *  - the keypad, selector and CTA keep ≥48dp targets and nothing clips on small screens;
 *  - portrait and landscape both lay out cleanly without hardcoded device breakpoints.
 *
 * Render via `./gradlew :app:assembleAmaxoniaDebug` or the IDE preview pane.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentPreviewFrame(content: @Composable BoxWithConstraintsScope.() -> Unit) {
    PosTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Cobro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Selecciona cómo pagar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
        ) { padding ->
            Surface(
                modifier = Modifier.fillMaxSize().padding(padding),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        content()
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CASH · portrait · full scaffold at every target width.
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Cash · 320×568 (smallest POS)", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun CashCompactPortrait320() =
    PaymentPreviewFrame {
        CashPaymentCompact(state = cashPreviewState(total = 65.0), onAction = {}, maxHeight = maxHeight)
    }

@Preview(name = "Cash · 360×640", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun CashCompactPortrait360() =
    PaymentPreviewFrame {
        CashPaymentCompact(state = cashPreviewState(total = 1_289.50), onAction = {}, maxHeight = maxHeight)
    }

@Preview(name = "Cash · 390×844 (Pixel)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CashCompactPortrait390() =
    PaymentPreviewFrame {
        CashPaymentCompact(state = cashPreviewState(total = 248.75), onAction = {}, maxHeight = maxHeight)
    }

@Preview(name = "Cash · 412×915 (Pixel 6)", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun CashCompactPortrait412() =
    PaymentPreviewFrame {
        CashPaymentCompact(state = cashPreviewState(total = 248.75, withDiscount = true), onAction = {}, maxHeight = maxHeight)
    }

@Preview(name = "Cash · 480×960 (large phone)", showBackground = true, widthDp = 480, heightDp = 960)
@Composable
private fun CashCompactPortrait480() =
    PaymentPreviewFrame {
        CashPaymentCompact(state = cashPreviewState(total = 248.75), onAction = {}, maxHeight = maxHeight)
    }

@Preview(name = "Cash · total enorme · sin wrap · 320×568", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun CashCompactPortraitHugeTotal() =
    PaymentPreviewFrame {
        CashPaymentCompact(state = cashPreviewState(total = 9_876_543.21), onAction = {}, maxHeight = maxHeight)
    }

// ─────────────────────────────────────────────────────────────────────────────
// NON-CASH · portrait.
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Tarjeta/Otro · 320×568", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun NonCashCompactPortrait320() =
    PaymentPreviewFrame {
        NonCashPaymentCompact(state = nonCashPreviewState(total = 250.0), onAction = {})
    }

@Preview(name = "Tarjeta/Otro · 390×844", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun NonCashCompactPortrait390() =
    PaymentPreviewFrame {
        NonCashPaymentCompact(state = nonCashPreviewState(total = 1_750.00), onAction = {})
    }

// ─────────────────────────────────────────────────────────────────────────────
// Landscape (phone + tablet) — header + summary on the left, keypad/list + CTA on the right.
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Cash · landscape · 733×340 (phone)", showBackground = true, widthDp = 733, heightDp = 340)
@Composable
private fun CashLandscapePhone() =
    PaymentPreviewFrame {
        PaymentLandscape(state = cashPreviewState(total = 248.75), onAction = {})
    }

@Preview(name = "Tarjeta/Otro · landscape · 733×340 (phone)", showBackground = true, widthDp = 733, heightDp = 340)
@Composable
private fun NonCashLandscapePhone() =
    PaymentPreviewFrame {
        PaymentLandscape(state = nonCashPreviewState(total = 250.0), onAction = {})
    }

@Preview(name = "Cash · landscape · 1280×800 (tablet)", showBackground = true, device = Devices.NEXUS_10)
@Composable
private fun CashLandscapeTablet() =
    PaymentPreviewFrame {
        PaymentLandscape(state = cashPreviewState(total = 248.75), onAction = {})
    }

// ─────────────────────────────────────────────────────────────────────────────
// Tablet portrait (≥600dp) → PaymentWide.
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Cash · tablet portrait · 600×960", showBackground = true, widthDp = 600, heightDp = 960)
@Composable
private fun CashWidePortraitTablet() =
    PaymentPreviewFrame {
        PaymentWide(state = cashPreviewState(total = 248.75), onAction = {})
    }

@Preview(name = "Tarjeta/Otro · tablet portrait · 600×960", showBackground = true, widthDp = 600, heightDp = 960)
@Composable
private fun NonCashWidePortraitTablet() =
    PaymentPreviewFrame {
        PaymentWide(state = nonCashPreviewState(total = 250.0), onAction = {})
    }

// ─────────────────────────────────────────────────────────────────────────────
// Component-level previews for edge cases (long totals, selector states, CTA).
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "Header · total corto", showBackground = true, widthDp = 320)
@Composable
private fun PaymentHeaderShortTotalPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(320.dp)) {
            Column {
                PaymentHeader(state = cashPreviewState(total = 12.5), modifier = Modifier.padding(16.dp))
                FinancialBreakdown(
                    snapshot = cashPreviewState(total = 12.5).financialSnapshot,
                    totalFallback = cashPreviewState(total = 12.5).totalAmountMoney,
                    taxLabel = "IVA",
                    isMultiCurrency = false,
                    tasa = 0.0,
                )
            }
        }
    }
}

@Preview(name = "Header · total largo sin wrap", showBackground = true, widthDp = 320)
@Composable
private fun PaymentHeaderLongTotalPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(320.dp)) {
            PaymentHeader(
                state = cashPreviewState(total = 9_876_543.21),
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(name = "Header · compacto (landscape)", showBackground = true, widthDp = 360)
@Composable
private fun PaymentHeaderCompactPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(360.dp)) {
            PaymentHeader(
                state = cashPreviewState(total = 1_234_567.89, withDiscount = true),
                compact = true,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Preview(name = "Selector · efectivo seleccionado", showBackground = true, widthDp = 360)
@Composable
private fun PaymentSelectorCashPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(360.dp)) {
            PaymentMethodSelectorRow(
                selectedMethod = PaymentMethod.CASH,
                cashEnabled = true,
                nonCashEnabled = true,
                onSelect = {},
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Preview(name = "Selector · tarjeta/otro seleccionado", showBackground = true, widthDp = 360)
@Composable
private fun PaymentSelectorNonCashPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(360.dp)) {
            PaymentMethodSelectorRow(
                selectedMethod = PaymentMethod.NON_CASH,
                cashEnabled = true,
                nonCashEnabled = true,
                onSelect = {},
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Preview(name = "CTA · total corto", showBackground = true, widthDp = 320)
@Composable
private fun PrimaryCtaCashShortPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(320.dp)) {
            PrimaryCtaButton(
                state = cashPreviewState(total = 65.0),
                warningScale = 1f,
                isInsufficient = false,
                onClick = {},
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Preview(name = "CTA · total largo · sin wrap", showBackground = true, widthDp = 320)
@Composable
private fun PrimaryCtaCashLongPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(320.dp)) {
            PrimaryCtaButton(
                state = cashPreviewState(total = 123_456.78),
                warningScale = 1f,
                isInsufficient = false,
                onClick = {},
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Preview(name = "CTA · estado insuficiente", showBackground = true, widthDp = 320)
@Composable
private fun PrimaryCtaInsufficientPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(320.dp)) {
            PrimaryCtaButton(
                state = cashPreviewState(total = 123_456.78),
                warningScale = 1.03f,
                isInsufficient = true,
                onClick = {},
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Preview(name = "Tarjeta/Otro · fila estrecha (<360dp)", showBackground = true, widthDp = 320)
@Composable
private fun NonCashRowNarrowPreview() {
    PosTheme {
        Surface(modifier = Modifier.width(320.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                NonCashRow(
                    forma = nonCashMethods().first(),
                    value = "120.00",
                    pendingAmount = "130.00",
                    canUseCredit = false,
                    narrow = true,
                    onValueChange = {},
                    onUseExactAmount = {},
                )
            }
        }
    }
}

@Preview(name = "Tarjeta/Otro · fila ancha", showBackground = true, widthDp = 600)
@Composable
private fun NonCashRowWidePreview() {
    PosTheme {
        Surface(modifier = Modifier.width(600.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                nonCashMethods().forEach { forma ->
                    NonCashRow(
                        forma = forma,
                        value = "",
                        pendingAmount = "250.00",
                        canUseCredit = true,
                        narrow = false,
                        onValueChange = {},
                        onUseExactAmount = {},
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview-only state builders.
// ─────────────────────────────────────────────────────────────────────────────

private fun cashPreviewState(
    total: Double,
    withDiscount: Boolean = false,
): PaymentState {
    val discount = if (withDiscount) total * 0.10 else 0.0
    val taxableBase = total - discount
    val tax = taxableBase - taxableBase / 1.16
    val snapshot =
        SaleFinancialSnapshot(
            subtotalGross = taxableBase / 1.16,
            itemDiscounts = discount,
            subtotalNet = taxableBase / 1.16,
            tax = tax,
            total = total,
        )
    return PaymentState(
        totalAmount = total,
        selectedMethod = PaymentMethod.CASH,
        formasPago =
            listOf(
                FormaPago(1, siglas = "CASH", descripcion = "Efectivo", activo = 1, pos = 1, grupo = 1, orden = 1, tipoMoneda = "BASE"),
            ),
        financialSnapshot = snapshot,
        taxLabel = "IVA",
    )
}

private fun nonCashPreviewState(total: Double): PaymentState {
    val tax = total - total / 1.16
    val snapshot =
        SaleFinancialSnapshot(
            subtotalGross = total / 1.16,
            itemDiscounts = 0.0,
            subtotalNet = total / 1.16,
            tax = tax,
            total = total,
        )
    return PaymentState(
        totalAmount = total,
        selectedMethod = PaymentMethod.NON_CASH,
        formasPago = nonCashMethods(),
        financialSnapshot = snapshot,
        taxLabel = "IVA",
    )
}

private fun nonCashMethods(): List<FormaPago> =
    listOf(
        FormaPago(2, siglas = "TDC", descripcion = "Visa Crédito", activo = 1, pos = 1, grupo = 1, orden = 1, tipoMoneda = "BASE"),
        FormaPago(3, siglas = "TDD", descripcion = "Mastercard Débito", activo = 1, pos = 1, grupo = 1, orden = 2, tipoMoneda = "BASE"),
        FormaPago(4, siglas = "TR", descripcion = "Transferencia ACH", activo = 1, pos = 1, grupo = 1, orden = 3, tipoMoneda = "BASE"),
        FormaPago(5, siglas = "ZELLE", descripcion = "Zelle", activo = 1, pos = 1, grupo = 1, orden = 4, tipoMoneda = "BASE"),
    )
