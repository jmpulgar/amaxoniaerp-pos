package com.amaxonia.erp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Escala unificada de redondeo para la aplicación.
 * Conectada al MaterialTheme en [PosTheme] para que todos los componentes estándar
 * de Material 3 (Button, Card, TextField, AlertDialog, etc.) la hereden de forma coherente.
 */
val PosShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/** Formas adicionales específicas para la interfaz y cromo del POS. */
object PosExtraShapes {
    val Pill = RoundedCornerShape(percent = 50)
    val BottomBarTop = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val NavPill = RoundedCornerShape(26.dp)
    val CardRadius = RoundedCornerShape(16.dp)
    val FeaturedCardRadius = RoundedCornerShape(20.dp)
    val DialogRadius = RoundedCornerShape(20.dp)
    val InputRadius = RoundedCornerShape(12.dp)
    val BadgeRadius = RoundedCornerShape(6.dp)
}
