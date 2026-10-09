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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(Color(0xFFF8FAFC))
            .border(width = 0.5.dp, color = FlowTableBorder)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // --- Izquierda: Chips de Estado y Contexto del Dispositivo ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Badge Online / Offline
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, FlowTableBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) Color(0xFF16A34A) else Color(0xFFDC2626))
                )
                Text(
                    text = if (isOnline) "Online" else "Offline",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isOnline) Color(0xFF16A34A) else Color(0xFFDC2626),
                )
            }

            // Chip Caja
            ContextInfoChip(
                icon = Icons.Default.PointOfSale,
                text = cajaName ?: "CAJA 1 SUC. 1",
                onClick = onNavigateToCajas,
            )

            // Chip Vendedor
            ContextInfoChip(
                icon = Icons.Default.Engineering,
                text = sellerName ?: "VENDEDOR/SALONERO #1",
                onClick = onChangeSeller,
            )

            // Chip Sucursal
            ContextInfoChip(
                icon = Icons.Default.Storefront,
                text = sucursalName ?: "SUC. AMAXONIA",
            )
        }

        // --- Derecha: Pestañas de Tipos de Documento (Factura, Pre-Orden, Apartado, etc.) ---
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DocumentTypeTabButton(
                label = "Factura",
                icon = Icons.Default.Receipt,
                isSelected = activeDocumentType == "Factura",
                onClick = { onDocumentTypeSelected("Factura") },
            )
            DocumentTypeTabButton(
                label = "Pre-Orden",
                icon = Icons.Default.ListAlt,
                isSelected = activeDocumentType == "Pre-Orden",
                onClick = { onDocumentTypeSelected("Pre-Orden") },
            )
            DocumentTypeTabButton(
                label = "Apartado",
                icon = Icons.Default.WorkOutline,
                isSelected = activeDocumentType == "Apartado",
                onClick = { onDocumentTypeSelected("Apartado") },
            )
            DocumentTypeTabButton(
                label = "Anticipo",
                icon = Icons.Default.BusinessCenter,
                isSelected = activeDocumentType == "Anticipo",
                onClick = { onDocumentTypeSelected("Anticipo") },
            )
            DocumentTypeTabButton(
                label = "Nota Crédito",
                icon = Icons.Default.Description,
                isSelected = activeDocumentType == "Nota Crédito",
                onClick = { onDocumentTypeSelected("Nota Crédito") },
            )
            DocumentTypeTabButton(
                label = "Nota Débito",
                icon = Icons.Default.CreditCard,
                isSelected = activeDocumentType == "Nota Débito",
                onClick = { onDocumentTypeSelected("Nota Débito") },
            )
            DocumentTypeTabButton(
                label = "Certificado",
                icon = Icons.Default.ConfirmationNumber,
                isSelected = activeDocumentType == "Certificado",
                onClick = { onDocumentTypeSelected("Certificado") },
            )
        }
    }
}

@Composable
private fun ContextInfoChip(
    icon: ImageVector,
    text: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(1.dp, FlowTableBorder, RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = FlowTableTextDark,
        )
    }
}

@Composable
private fun DocumentTypeTabButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) FlowBrandBlue else Color.White)
            .border(
                width = 1.dp,
                color = if (isSelected) FlowBrandBlue else FlowTableBorder,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color.White else Color(0xFF64748B),
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else Color(0xFF475569),
        )
    }
}
