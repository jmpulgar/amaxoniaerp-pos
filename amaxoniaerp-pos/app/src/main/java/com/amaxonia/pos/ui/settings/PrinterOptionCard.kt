package com.amaxonia.pos.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tarjeta de selección de un tipo de impresora. */
@Composable
internal fun PrinterOptionCard(
    visual: PrinterOptionVisual,
    title: String,
    description: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val icon = visual.icon
    val iconTint = visual.iconTint
    ElevatedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.elevatedCardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.12f,
                        )
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        elevation =
            CardDefaults.elevatedCardElevation(
                defaultElevation = if (isSelected) 4.dp else 1.dp,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrinterOptionIcon(icon = icon, iconTint = iconTint)

            Spacer(modifier = Modifier.width(16.dp))

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Selection indicator
            PrinterSelectionIndicator(isSelected = isSelected, onSelect = onSelect)
        }
    }
}

@Composable
private fun PrinterSelectionIndicator(
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    if (isSelected) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = "Seleccionada",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    } else {
        RadioButton(
            selected = false,
            onClick = onSelect,
            colors =
                RadioButtonDefaults.colors(
                    unselectedColor = MaterialTheme.colorScheme.outline,
                ),
        )
    }
}

@Composable
private fun PrinterOptionIcon(
    icon: ImageVector,
    iconTint: Color,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(26.dp),
        )
    }
}
