package com.amaxonia.erp.ui.pos.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.ClientBranch
import com.amaxonia.erp.ui.components.isLargeScreen
import com.amaxonia.erp.ui.theme.FlowBrandBlue
import com.amaxonia.erp.ui.theme.FlowTableBorder
import com.amaxonia.erp.ui.theme.FlowTableTextDark
import com.amaxonia.erp.ui.theme.FlowTableTextMuted

/**
 * Diálogo modal para seleccionar la sucursal de un cliente con múltiples sucursales.
 * Fiel a los tokens de Flow UI y limpio para pantallas táctiles de POS.
 */
@Composable
fun ClientBranchSelectionDialog(
    client: Client?,
    branches: List<ClientBranch>,
    selectedBranch: ClientBranch?,
    onSelectBranch: (ClientBranch) -> Unit,
    onDismiss: () -> Unit,
) {
    val isLarge = isLargeScreen()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 340.dp, max = if (isLarge) 520.dp else 440.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isLarge) 24.dp else 18.dp),
            ) {
                // Cabecera: Icono Tienda + Título + Subtítulo + Botón Cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isLarge) 42.dp else 36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEEF5FF))
                                .border(1.dp, Color(0xFFDCEBFF), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = FlowBrandBlue,
                                modifier = Modifier.size(if (isLarge) 22.dp else 18.dp),
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sucursales del Cliente",
                                fontSize = if (isLarge) 16.sp else 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlowTableTextDark,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${client?.name ?: "Cliente"} • ${branches.size} sucursales disponibles",
                                fontSize = if (isLarge) 12.sp else 11.sp,
                                color = FlowTableTextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Selecciona la sucursal para facturación y entrega:",
                    fontSize = if (isLarge) 12.5.sp else 11.5.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Lista de sucursales
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (isLarge) 380.dp else 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(branches, key = { it.sucursalId }) { branch ->
                        val isSelected = branch.sucursalId == selectedBranch?.sucursalId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFFEEF5FF) else Color(0xFFFAFBFD))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) FlowBrandBlue else FlowTableBorder,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { onSelectBranch(branch) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Radio / Check Indicator
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) FlowBrandBlue else Color.White)
                                    .border(
                                        1.5.dp,
                                        if (isSelected) FlowBrandBlue else Color(0xFFCBD5E1),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = branch.nombreSucursal,
                                    fontSize = if (isLarge) 13.5.sp else 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) FlowBrandBlue else FlowTableTextDark,
                                )

                                branch.direccion?.takeIf { it.isNotBlank() }?.let { dir ->
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Text(
                                            text = dir,
                                            fontSize = if (isLarge) 11.5.sp else 10.5.sp,
                                            color = FlowTableTextMuted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                branch.telefonoContacto?.takeIf { it.isNotBlank() }?.let { tel ->
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(11.dp),
                                        )
                                        Text(
                                            text = tel,
                                            fontSize = if (isLarge) 11.sp else 10.sp,
                                            color = FlowTableTextMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botón Cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF64748B),
                        ),
                    ) {
                        Text(
                            text = "Cerrar",
                            fontSize = if (isLarge) 12.5.sp else 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
