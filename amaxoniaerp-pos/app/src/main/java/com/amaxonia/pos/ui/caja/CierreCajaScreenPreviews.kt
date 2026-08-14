package com.amaxonia.pos.ui.caja

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.amaxonia.pos.domain.model.caja.CierreCajaPaymentLine
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.ui.theme.PosTheme

private const val HUGE_AMOUNT_SCALE = 10_000.0

/**
 * Visual regression surface for the cash-close (Cierre de Caja) screen.
 *
 * Exercises the production [ReadyContent] composable with the ready-state summary at every
 * target Android width plus landscape, including enormous amounts ("Cierre Esperado", payment
 * lines) that must shrink via AdaptiveAmountText instead of clipping, and long caja names /
 * payment labels that must ellipsize gracefully.
 */
@Preview(name = "Cierre Â· 320Ã—568", showBackground = true, widthDp = 320, heightDp = 568)
@Composable
private fun CierreReady320() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ReadyContent(
            summary = previewSummary(),
            isClosing = false,
            isPrintingReportX = false,
            isPrintingReportZ = false,
            showReportButtons = true,
            actions = previewActions(),
        )
    }
}

@Preview(name = "Cierre Â· 360Ã—640", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun CierreReady360() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ReadyContent(
            summary = previewSummary(),
            isClosing = false,
            isPrintingReportX = false,
            isPrintingReportZ = false,
            showReportButtons = true,
            actions = previewActions(),
        )
    }
}

@Preview(name = "Cierre Â· 390Ã—844 (montos enormes)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CierreReadyHugeAmounts() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ReadyContent(
            summary = previewSummary(huge = true),
            isClosing = false,
            isPrintingReportX = false,
            isPrintingReportZ = false,
            showReportButtons = true,
            actions = previewActions(),
        )
    }
}

@Preview(name = "Cierre Â· 480Ã—960 (cerrando)", showBackground = true, widthDp = 480, heightDp = 960)
@Composable
private fun CierreReadyClosing() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ReadyContent(
            summary = previewSummary(),
            isClosing = true,
            isPrintingReportX = false,
            isPrintingReportZ = false,
            showReportButtons = false,
            actions = previewActions(),
        )
    }
}

@Preview(name = "Cierre Â· landscape Â· 733Ã—360", showBackground = true, widthDp = 733, heightDp = 360)
@Composable
private fun CierreReadyLandscape() = PosTheme {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ReadyContent(
            summary = previewSummary(),
            isClosing = false,
            isPrintingReportX = false,
            isPrintingReportZ = false,
            showReportButtons = true,
            actions = previewActions(),
        )
    }
}

private fun previewActions() =
    CierreCajaActions(
        onConfirmClose = {},
        onPrintReportX = {},
        onPrintReportZ = {},
    )

private fun previewSummary(huge: Boolean = false): CierreCajaSummary {
    val scale = if (huge) HUGE_AMOUNT_SCALE else 1.0
    return CierreCajaSummary(
        idCajaSecuencia = "preview-seq",
        idCaja = "preview-caja",
        cajaName = "Caja Principal Sucursal Centro Comercial Los Jardines",
        openedAt = "2026-08-14 08:15",
        openAmount = 500.0 * scale,
        totalSales = 12_480.75 * scale,
        totalCash = 4_820.25 * scale,
        totalCard = 6_150.50 * scale,
        totalOther = 1_510.00 * scale,
        transactionCount = 87,
        expectedClose = 5_320.25 * scale,
        paymentLines =
            listOf(
                CierreCajaPaymentLine(1, "Efectivo", "CASH", 4_820.25 * scale),
                CierreCajaPaymentLine(2, "Mastercard DÃ©bito", "TARJETA", 3_275.30 * scale),
                CierreCajaPaymentLine(3, "Visa CrÃ©dito", "TARJETA", 2_875.20 * scale),
                CierreCajaPaymentLine(4, "Transferencia ACH", "OT", 1_510.00 * scale),
            ),
    )
}
