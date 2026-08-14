package com.amaxonia.pos.ui.caja

/** Acciones del cierre agrupadas para mantener las firmas de los composables pequeñas. */
internal class CierreCajaActions(
    val onConfirmClose: () -> Unit,
    val onPrintReportX: () -> Unit,
    val onPrintReportZ: () -> Unit,
)
