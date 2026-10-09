package com.amaxonia.erp.ui.pos.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
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
import com.amaxonia.erp.ui.theme.FlowBrandBlue
import com.amaxonia.erp.ui.theme.FlowHeaderGradient

@Composable
fun PosWebHeaderBar(
    correlativo: String = "#001-00112",
    cartItemCount: Int = 0,
    isCatalogOpen: Boolean = false,
    onOpenDrawer: () -> Unit = {},
    onClearCart: () -> Unit,
    onSaveDraft: () -> Unit,
    onSearchInvoices: () -> Unit,
    onPrintReceipt: () -> Unit,
    onToggleCatalog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(FlowHeaderGradient)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // --- Izquierda: Hamburguesa + Título + Correlativo ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .clickable { onOpenDrawer() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menú principal",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }

            Text(
                text = "Facturación",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = correlativo,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // --- Derecha: Acciones Rápidas Superiores (Limpiar, Guardar, Buscar, Imprimir) ---
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HeaderActionItem(
                icon = Icons.Default.AutoFixHigh,
                label = "Limpiar",
                badgeCount = if (cartItemCount > 0) cartItemCount else null,
                onClick = onClearCart,
            )

            HeaderActionItem(
                icon = Icons.Default.Save,
                label = "Guardar",
                onClick = onSaveDraft,
            )

            HeaderActionItem(
                icon = Icons.Default.Search,
                label = "Buscar",
                onClick = onSearchInvoices,
            )

            HeaderActionItem(
                icon = Icons.Default.Print,
                label = "Imprimir",
                onClick = onPrintReceipt,
            )
        }
    }
}

@Composable
private fun HeaderActionItem(
    icon: ImageVector,
    label: String,
    badgeCount: Int? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (badgeCount != null) {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White,
                    ) {
                        Text(text = badgeCount.toString(), fontSize = 8.sp)
                    }
                }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
