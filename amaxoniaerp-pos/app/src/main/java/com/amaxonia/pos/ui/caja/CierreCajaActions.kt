package com.amaxonia.pos.ui.caja

/** Acciones del cierre agrupadas para mantener las firmas de los composables pequeñas. */
internal class CierreCajaActions(
    val onConfirmClose: () -> Unit,
    val onPrintReportX: () -> Unit,
    val onPrintReportZ: () -> Unit,
)

/** Estado puramente visual del contenido listo/cerrando. */
internal data class CierreCajaReadyState(
    val isClosing: Boolean,
    val isPrintingReportX: Boolean,
    val isPrintingReportZ: Boolean,
    val showReportButtons: Boolean,
)
