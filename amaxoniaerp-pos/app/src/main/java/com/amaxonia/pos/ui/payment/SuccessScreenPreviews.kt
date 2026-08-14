package com.amaxonia.pos.ui.payment

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.amaxonia.pos.domain.model.payment.PaymentSuccessPayload
import com.amaxonia.pos.ui.theme.PosTheme

/**
 * Visual regression surface for the payment-success (peak-end) screen.
 *
 * Exercises [SuccessContent] — the pure presentation layer wired by [SuccessScreen] — across
 * every target Android size and the most relevant payload variants (cash single-currency,
 * multi-currency with cambio in Bs, and a sale whose fiscal confirmation is still pending).
 */
@Preview(name = "Éxito · 320×568 (smallest)", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
internal fun SuccessPortrait320() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SuccessContent(
            isLoading = false,
            payload = previewPayload(codFactura = "FAC-2026-000123", changeDue = 234.50),
            actions = previewActions(),
        )
    }
}

@Preview(name = "Éxito · 360×640", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
internal fun SuccessPortrait360() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SuccessContent(
            isLoading = false,
            payload = previewPayload(codFactura = "FAC-2026-000123", changeDue = 12.75),
            actions = previewActions(),
        )
    }
}

@Preview(name = "Éxito · 390×844 (multi-moneda)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
internal fun SuccessPortrait390MultiCurrency() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SuccessContent(
            isLoading = false,
            payload =
                previewPayload(
                    codFactura = "FAC-2026-000999",
                    changeDue = 64.30,
                    secondaryAmounts = 1_820.16 to 469.78,
                ),
            actions = previewActions(),
        )
    }
}

@Preview(name = "Éxito · 412×915 (fiscal pendiente)", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
internal fun SuccessPortrait412FiscalPending() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SuccessContent(
            isLoading = false,
            payload =
                previewPayload(
                    codFactura = "",
                    changeDue = 0.0,
                    feError = "El proveedor fiscal no respondió a tiempo; la factura quedó en cola de confirmación.",
                ),
            actions = previewActions(),
        )
    }
}

@Preview(name = "Éxito · 480×960 (cambio grande)", showBackground = true, widthDp = 480, heightDp = 960)
@Composable
internal fun SuccessPortrait480BigChange() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SuccessContent(
            isLoading = false,
            payload = previewPayload(codFactura = "FAC-2026-777777", changeDue = 9_876_543.21),
            actions = previewActions(),
        )
    }
}

@Preview(name = "Éxito · landscape · 733×360", showBackground = true, widthDp = 733, heightDp = 360)
@Composable
internal fun SuccessLandscapePhone() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SuccessContent(
            isLoading = false,
            payload =
                previewPayload(
                    codFactura = "FAC-2026-000123",
                    changeDue = 234.50,
                    secondaryAmounts = 1_820.16 to 469.78,
                ),
            actions = previewActions(),
        )
    }
}

@Preview(name = "Éxito · cargando", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
internal fun SuccessLoadingPreview() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SuccessContent(
            isLoading = true,
            payload = null,
            actions = previewActions(),
        )
    }
}

private fun previewActions() =
    SuccessActions(
        onPrintReceipt = {},
        onSendReceiptEmail = {},
        onNextOrder = {},
    )

private fun previewPayload(
    codFactura: String,
    changeDue: Double,
    secondaryAmounts: Pair<Double, Double>? = null,
    feError: String? = null,
): PaymentSuccessPayload =
    PaymentSuccessPayload(
        changeDue = changeDue,
        paymentMethodsLabel = "Efectivo",
        codFactura = codFactura,
        transactionId = "preview-tx",
        totalBs = secondaryAmounts?.first ?: 0.0,
        changeDueBs = secondaryAmounts?.second ?: 0.0,
        abrMonedaSecundaria = if (secondaryAmounts != null) "VES" else "",
        isMultiCurrency = secondaryAmounts != null,
        feError = feError,
        tableSessionClosed = true,
    )
