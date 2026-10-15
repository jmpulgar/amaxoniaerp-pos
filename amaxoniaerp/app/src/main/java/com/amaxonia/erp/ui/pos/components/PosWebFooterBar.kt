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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WorkOutline
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
import com.amaxonia.erp.ui.components.isLargeScreen
import com.amaxonia.erp.ui.theme.FlowBrandBlue
import com.amaxonia.erp.ui.theme.FlowTableBorder
import com.amaxonia.erp.ui.theme.FlowTableTextDark

@Composable
fun PosWebFooterBar(
    isOnline: Boolean = true,
    cajaName: String?,
    sellerName: String?,
    sucursalName: String?,
    activeDocumentType: String = "Factura",
    onDocumentTypeSelected: (String) -> Unit,
    onNavigateToCajas: () -> Unit,
    onChangeSeller: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLarge = isLargeScreen()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isLarge) 50.dp else 34.dp)
            .background(Color(0xFFF8FAFC))
            .border(width = 0.5.dp, color = FlowTableBorder)
            .padding(horizontal = if (isLarge) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // --- Izquierda: Chips de Estado y Contexto del Dispositivo ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isLarge) 10.dp else 8.dp),
        ) {
            // Badge Online / Offline
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, FlowTableBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = if (isLarge) 12.dp else 8.dp, vertical = if (isLarge) 6.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isLarge) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) Color(0xFF16A34A) else Color(0xFFDC2626))
                )
                Text(
                    text = if (isOnline) "Online" else "Offline",
                    fontSize = if (isLarge) 13.5.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOnline) Color(0xFF16A34A) else Color(0xFFDC2626),
                )
            }

            // Chip Caja
            ContextInfoChip(
                icon = Icons.Default.PointOfSale,
                text = cajaName ?: "CAJA 1 SUC. 1",
                isLarge = isLarge,
                onClick = onNavigateToCajas,
            )

            // Chip Vendedor
            ContextInfoChip(
                icon = Icons.Default.Engineering,
                text = sellerName ?: "VENDEDOR/SALONERO #1",
                isLarge = isLarge,
                onClick = onChangeSeller,
            )

            // Chip Sucursal
            ContextInfoChip(
                icon = Icons.Default.Storefront,
                text = sucursalName ?: "SUC. AMAXONIA",
                isLarge = isLarge,
            )
        }

        // --- Derecha: Pestañas de Tipos de Documento (Factura, Pre-Orden, Apartado, etc.) ---
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isLarge) 8.dp else 6.dp),
        ) {
            DocumentTypeTabButton(
                label = "Factura",
                icon = Icons.Default.Receipt,
                isSelected = activeDocumentType == "Factura",
                isLarge = isLarge,
                onClick = { onDocumentTypeSelected("Factura") },
            )
            DocumentTypeTabButton(
                label = "Pre-Orden",
                icon = Icons.AutoMirrored.Filled.ListAlt,
                isSelected = activeDocumentType == "Pre-Orden",
                isLarge = isLarge,
                onClick = { onDocumentTypeSelected("Pre-Orden") },
            )
            DocumentTypeTabButton(
                label = "Apartado",
                icon = Icons.Default.WorkOutline,
                isSelected = activeDocumentType == "Apartado",
                isLarge = isLarge,
                onClick = { onDocumentTypeSelected("Apartado") },
            )
            DocumentTypeTabButton(
                label = "Anticipo",
                icon = Icons.Default.BusinessCenter,
                isSelected = activeDocumentType == "Anticipo",
                isLarge = isLarge,
                onClick = { onDocumentTypeSelected("Anticipo") },
            )
            DocumentTypeTabButton(
                label = "Nota Crédito",
                icon = Icons.Default.Description,
                isSelected = activeDocumentType == "Nota Crédito",
                isLarge = isLarge,
                onClick = { onDocumentTypeSelected("Nota Crédito") },
            )
            DocumentTypeTabButton(
                label = "Nota Débito",
                icon = Icons.Default.CreditCard,
                isSelected = activeDocumentType == "Nota Débito",
                isLarge = isLarge,
                onClick = { onDocumentTypeSelected("Nota Débito") },
            )
            DocumentTypeTabButton(
                label = "Certificado",
                icon = Icons.Default.ConfirmationNumber,
                isSelected = activeDocumentType == "Certificado",
                isLarge = isLarge,
                onClick = { onDocumentTypeSelected("Certificado") },
            )
        }
    }
}

@Composable
private fun ContextInfoChip(
    icon: ImageVector,
    text: String,
    isLarge: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, FlowTableBorder, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = if (isLarge) 12.dp else 6.dp, vertical = if (isLarge) 6.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(if (isLarge) 18.dp else 13.dp),
        )
        Text(
            text = text,
            fontSize = if (isLarge) 13.5.sp else 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = FlowTableTextDark,
        )
    }
}

@Composable
private fun DocumentTypeTabButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    isLarge: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) FlowBrandBlue else Color.White)
            .border(
                width = 1.dp,
                color = if (isSelected) FlowBrandBlue else FlowTableBorder,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = if (isLarge) 12.dp else 7.dp, vertical = if (isLarge) 6.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color.White else Color(0xFF64748B),
            modifier = Modifier.size(if (isLarge) 17.dp else 12.dp),
        )
        Text(
            text = label,
            fontSize = if (isLarge) 13.5.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else Color(0xFF475569),
        )
    }
}
