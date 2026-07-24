package com.amaxonia.pos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Single radius scale for the app, replacing scattered inline RoundedCornerShape(...) calls.
 * Wired into PosTheme's MaterialTheme(shapes = PosShapes.material) so standard Material3
 * components (Button, Card, TextField, ModalBottomSheet, AlertDialog...) inherit it for free.
 */
val PosShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/** Shapes outside the Material scale, for POS-specific chrome. */
object PosExtraShapes {
    val Pill = RoundedCornerShape(percent = 50)
    val BottomBarTop = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val NavPill = RoundedCornerShape(26.dp)
}
