package com.amaxonia.erp.ui.clients

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.erp.domain.model.AccountStatement
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.TaxpayerType
import com.amaxonia.erp.ui.components.FlowInputBorder
import com.amaxonia.erp.ui.components.FlowInputGroup
import com.amaxonia.erp.ui.components.FlowInputText
import com.amaxonia.erp.ui.components.FlowPlaceholder
import com.amaxonia.erp.ui.components.FlowPrependBg
import com.amaxonia.erp.ui.components.FlowSectionPortlet
import com.amaxonia.erp.ui.components.FlowSelectGroup
import com.amaxonia.erp.ui.components.PosEmptyState
import com.amaxonia.erp.ui.components.PosFeedbackCard
import com.amaxonia.erp.ui.components.PosVisualAction
import com.amaxonia.erp.ui.components.PosVisualTone
import com.amaxonia.erp.ui.theme.FlowHeaderGradient
import com.amaxonia.erp.ui.theme.PosExtraShapes
import java.util.Locale

private fun formatMoney(amount: Double): String = String.format(Locale.US, "$ %,.2f", amount)

private fun formatColAmount(amount: Double, showZero: Boolean = false): String =
    if (amount > 0.0) {
        String.format(Locale.US, "$%.2f", amount)
    } else if (amount < 0.0) {
        String.format(Locale.US, "-$%.2f", -amount)
    } else if (showZero) {
        "0.00"
    } else {
        ""
    }

/**
 * Pantalla principal del Módulo de Clientes.
 * Réplica visual 1:1 de `c:\wamp64\www\amaxonia_administrativo\app\module\cliente\main.html`
 * y `flow-app.css`.
 */
@Composable
fun ClientListScreen(
    viewModel: ClientListViewModel,
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFFF4F5F8)),
    ) {
        // ══════════════════════════════════════════════════════════════
        // ENCABEZADO PRINCIPAL (1:1 con PosWebHeaderBar y flow-app.css)
        // ══════════════════════════════════════════════════════════════
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(FlowHeaderGradient)
                    .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Izquierda: Hamburguesa + Ícono + Título Clientes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Botón Hamburguesa 1:1 con POS
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
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
                        modifier = Modifier.size(20.dp),
                    )
                }

                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Clientes",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.01).sp,
                )
            }

            // Derecha: Sincronizar Express + Agregar (+) + Eliminar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Botón Sincronizar (#btnSincronizar) estilo verde Flow
                Box(
                    modifier =
                        Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981))
                            .clickable { /* Sincronizar */ }
                            .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Sincronizar Express",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Botón Agregar (+): .btn.btn-accent
                Box(
                    modifier =
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.20f))
                            .clickable { viewModel.openCreateForm() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                // Botón Eliminar: .btn.btn-accent
                Box(
                    modifier =
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.20f))
                            .clickable { /* Eliminar selección */ },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        // CUERPO DEL MÓDULO (padding 14dp)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(14.dp),
        ) {
            // Buscador DataTables a la derecha
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(300.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .border(BorderStroke(1.dp, Color(0xFFD5DDE8)), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                text = "Buscar cliente...",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.5.sp,
                            )
                        }
                        BasicTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(color = Color(0xFF0F172A), fontSize = 12.5.sp),
                            cursorBrush = SolidColor(Color(0xFF2488FA)),
                        )
                        if (state.searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp).clickable { viewModel.onSearchQueryChange("") },
                            )
                        }
                    }
                }
            }

            if (state.error != null) {
                PosFeedbackCard(
                    title = "Error",
                    message = state.error!!,
                    tone = PosVisualTone.Error,
                    action = PosVisualAction(label = "Reintentar", onClick = viewModel::retry),
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // TABLA DE DATATABLES 1:1
            if (state.isLoading && state.clients.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF2488FA))
                }
            } else if (state.clients.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    PosEmptyState(
                        icon = Icons.Default.Person,
                        title = "Sin registros",
                        message = "No se encontraron clientes registrados.",
                    )
                }
            } else {
                ClientDataTable(
                    clients = state.clients,
                    onEdit = viewModel::openEditForm,
                    onStatement = viewModel::openAccountStatement,
                    onLoadMore = viewModel::loadMore,
                    hasMore = !state.endOfListReached,
                    isLoadingMore = state.isLoading,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    // Modal Crear / Editar Cliente
    if (state.isFormOpen) {
        ClientWebModal(
            client = state.editingClient,
            onDismiss = viewModel::closeForm,
            onConfirm = viewModel::saveClient,
        )
    }

    // Modal Estado de Cuenta
    if (state.isAccountStatementOpen) {
        ClientWebAccountStatementModal(
            client = state.selectedClientForStatement,
            statement = state.selectedAccountStatement,
            isLoading = state.isLoadingAccountStatement,
            errorMessage = state.accountStatementError,
            searchVisible = state.statementSearchVisible,
            filterTipo = state.statementFilterTipo,
            filterNumero = state.statementFilterNumero,
            filterSerieNumero = state.statementFilterSerieNumero,
            filterMes = state.statementFilterMes,
            filterAnio = state.statementFilterAnio,
            filterFechaInicio = state.statementFilterFechaInicio,
            filterMesFin = state.statementFilterMesFin,
            filterAnioFin = state.statementFilterAnioFin,
            filterFechaFin = state.statementFilterFechaFin,
            statusPendiente = state.statementStatusPendiente,
            statusPagado = state.statementStatusPagado,
            statusAnulado = state.statementStatusAnulado,
            actionMessage = state.statementActionMessage,
            onToggleSearch = viewModel::toggleStatementAdvancedSearch,
            onFilterTipoChange = viewModel::setStatementFilterTipo,
            onFilterNumeroChange = viewModel::setStatementFilterNumero,
            onFilterSerieNumeroChange = viewModel::setStatementFilterSerieNumero,
            onFilterMesChange = viewModel::setStatementFilterMes,
            onFilterAnioChange = viewModel::setStatementFilterAnio,
            onFilterFechaInicioChange = viewModel::setStatementFilterFechaInicio,
            onFilterMesFinChange = viewModel::setStatementFilterMesFin,
            onFilterAnioFinChange = viewModel::setStatementFilterAnioFin,
            onFilterFechaFinChange = viewModel::setStatementFilterFechaFin,
            onToggleStatusPendiente = viewModel::toggleStatementStatusPendiente,
            onToggleStatusPagado = viewModel::toggleStatementStatusPagado,
            onToggleStatusAnulado = viewModel::toggleStatementStatusAnulado,
            onClearFilters = viewModel::clearStatementFilters,
            onExportExcel = viewModel::onExportExcel,
            onExportDetailExcel = viewModel::onExportDetailExcel,
            onExportPdf = viewModel::onExportPdf,
            onSendEmail = viewModel::onSendEmail,
            onDismissActionMessage = viewModel::dismissStatementActionMessage,
            onDismiss = viewModel::closeAccountStatement,
        )
    }
}

/**
 * Tabla de clientes fiel a `.amaxonia_module__list.table.table-bordered` y `flow-app.css`.
 */
@Composable
private fun ClientDataTable(
    clients: List<Client>,
    onEdit: (Client) -> Unit,
    onStatement: (Client) -> Unit,
    onLoadMore: () -> Unit,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE4E9F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState),
            ) {
                Column(modifier = Modifier.widthIn(min = 1100.dp)) {
                    // CABECERA THEAD (flow-app.css .table thead th: #F8FAFC, #64748B, 11px, bold, uppercase)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("", modifier = Modifier.width(36.dp))
                        Text("CÓDIGO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.width(90.dp))
                        Text("IDENTIFICACIÓN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.width(130.dp))
                        Text("NOMBRE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.width(230.dp))
                        Text("DIRECCIÓN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.width(200.dp))
                        Text("TELÉFONO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.width(110.dp))
                        Text("CORREO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.width(170.dp))
                        Text("SALDO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.End, modifier = Modifier.width(100.dp))
                        Text("ACCIONES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.Center, modifier = Modifier.width(110.dp))
                    }

                    HorizontalDivider(color = Color(0xFFE4E9F0), thickness = 1.dp)

                    // FILAS TBODY (flow-app.css: hover #F5F9FF con borde izquierdo #2488FA, acciones .listado-accion)
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        items(clients, key = { it.id.ifBlank { it.code } }) { client ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Color.White)
                                        .clickable { onEdit(client) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Checkbox (.m-checkbox)
                                Box(
                                    modifier =
                                        Modifier
                                            .width(36.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(16.dp)
                                                .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp)),
                                    )
                                }

                                // Código
                                Text(
                                    text = client.code.ifBlank { "-" },
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.width(90.dp),
                                )

                                // Identificación + DV
                                val rucText = if (client.dv.isNotBlank()) "${client.identification}-${client.dv}" else client.identification
                                Text(
                                    text = rucText.ifBlank { "-" },
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF475569),
                                    modifier = Modifier.width(130.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                // Nombre / Razón Social
                                Column(modifier = Modifier.width(230.dp).padding(end = 8.dp)) {
                                    Text(
                                        text = client.fullName.ifBlank { "Sin nombre" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (client.nombreComercial.isNotBlank()) {
                                        Text(
                                            text = client.nombreComercial,
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                // Dirección
                                Text(
                                    text = client.address.ifBlank { "-" },
                                    fontSize = 12.sp,
                                    color = Color(0xFF475569),
                                    modifier = Modifier.width(200.dp).padding(end = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                // Teléfono
                                Text(
                                    text = client.phone.ifBlank { "-" },
                                    fontSize = 12.sp,
                                    color = Color(0xFF475569),
                                    modifier = Modifier.width(110.dp),
                                    maxLines = 1,
                                )

                                // Correo
                                Text(
                                    text = client.email.ifBlank { "-" },
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.width(170.dp).padding(end = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                // Saldo (alineado derecha con formato tabular)
                                Text(
                                    text = formatMoney(client.saldo),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (client.saldo > 0.0) Color(0xFFDC2626) else Color(0xFF0F172A),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(100.dp),
                                )

                                // Acciones (.listado-accion de flow-app.css: 32x32px, radio 10px, tinte de color)
                                Row(
                                    modifier = Modifier.width(110.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // 1. Estado de Cuenta (.mdi-cash-usd)
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF2488FA).copy(alpha = 0.12f))
                                                .clickable { onStatement(client) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Paid,
                                            contentDescription = "Estado de Cuenta",
                                            tint = Color(0xFF2488FA),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // 2. Editar (.mdi-square-edit-outline)
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF4F46E5).copy(alpha = 0.12f))
                                                .clickable { onEdit(client) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar",
                                            tint = Color(0xFF4F46E5),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF0F3F8), thickness = 1.dp)
                        }

                        if (hasMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isLoadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF2488FA))
                                    } else {
                                        OutlinedButton(
                                            onClick = onLoadMore,
                                            shape = RoundedCornerShape(10.dp),
                                        ) {
                                            Text("Cargar más registros", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Componente para selector de Mes y Año réplica de `#fecha_mes` y `#fecha_anio` en `main-modal.html`.
 */
@Composable
private fun FlowMonthYearSelector(
    label: String,
    selectedMonth: String,
    selectedYear: String,
    onMonthSelected: (String) -> Unit,
    onYearChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    prependWidth: Dp = 90.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    val months =
        listOf(
            "" to "Todo",
            "01" to "Enero",
            "02" to "Febrero",
            "03" to "Marzo",
            "04" to "Abril",
            "05" to "Mayo",
            "06" to "Junio",
            "07" to "Julio",
            "08" to "Agosto",
            "09" to "Septiembre",
            "10" to "Octubre",
            "11" to "Noviembre",
            "12" to "Diciembre",
        )
    val displayMonth = months.firstOrNull { it.first == selectedMonth }?.second ?: "Todo"

    Row(
        modifier = modifier.height(44.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Prepend (.input-group-prepend > .input-group-text)
        Box(
            modifier =
                Modifier
                    .width(prependWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(FlowPrependBg)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Selector de mes (.m-select2)
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.White)
                    .border(BorderStroke(1.dp, FlowInputBorder))
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayMonth,
                    color = FlowInputText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp),
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White),
            ) {
                months.forEach { (code, name) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = name,
                                fontSize = 13.sp,
                                fontWeight = if (code == selectedMonth) FontWeight.Bold else FontWeight.Normal,
                                color = if (code == selectedMonth) Color(0xFF2488FA) else FlowInputText,
                            )
                        },
                        onClick = {
                            onMonthSelected(code)
                            expanded = false
                        },
                    )
                }
            }
        }

        // Campo de año (#fecha_anio)
        Box(
            modifier =
                Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(Color.White)
                    .border(
                        BorderStroke(1.dp, FlowInputBorder),
                        RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                    )
                    .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (selectedYear.isEmpty()) {
                Text(
                    text = "Año",
                    color = FlowPlaceholder,
                    fontSize = 12.sp,
                )
            }
            BasicTextField(
                value = selectedYear,
                onValueChange = onYearChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle =
                    TextStyle(
                        color = FlowInputText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                cursorBrush = SolidColor(Color(0xFF2488FA)),
            )
        }
    }
}

/**
 * Tarjeta de total resumen réplica 1:1 de `#resumenMovimiento` de `main-modal.html` y `main.css`.
 */
@Composable
private fun FlowSummaryBadge(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(44.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(BorderStroke(1.dp, badgeColor), RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Prepend de color sólido (.input-group-prepend > .input-group-text)
        Box(
            modifier =
                Modifier
                    .width(115.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp))
                    .background(badgeColor)
                    .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Valor alineado a la derecha (.form-control en negrita y color personalizado)
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.White)
                    .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = value,
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
    }
}

/**
 * Botón de acción con ícono y texto réplica de los botones superiores de acción (`#btnBuscar`, `#btnLimpiarFilter`, etc.).
 */
@Composable
private fun FlowActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = Modifier.height(34.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(imageVector = icon, contentDescription = text, tint = Color.White, modifier = Modifier.size(15.dp))
            Text(text = text, color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Chip del grupo segmentado de filtro de estatus réplica de `.btn-filtro-estatus` en `flow-app.css`.
 */
@Composable
private fun FlowStatusFilterChip(
    label: String,
    checked: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (checked) Color.White else Color.Transparent)
                .border(
                    BorderStroke(1.dp, if (checked) accentColor.copy(alpha = 0.45f) else Color.Transparent),
                    RoundedCornerShape(10.dp),
                )
                .clickable { onToggle() }
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Checkbox visual moderno
            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (checked) accentColor else Color.White)
                        .border(
                            BorderStroke(1.5.dp, if (checked) accentColor else Color(0xFFCBD5E1)),
                            RoundedCornerShape(5.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            Text(
                text = label,
                color = if (checked) Color(0xFF0F172A) else Color(0xFF64748B),
                fontSize = 12.5.sp,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Modal de Estado de Cuenta interactivo réplica 1:1 de `reportes_clientes_ventas/estado_cuenta_modal/main-modal.html`.
 */
@Composable
private fun ClientWebAccountStatementModal(
    client: Client?,
    statement: AccountStatement?,
    isLoading: Boolean,
    errorMessage: String?,
    searchVisible: Boolean,
    filterTipo: String,
    filterNumero: String,
    filterSerieNumero: String,
    filterMes: String,
    filterAnio: String,
    filterFechaInicio: String,
    filterMesFin: String,
    filterAnioFin: String,
    filterFechaFin: String,
    statusPendiente: Boolean,
    statusPagado: Boolean,
    statusAnulado: Boolean,
    actionMessage: String?,
    onToggleSearch: () -> Unit,
    onFilterTipoChange: (String) -> Unit,
    onFilterNumeroChange: (String) -> Unit,
    onFilterSerieNumeroChange: (String) -> Unit,
    onFilterMesChange: (String) -> Unit,
    onFilterAnioChange: (String) -> Unit,
    onFilterFechaInicioChange: (String) -> Unit,
    onFilterMesFinChange: (String) -> Unit,
    onFilterAnioFinChange: (String) -> Unit,
    onFilterFechaFinChange: (String) -> Unit,
    onToggleStatusPendiente: () -> Unit,
    onToggleStatusPagado: () -> Unit,
    onToggleStatusAnulado: () -> Unit,
    onClearFilters: () -> Unit,
    onExportExcel: () -> Unit,
    onExportDetailExcel: () -> Unit,
    onExportPdf: () -> Unit,
    onSendEmail: () -> Unit,
    onDismissActionMessage: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ENCABEZADO MODAL CON DEGRADADO (.kt-portlet__head.amaxonia_module__head)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(FlowHeaderGradient)
                            .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Paid,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = "Estado de Cuenta: ${client?.fullName ?: statement?.clientName ?: ""}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Botón "Búsqueda" (kt-popover Búsqueda Avanzada)
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (searchVisible) Color(0xFF2488FA) else Color.White.copy(alpha = 0.20f),
                                    )
                                    .border(
                                        BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable { onToggleSearch() }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Búsqueda",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Búsqueda",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }

                        // Botón "Cerrar"
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEF4444))
                                    .clickable { onDismiss() }
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "Cerrar",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF2488FA))
                        }
                    } else if (errorMessage != null) {
                        PosFeedbackCard(title = "Error", message = errorMessage, tone = PosVisualTone.Error)
                    } else {
                        // Mensaje de acción opcional (Excel, PDF, Email)
                        if (actionMessage != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = actionMessage,
                                            color = Color(0xFF1E40AF),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cerrar",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp).clickable { onDismissActionMessage() },
                                    )
                                }
                            }
                        }

                        // 1. BÚSQUEDA AVANZADA (Colapsable con animación)
                        AnimatedVisibility(
                            visible = searchVisible,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Búsqueda Avanzada",
                                            color = Color(0xFF5D78FF),
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )

                                        Box(
                                            modifier =
                                                Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .size(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFFE2E8F0))
                                                    .clickable { onToggleSearch() },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowUp,
                                                contentDescription = "Ocultar",
                                                tint = Color(0xFF475569),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }

                                    // Fila 1 de Filtros
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        FlowSelectGroup(
                                            label = "Tipo",
                                            selectedOption = filterTipo,
                                            options = listOf("", "FACT", "NC", "ABONO", "ICC"),
                                            onOptionSelected = onFilterTipoChange,
                                            optionLabel = { opt ->
                                                when (opt) {
                                                    "FACT" -> "FACTURA"
                                                    "NC" -> "NOTA DE CRÉDITO"
                                                    "ABONO" -> "ABONO"
                                                    "ICC" -> "INGRESO COBRANZA"
                                                    else -> "TODOS"
                                                }
                                            },
                                            icon = Icons.Default.Description,
                                            modifier = Modifier.weight(1f),
                                            prependWidth = 70.dp,
                                        )

                                        FlowInputGroup(
                                            label = "Número",
                                            value = filterNumero,
                                            onValueChange = onFilterNumeroChange,
                                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                            placeholder = "Buscar número...",
                                            modifier = Modifier.weight(1f),
                                            prependWidth = 75.dp,
                                        )

                                        FlowInputGroup(
                                            label = "N° Fiscal",
                                            value = filterSerieNumero,
                                            onValueChange = onFilterSerieNumeroChange,
                                            icon = Icons.Default.Tag,
                                            placeholder = "Serie / N° Fiscal...",
                                            modifier = Modifier.weight(1f),
                                            prependWidth = 80.dp,
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Fila 2 de Filtros (Fechas)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        FlowMonthYearSelector(
                                            label = "Mes / Año",
                                            selectedMonth = filterMes,
                                            selectedYear = filterAnio,
                                            onMonthSelected = onFilterMesChange,
                                            onYearChanged = onFilterAnioChange,
                                            modifier = Modifier.weight(1.3f),
                                            prependWidth = 85.dp,
                                        )

                                        FlowInputGroup(
                                            label = "Fecha Inicio",
                                            value = filterFechaInicio,
                                            onValueChange = onFilterFechaInicioChange,
                                            icon = Icons.Default.DateRange,
                                            placeholder = "DD/MM/AAAA",
                                            modifier = Modifier.weight(1f),
                                            prependWidth = 90.dp,
                                        )

                                        FlowMonthYearSelector(
                                            label = "Mes / Año",
                                            selectedMonth = filterMesFin,
                                            selectedYear = filterAnioFin,
                                            onMonthSelected = onFilterMesFinChange,
                                            onYearChanged = onFilterAnioFinChange,
                                            modifier = Modifier.weight(1.3f),
                                            prependWidth = 85.dp,
                                        )

                                        FlowInputGroup(
                                            label = "Fecha Fin",
                                            value = filterFechaFin,
                                            onValueChange = onFilterFechaFinChange,
                                            icon = Icons.Default.DateRange,
                                            placeholder = "DD/MM/AAAA",
                                            modifier = Modifier.weight(1f),
                                            prependWidth = 85.dp,
                                        )
                                    }
                                }
                            }
                        }

                        // 2. RESUMEN DEL MOVIMIENTO (#resumenMovimiento)
                        val rawItems = statement?.items.orEmpty()
                        val computedSaldoPendiente =
                            if (statement != null && statement.totalSaldoPendiente > 0.0) {
                                statement.totalSaldoPendiente
                            } else {
                                rawItems.filter {
                                    it.status.equals("Pendiente", ignoreCase = true) || it.status.equals("Disponible", ignoreCase = true)
                                }.sumOf { if (it.montoPendiente > 0.0) it.montoPendiente else it.saldo }
                            }

                        val computedAnticipos =
                            if (statement != null && statement.totalAnticipos > 0.0) {
                                statement.totalAnticipos
                            } else {
                                rawItems.filter {
                                    it.comprobante.equals("ANTICIPO", ignoreCase = true) || it.comprobante.equals("ABONO", ignoreCase = true)
                                }.sumOf { it.credito }
                            }

                        val computedNC =
                            if (statement != null && statement.totalNC > 0.0) {
                                statement.totalNC
                            } else {
                                rawItems.filter {
                                    it.comprobante.equals("NC", ignoreCase = true)
                                }.sumOf { it.credito }
                            }

                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text(
                                text = "Resumen del movimiento",
                                color = Color(0xFF5D78FF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                FlowSummaryBadge(
                                    label = "Saldo Pendiente",
                                    value = formatMoney(computedSaldoPendiente),
                                    icon = Icons.Default.Description,
                                    badgeColor = Color(0xFFF59E0B),
                                    textColor = Color(0xFFD97706),
                                    modifier = Modifier.weight(1f),
                                )

                                FlowSummaryBadge(
                                    label = "Anticipos",
                                    value = formatMoney(computedAnticipos),
                                    icon = Icons.Default.Paid,
                                    badgeColor = Color(0xFF10B981),
                                    textColor = Color(0xFF059669),
                                    modifier = Modifier.weight(1f),
                                )

                                FlowSummaryBadge(
                                    label = "Abonos NC",
                                    value = formatMoney(computedNC),
                                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                    badgeColor = Color(0xFF6366F1),
                                    textColor = Color(0xFF4F46E5),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        // 3. BARRA DE BOTONES DE ACCIÓN (Buscar, Limpiar, Excel, Detalle, PDF, Enviar)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FlowActionButton(
                                    text = "Buscar",
                                    icon = Icons.Default.Search,
                                    color = Color(0xFF2488FA),
                                    onClick = { /* Aplica filtros automáticamente vía state */ },
                                )
                                FlowActionButton(
                                    text = "Limpiar",
                                    icon = Icons.Default.DeleteSweep,
                                    color = Color(0xFFEF4444),
                                    onClick = onClearFilters,
                                )
                                FlowActionButton(
                                    text = "Excel",
                                    icon = Icons.Default.TableChart,
                                    color = Color(0xFF10B981),
                                    onClick = onExportExcel,
                                )
                                FlowActionButton(
                                    text = "Detalle",
                                    icon = Icons.Default.Description,
                                    color = Color(0xFF059669),
                                    onClick = onExportDetailExcel,
                                )
                                FlowActionButton(
                                    text = "PDF",
                                    icon = Icons.Default.PictureAsPdf,
                                    color = Color(0xFFDC2626),
                                    onClick = onExportPdf,
                                )
                                FlowActionButton(
                                    text = "Enviar",
                                    icon = Icons.Default.Email,
                                    color = Color(0xFF0EA5E9),
                                    onClick = onSendEmail,
                                )
                            }
                        }

                        // 4. FILTRO DE ESTATUS SEGMENTADO (#btnGroupEstatus)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FlowStatusFilterChip(
                                    label = "Pendientes",
                                    checked = statusPendiente,
                                    accentColor = Color(0xFFF59E0B),
                                    onToggle = onToggleStatusPendiente,
                                    modifier = Modifier.weight(1f),
                                )
                                FlowStatusFilterChip(
                                    label = "Pagadas",
                                    checked = statusPagado,
                                    accentColor = Color(0xFF10B981),
                                    onToggle = onToggleStatusPagado,
                                    modifier = Modifier.weight(1f),
                                )
                                FlowStatusFilterChip(
                                    label = "Anuladas",
                                    checked = statusAnulado,
                                    accentColor = Color(0xFFEF4444),
                                    onToggle = onToggleStatusAnulado,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        // 5. TABLA DE ESTADO DE CUENTA (#m_table_list_cxc de 12 columnas)
                        val filteredItems =
                            remember(
                                rawItems,
                                filterTipo,
                                filterNumero,
                                filterSerieNumero,
                                filterFechaInicio,
                                filterFechaFin,
                                statusPendiente,
                                statusPagado,
                                statusAnulado,
                            ) {
                                rawItems.filter { item ->
                                    val s = item.status.lowercase(Locale.ROOT)
                                    val isPendiente = s.contains("pendiente") || s.contains("disponible")
                                    val isPagada = s.contains("pagad") || s.contains("consumido")
                                    val isAnulada = s.contains("anulad") || s.contains("vencido")

                                    val passesStatus =
                                        when {
                                            isPendiente -> statusPendiente
                                            isPagada -> statusPagado
                                            isAnulada -> statusAnulado
                                            else -> true
                                        }
                                    if (!passesStatus) return@filter false

                                    if (filterTipo.isNotBlank() && !item.comprobante.equals(filterTipo, ignoreCase = true)) {
                                        return@filter false
                                    }

                                    if (filterNumero.isNotBlank() && !item.comprobanteNumero.contains(filterNumero, ignoreCase = true)) {
                                        return@filter false
                                    }

                                    if (filterSerieNumero.isNotBlank()) {
                                        val matchSerie = item.serieNumero.contains(filterSerieNumero, ignoreCase = true)
                                        val matchFiscal = item.comprobanteFiscal.contains(filterSerieNumero, ignoreCase = true)
                                        if (!matchSerie && !matchFiscal) return@filter false
                                    }

                                    true
                                }
                            }

                        val totalPendiente = filteredItems.sumOf { it.montoPendiente }
                        val totalDebito = filteredItems.sumOf { it.debito }
                        val totalCredito = filteredItems.sumOf { it.credito }
                        val totalSaldo = totalDebito - totalCredito

                        val colFechaEmision = 90.dp
                        val colFechaVenc = 98.dp
                        val colSerieNumero = 105.dp
                        val colTipo = 60.dp
                        val colNumero = 95.dp
                        val colNumeroFiscal = 105.dp
                        val colConcepto = 250.dp
                        val colEstatus = 75.dp
                        val colMontoPendiente = 88.dp
                        val colDebito = 72.dp
                        val colCredito = 78.dp
                        val colSaldo = 72.dp
                        val colSpanGroup = 878.dp
                        val totalTableWidth = 1188.dp

                        val clientDisplayName =
                            client?.let { "${it.code} ${it.fullName}" }
                                ?: statement?.let { "${it.clientCode} ${it.clientName}" }
                                ?: ""

                        val scrollState = rememberScrollState()

                        Card(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .horizontalScroll(scrollState),
                                ) {
                                    Column(modifier = Modifier.widthIn(min = totalTableWidth)) {
                                        // Franja superior de acento amarilla/ámbar de Image 1 (#ffb822 / var(--fl-th-accent))
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(3.dp)
                                                    .background(Color(0xFFF59E0B)),
                                        )

                                        // THEAD de #m_table_list_cxc (12 columnas exactas a Image 1)
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFFBFBFD))
                                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.width(colFechaEmision), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("FECHA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                                                Text("(EMISIÓN)", fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                                            }
                                            Column(modifier = Modifier.width(colFechaVenc), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("FECHA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                                                Text("(VENCIMIENTO)", fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                                            }
                                            Text("SERIE / NÚMERO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.Center, modifier = Modifier.width(colSerieNumero))
                                            Text("TIPO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.Center, modifier = Modifier.width(colTipo))
                                            Text("NÚMERO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.Center, modifier = Modifier.width(colNumero))
                                            Text("NÚMERO (FISCAL)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.Center, modifier = Modifier.width(colNumeroFiscal))
                                            Text("CONCEPTO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.width(colConcepto).padding(start = 6.dp))
                                            Text("ESTATUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.Center, modifier = Modifier.width(colEstatus))
                                            Column(modifier = Modifier.width(colMontoPendiente), horizontalAlignment = Alignment.End) {
                                                Text("MONTO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.End)
                                                Text("(PENDIENTE)", fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B), textAlign = TextAlign.End)
                                            }
                                            Text("DEBITO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.End, modifier = Modifier.width(colDebito))
                                            Text("CRÉDITO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.End, modifier = Modifier.width(colCredito))
                                            Text("SALDO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.End, modifier = Modifier.width(colSaldo))
                                        }

                                        HorizontalDivider(color = Color(0xFFE2E8F0))

                                        if (filteredItems.isEmpty()) {
                                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                                Text("No hay movimientos registrados que coincidan con los filtros.", color = Color(0xFF64748B), fontSize = 13.sp)
                                            }
                                        } else {
                                            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                                // Fila de grupo del cliente (.group-cliente de Image 1)
                                                item {
                                                    Row(
                                                        modifier =
                                                            Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 8.dp, vertical = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Text(
                                                            text = clientDisplayName,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Normal,
                                                            color = Color(0xFF0F172A),
                                                            modifier = Modifier.width(colSpanGroup).padding(start = 4.dp),
                                                        )
                                                        Text(
                                                            text = formatColAmount(totalPendiente, showZero = true),
                                                            fontSize = 12.sp,
                                                            color = Color(0xFF0F172A),
                                                            textAlign = TextAlign.End,
                                                            modifier = Modifier.width(colMontoPendiente),
                                                        )
                                                        Text(
                                                            text = formatColAmount(totalDebito, showZero = true),
                                                            fontSize = 12.sp,
                                                            color = Color(0xFF0F172A),
                                                            textAlign = TextAlign.End,
                                                            modifier = Modifier.width(colDebito),
                                                        )
                                                        Row(
                                                            modifier = Modifier.width(colCredito),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text("-", fontSize = 12.sp, color = Color(0xFF0F172A), modifier = Modifier.padding(start = 2.dp))
                                                            Text(
                                                                text = formatColAmount(totalCredito, showZero = true),
                                                                fontSize = 12.sp,
                                                                color = Color(0xFF0F172A),
                                                                textAlign = TextAlign.End,
                                                                modifier = Modifier.weight(1f),
                                                            )
                                                        }
                                                        Row(
                                                            modifier = Modifier.width(colSaldo),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text("=", fontSize = 12.sp, color = Color(0xFF0F172A), modifier = Modifier.padding(start = 2.dp))
                                                            Text(
                                                                text = formatColAmount(totalSaldo, showZero = true),
                                                                fontSize = 12.sp,
                                                                color = Color(0xFF0F172A),
                                                                textAlign = TextAlign.End,
                                                                modifier = Modifier.weight(1f),
                                                            )
                                                        }
                                                    }
                                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                                }

                                                // Filas de movimientos individuales
                                                items(filteredItems) { item ->
                                                    Row(
                                                        modifier =
                                                            Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Text(
                                                            text = item.fechaEmision.ifBlank { item.fecha },
                                                            fontSize = 11.5.sp,
                                                            color = Color(0xFF334155),
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.width(colFechaEmision),
                                                        )
                                                        Text(
                                                            text = item.fechaVencimiento.ifBlank { "-" },
                                                            fontSize = 11.5.sp,
                                                            color = Color(0xFF64748B),
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.width(colFechaVenc),
                                                        )
                                                        Text(
                                                            text = item.serieNumero,
                                                            fontSize = 11.5.sp,
                                                            color = Color(0xFF0F172A),
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.width(colSerieNumero),
                                                        )
                                                        Text(
                                                            text = item.comprobante,
                                                            fontSize = 11.5.sp,
                                                            color = Color(0xFF0F172A),
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.width(colTipo),
                                                        )
                                                        Text(
                                                            text = item.comprobanteNumero,
                                                            fontSize = 11.5.sp,
                                                            color = Color(0xFF0F172A),
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.width(colNumero),
                                                        )
                                                        Text(
                                                            text = item.comprobanteFiscal.ifBlank { item.serieNumero },
                                                            fontSize = 11.5.sp,
                                                            color = Color(0xFF0F172A),
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.width(colNumeroFiscal),
                                                        )

                                                        // Concepto
                                                        Column(modifier = Modifier.width(colConcepto).padding(horizontal = 6.dp)) {
                                                            Text(
                                                                text = item.concepto,
                                                                fontSize = 11.sp,
                                                                color = Color(0xFF334155),
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                            if (item.observacionFactura.isNotBlank()) {
                                                                Text(
                                                                    text = item.observacionFactura,
                                                                    fontSize = 10.5.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF475569),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                )
                                                            }
                                                        }

                                                        // Estatus (texto plano color #34BFA3 exacto a Image 1)
                                                        Box(modifier = Modifier.width(colEstatus), contentAlignment = Alignment.Center) {
                                                            val s = item.status.lowercase(Locale.ROOT)
                                                            val statusColor =
                                                                when {
                                                                    s.contains("pendiente") || s.contains("disponible") -> Color(0xFFFF9800)
                                                                    s.contains("anulad") || s.contains("vencido") -> Color(0xFFF44336)
                                                                    else -> Color(0xFF34BFA3)
                                                                }
                                                            Text(
                                                                text = item.status.ifBlank { "Pagada" },
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = statusColor,
                                                                textAlign = TextAlign.Center,
                                                            )
                                                        }

                                                        // Monto Pendiente (vacío si es 0.0)
                                                        Text(
                                                            text = formatColAmount(item.montoPendiente),
                                                            fontSize = 11.5.sp,
                                                            color = Color(0xFF0F172A),
                                                            textAlign = TextAlign.End,
                                                            modifier = Modifier.width(colMontoPendiente),
                                                        )

                                                        // Débito (vacío si es 0.0)
                                                        Text(
                                                            text = formatColAmount(item.debito),
                                                            fontSize = 11.5.sp,
                                                            color = Color(0xFF0F172A),
                                                            textAlign = TextAlign.End,
                                                            modifier = Modifier.width(colDebito),
                                                        )

                                                        // Crédito (vacío si es 0.0)
                                                        Text(
                                                            text = formatColAmount(item.credito),
                                                            fontSize = 11.5.sp,
                                                            color = Color(0xFF0F172A),
                                                            textAlign = TextAlign.End,
                                                            modifier = Modifier.width(colCredito),
                                                        )

                                                        // Saldo (muestra 0.00 si es 0.0)
                                                        Text(
                                                            text = formatColAmount(item.saldo, showZero = true),
                                                            fontSize = 11.5.sp,
                                                            color = Color(0xFF0F172A),
                                                            textAlign = TextAlign.End,
                                                            modifier = Modifier.width(colSaldo),
                                                        )
                                                    }
                                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                                }
                                            }

                                            // Línea superior del TOTAL
                                            HorizontalDivider(color = Color(0xFF595D6E), thickness = 1.dp)

                                            // FILA TOTAL (tfoot de Image 1)
                                            Row(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFFFBFBFD))
                                                        .padding(horizontal = 8.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = "TOTAL",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A),
                                                    modifier = Modifier.width(colSpanGroup).padding(end = 12.dp),
                                                    textAlign = TextAlign.End,
                                                )
                                                Text(
                                                    text = formatColAmount(totalPendiente, showZero = true),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A),
                                                    textAlign = TextAlign.End,
                                                    modifier = Modifier.width(colMontoPendiente),
                                                )
                                                Text(
                                                    text = formatColAmount(totalDebito, showZero = true),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A),
                                                    textAlign = TextAlign.End,
                                                    modifier = Modifier.width(colDebito),
                                                )
                                                Text(
                                                    text = formatColAmount(totalCredito, showZero = true),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A),
                                                    textAlign = TextAlign.End,
                                                    modifier = Modifier.width(colCredito),
                                                )
                                                Text(
                                                    text = formatColAmount(totalSaldo, showZero = true),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A),
                                                    textAlign = TextAlign.End,
                                                    modifier = Modifier.width(colSaldo),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modal 1:1 de Crear y Editar Cliente réplica exacta de `#modal-agregar` en `main.html` de Amaxonia.
 * Cuenta con la barra de pestañas verdes (`.m-tabs-line--success`), botones de acción superior (`li.actions`),
 * sub-tarjetas con cabecera gris (`Información Básica`, `Dirección`) y todos los campos con prepend `FlowInputGroup`.
 */
@Composable
private fun ClientWebModal(
    client: Client?,
    onDismiss: () -> Unit,
    onConfirm: (Client) -> Unit,
) {
    key(client?.id?.ifBlank { client.code } ?: "new_client") {
        var selectedTab by remember { mutableIntStateOf(0) }

        // --- TAB 1: DATOS BÁSICOS ---
        var codigo by remember { mutableStateOf(client?.code ?: "") }
        var codTipoCliente by remember { mutableStateOf(client?.clientTypeId?.toString() ?: "2") }
        var tipoContribuyente by remember { mutableStateOf(if (client?.taxpayerType == TaxpayerType.JURIDICO) "2" else "1") }
        var tipoIdentificacionExtranjera by remember { mutableStateOf(client?.foreignAuthTypeId ?: "01") }
        var identificacion by remember { mutableStateOf(client?.identification ?: "") }
        var dv by remember { mutableStateOf(client?.dv ?: "") }
        var nombre by remember { mutableStateOf(client?.name ?: "") }
        var apellido by remember { mutableStateOf(client?.lastName ?: "") }
        var nombreComercial by remember { mutableStateOf(client?.nombreComercial ?: "") }
        var representante by remember { mutableStateOf(client?.representante ?: "") }
        var correo by remember { mutableStateOf(client?.email ?: "") }
        var telefono by remember { mutableStateOf(client?.phone ?: "") }
        var telefono2 by remember { mutableStateOf(client?.phone2 ?: "") }
        var web by remember { mutableStateOf(client?.web ?: "") }
        var fax by remember { mutableStateOf(client?.fax ?: "") }
        var codigoCrm by remember { mutableStateOf(client?.codigoCrm ?: "") }
        var empresaZonaLibre by remember { mutableStateOf(client?.empresaZonaLibre ?: "0") }
        var actividad by remember { mutableStateOf(client?.actividad ?: "") }
        var pedidosOnline by remember { mutableStateOf(client?.pedidosOnline ?: "0") }
        var fnacimiento by remember { mutableStateOf(client?.fnacimiento ?: "") }
        var genero by remember { mutableStateOf(client?.genero ?: "") }
        var clave by remember { mutableStateOf("") }
        var estado by remember { mutableStateOf(if (client?.status != false) "A" else "I") }
        var fechaRegistro by remember { mutableStateOf(client?.fnacimiento?.takeIf { it.isNotBlank() } ?: "17/10/2026") }

        // --- SUB-SECCIÓN: DIRECCIÓN ---
        var direccion by remember { mutableStateOf(client?.address ?: "") }
        var pais by remember { mutableStateOf(client?.countryId?.toString() ?: "170") }
    var paisExtranjero by remember { mutableStateOf("") }
    var direccionNivel1 by remember { mutableStateOf(client?.addressLevel1 ?: "") }
    var direccionNivel2 by remember { mutableStateOf(client?.addressLevel2 ?: "") }
    var direccionNivel3 by remember { mutableStateOf(client?.addressLevel3 ?: "") }
    var direccionCiudad by remember { mutableStateOf(client?.direccionCiudad ?: "") }
    var localidad by remember { mutableStateOf(client?.localidad ?: "") }
    var direccionSector by remember { mutableStateOf(client?.direccionSector ?: "") }
    var direccionUrbanizacion by remember { mutableStateOf(client?.direccionUrbanizacion ?: "") }
    var direccionCalleAv by remember { mutableStateOf(client?.direccionCalleAv ?: "") }
    var direccionEdificioCasa by remember { mutableStateOf(client?.direccionEdificioCasa ?: "") }
    var direccionPiso by remember { mutableStateOf(client?.direccionPiso ?: "") }
    var direccionOficina by remember { mutableStateOf(client?.direccionOficina ?: "") }
    var direccionReferencia by remember { mutableStateOf(client?.direccionReferencia ?: "") }
    var alterna by remember { mutableStateOf(client?.alterna ?: "") }
    var alterna2 by remember { mutableStateOf(client?.alterna2 ?: "") }

    // --- TAB 2: DATOS ADMINISTRATIVOS ---
    var permiteCredito by remember { mutableStateOf(if (client?.permiteCredito == true) "1" else "0") }
    var limite by remember { mutableStateOf(client?.limiteCredito?.toString() ?: "0.0") }
    var dias by remember { mutableStateOf(client?.diasCredito?.toString() ?: "0") }
    var tolerancia by remember { mutableStateOf(client?.tolerancia?.toString() ?: "0") }
    var porcParcial by remember { mutableStateOf(client?.porcParcial?.toString() ?: "0.0") }
    var porcDescuentoGlobal by remember { mutableStateOf(client?.porcDescuentoGlobal?.toString() ?: "0.0") }
    var codTipoPrecio by remember { mutableStateOf(client?.codTipoPrecio?.toString() ?: "2") }
    var cuentaContable by remember { mutableStateOf(client?.cuentaContable ?: "") }
    var codVendedor by remember { mutableStateOf(client?.codVendedor ?: "") }
    var codZona by remember { mutableStateOf(client?.codZona ?: "") }
    var contribuyenteEspecial by remember { mutableStateOf(client?.contribuyenteEspecial ?: "0") }
    var retenidoPorCliente by remember { mutableStateOf(client?.retenidoPorCliente ?: "") }
    var codEntidad by remember { mutableStateOf(client?.codEntidad ?: "1") }
    var aplicaItbms by remember { mutableStateOf(if (client?.aplicaItbms != false) "1" else "0") }
    var lugarEntrega by remember { mutableStateOf(client?.lugarEntrega ?: "") }
    var idSucursal by remember { mutableStateOf("") }
    var neto by remember { mutableStateOf("0.00") }
    var total by remember { mutableStateOf("0.00") }
    var cuotaMensual by remember { mutableStateOf("0.00") }

    // --- TAB 5: PUNTOS ---
    var clienteFidelizacion by remember { mutableStateOf(client?.clienteFidelizacion == 1) }
    var clienteFidelizacionSucursal by remember { mutableStateOf(client?.clienteFidelizacionSucursal?.toString() ?: "0") }
    var clienteFidelizacionNivel by remember { mutableStateOf(client?.clienteFidelizacionNivel?.toString() ?: "0") }
    var clienteFidelizacionFecha by remember { mutableStateOf(client?.clienteFidelizacionFecha ?: "") }
    var clienteFidelizacionPuntos by remember { mutableStateOf(client?.clienteFidelizacionPuntos?.toString() ?: "0") }

    val tabs = listOf(
        "Datos Básicos" to Icons.Default.Person,
        "Datos Administrativos" to Icons.Default.Assessment,
        "Datos Comerciales" to Icons.Default.Store,
        "Documentos" to Icons.Default.Description,
        "Puntos" to Icons.Default.Star,
        "Sucursales" to Icons.Default.Business,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F5F8)),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ══════════════════════════════════════════════════════════════
                // BARRA DE PESTAÑAS Y ACCIONES (.nav-tabs.m-tabs-line--success)
                // ══════════════════════════════════════════════════════════════
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)))
                            .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Pestañas
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.White,
                        contentColor = Color(0xFF10B981),
                        modifier = Modifier.weight(1f),
                    ) {
                        tabs.forEachIndexed { index, (title, icon) ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title, fontSize = 12.5.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium) },
                                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp)) },
                            )
                        }
                    }

                    // ACCIONES A LA DERECHA (li.actions: Guardar verde, Cancelar rojo)
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Botón Guardar (#btnGuardar)
                        Box(
                            modifier =
                                Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF10B981))
                                    .clickable {
                                        if (identificacion.isNotBlank() && nombre.isNotBlank()) {
                                            onConfirm(
                                                Client(
                                                    id = client?.id ?: "",
                                                    code = codigo,
                                                    identification = identificacion.trim(),
                                                    dv = dv.trim(),
                                                    name = nombre.trim(),
                                                    lastName = apellido.trim(),
                                                    nombreComercial = nombreComercial.trim(),
                                                    representante = representante.trim(),
                                                    phone = telefono.trim(),
                                                    phone2 = telefono2.trim(),
                                                    email = correo.trim(),
                                                    web = web.trim(),
                                                    fax = fax.trim(),
                                                    codigoCrm = codigoCrm.trim(),
                                                    actividad = actividad.trim(),
                                                    empresaZonaLibre = empresaZonaLibre,
                                                    pedidosOnline = pedidosOnline,
                                                    fnacimiento = fnacimiento.trim(),
                                                    genero = genero.trim(),
                                                    status = estado == "A",
                                                    address = direccion.trim(),
                                                    addressLevel1 = direccionNivel1.trim(),
                                                    addressLevel2 = direccionNivel2.trim(),
                                                    addressLevel3 = direccionNivel3.trim(),
                                                    direccionCiudad = direccionCiudad.trim(),
                                                    localidad = localidad.trim(),
                                                    direccionSector = direccionSector.trim(),
                                                    direccionUrbanizacion = direccionUrbanizacion.trim(),
                                                    direccionCalleAv = direccionCalleAv.trim(),
                                                    direccionEdificioCasa = direccionEdificioCasa.trim(),
                                                    direccionPiso = direccionPiso.trim(),
                                                    direccionOficina = direccionOficina.trim(),
                                                    direccionReferencia = direccionReferencia.trim(),
                                                    alterna = alterna.trim(),
                                                    alterna2 = alterna2.trim(),
                                                    permiteCredito = permiteCredito == "1",
                                                    limiteCredito = limite.toDoubleOrNull() ?: 0.0,
                                                    diasCredito = dias.toIntOrNull() ?: 0,
                                                    tolerancia = tolerancia.toIntOrNull() ?: 0,
                                                    porcParcial = porcParcial.toDoubleOrNull() ?: 0.0,
                                                    porcDescuentoGlobal = porcDescuentoGlobal.toDoubleOrNull() ?: 0.0,
                                                    codTipoPrecio = codTipoPrecio.toIntOrNull() ?: 2,
                                                    cuentaContable = cuentaContable.trim(),
                                                    codVendedor = codVendedor.trim(),
                                                    codZona = codZona.trim(),
                                                    lugarEntrega = lugarEntrega.trim(),
                                                    aplicaItbms = aplicaItbms == "1",
                                                    contribuyenteEspecial = contribuyenteEspecial,
                                                    clienteFidelizacion = if (clienteFidelizacion) 1 else 0,
                                                    clienteFidelizacionSucursal = clienteFidelizacionSucursal.toIntOrNull() ?: 0,
                                                    clienteFidelizacionNivel = clienteFidelizacionNivel.toIntOrNull() ?: 0,
                                                    clienteFidelizacionPuntos = clienteFidelizacionPuntos.toDoubleOrNull() ?: 0.0,
                                                    clienteFidelizacionFecha = clienteFidelizacionFecha.trim(),
                                                    taxpayerType = if (tipoContribuyente == "2") TaxpayerType.JURIDICO else TaxpayerType.NATURAL,
                                                    clientTypeId = codTipoCliente.toIntOrNull() ?: 2,
                                                    countryId = pais.toIntOrNull() ?: 170,
                                                    foreignAuthTypeId = if (codTipoCliente == "4") tipoIdentificacionExtranjera else null,
                                                    saldo = client?.saldo ?: 0.0,
                                                ),
                                            )
                                        }
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Guardar", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        // Botón Cancelar (cerrar rojo)
                        Box(
                            modifier =
                                Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEF4444))
                                    .clickable { onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // ══════════════════════════════════════════════════════════════
                // CONTENIDO DE LA PESTAÑA SELECCIONADA (Scrollable)
                // ══════════════════════════════════════════════════════════════
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (selectedTab) {
                        0 -> {
                            // ────────────────────────────────────────────────────────
                            // SUB-TARJETA: Información de Básica
                            // ────────────────────────────────────────────────────────
                            FlowSectionPortlet(title = "Información de Básica") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // Columna Izquierda: Rejilla de Inputs con Prepend Flow
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        // Fila 1
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            FlowInputGroup("Código", codigo, { codigo = it }, icon = Icons.Default.Badge, modifier = Modifier.weight(1f), placeholder = "Autogenerado")
                                            FlowSelectGroup(
                                                "Tipo Cliente",
                                                codTipoCliente,
                                                listOf("1", "2", "3", "4"),
                                                { codTipoCliente = it },
                                                icon = Icons.Default.Person,
                                                optionLabel = { if (it == "1") "CONSUMIDOR FINAL" else if (it == "2") "CONTRIBUYENTE" else if (it == "3") "GOBIERNO" else "EXTRANJERO" },
                                                modifier = Modifier.weight(1f),
                                            )
                                            FlowSelectGroup(
                                                "Contribuyente",
                                                tipoContribuyente,
                                                listOf("1", "2"),
                                                { tipoContribuyente = it },
                                                icon = Icons.Default.AccountBox,
                                                optionLabel = { if (it == "1") "NATURAL" else "JURÍDICO" },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }

                                        // Fila 2
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            FlowInputGroup("RUC / Cédula", identificacion, { identificacion = it }, icon = Icons.Default.Description, modifier = Modifier.weight(2f), placeholder = "Presione Enter para validar")
                                            FlowInputGroup("DV", dv, { dv = it }, icon = Icons.Default.Badge, modifier = Modifier.weight(1f), placeholder = "DV", prependWidth = 60.dp)
                                            FlowInputGroup(if (tipoContribuyente == "2") "Razón Social" else "Nombre", nombre, { nombre = it }, icon = Icons.Default.Person, modifier = Modifier.weight(2f))
                                        }

                                        // Fila 3
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            if (tipoContribuyente == "1") {
                                                FlowInputGroup("Apellido", apellido, { apellido = it }, icon = Icons.Default.Person, modifier = Modifier.weight(1f))
                                            } else {
                                                FlowInputGroup("N. Comercial", nombreComercial, { nombreComercial = it }, icon = Icons.Default.Store, modifier = Modifier.weight(1f))
                                                FlowInputGroup("Representante", representante, { representante = it }, icon = Icons.Default.AccountBox, modifier = Modifier.weight(1f))
                                            }
                                            FlowInputGroup("Correo", correo, { correo = it }, icon = Icons.Default.Email, modifier = Modifier.weight(1.5f))
                                        }

                                        // Fila 4
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            FlowInputGroup("Teléfono", telefono, { telefono = it }, icon = Icons.Default.Phone, modifier = Modifier.weight(1f))
                                            FlowInputGroup("Tlf. envios", telefono2, { telefono2 = it }, icon = Icons.Default.Phone, modifier = Modifier.weight(1f))
                                            FlowInputGroup("Web", web, { web = it }, icon = Icons.Default.Home, modifier = Modifier.weight(1f))
                                        }

                                        // Fila 5
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            FlowInputGroup("FAX", fax, { fax = it }, icon = Icons.Default.Description, modifier = Modifier.weight(1f))
                                            FlowInputGroup("CRM", codigoCrm, { codigoCrm = it }, icon = Icons.Default.Badge, modifier = Modifier.weight(1f))
                                            FlowSelectGroup("Zona Libre", empresaZonaLibre, listOf("0", "1"), { empresaZonaLibre = it }, icon = Icons.Default.Business, optionLabel = { if (it == "1") "SÍ" else "NO" }, modifier = Modifier.weight(1f))
                                        }

                                        // Fila 6
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            FlowInputGroup("Actividad", actividad, { actividad = it }, icon = Icons.Default.Business, modifier = Modifier.weight(1f))
                                            FlowSelectGroup("Pedidos", pedidosOnline, listOf("0", "1"), { pedidosOnline = it }, icon = Icons.Default.Receipt, optionLabel = { if (it == "1") "SÍ" else "NO" }, modifier = Modifier.weight(1f))
                                            FlowInputGroup("Nacimiento", fnacimiento, { fnacimiento = it }, icon = Icons.Default.DateRange, modifier = Modifier.weight(1f), placeholder = "DD/MM/AAAA")
                                        }

                                        // Fila 7
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            FlowSelectGroup("Género", genero, listOf("", "M", "F"), { genero = it }, icon = Icons.Default.Person, optionLabel = { if (it == "M") "MASCULINO" else if (it == "F") "FEMENINO" else "SELECCIONE" }, modifier = Modifier.weight(1f))
                                            FlowInputGroup(
                                                "Clave",
                                                clave,
                                                { clave = it },
                                                icon = Icons.Default.Lock,
                                                modifier = Modifier.weight(1.5f),
                                                placeholder = "Clave Portal",
                                                trailingContent = {
                                                    Box(
                                                        modifier =
                                                            Modifier
                                                                .size(44.dp)
                                                                .background(Color(0xFF2488FA))
                                                                .clickable { clave = (1000..9999).random().toString() },
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Icon(Icons.Default.VpnKey, contentDescription = "Generar", tint = Color.White, modifier = Modifier.size(18.dp))
                                                    }
                                                },
                                            )
                                            FlowSelectGroup("Estatus", estado, listOf("A", "I"), { estado = it }, icon = Icons.Default.Assessment, optionLabel = { if (it == "A") "ACTIVO" else "INACTIVO" }, modifier = Modifier.weight(1f))
                                        }
                                    }

                                    // Columna Derecha: Foto del Cliente
                                    Column(
                                        modifier = Modifier.width(180.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text("Foto Cliente", color = Color(0xFF00BCD4), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(160.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(12.dp))
                                                    .background(Color(0xFFF8FAFC)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(48.dp))
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        FlowInputGroup("Registro", fechaRegistro, { fechaRegistro = it }, icon = Icons.Default.DateRange, prependWidth = 70.dp, readOnly = true)
                                    }
                                }
                            }

                            // ────────────────────────────────────────────────────────
                            // SUB-TARJETA: Dirección
                            // ────────────────────────────────────────────────────────
                            FlowSectionPortlet(title = "Dirección") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FlowInputGroup("Dirección", direccion, { direccion = it }, icon = Icons.Default.Place, placeholder = "Dirección completa")

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FlowSelectGroup("País", pais, listOf("170", "238", "1"), { pais = it }, icon = Icons.Default.LocationCity, optionLabel = { if (it == "170") "PANAMÁ" else if (it == "238") "VENEZUELA" else "OTRO" }, modifier = Modifier.weight(1f))
                                        FlowSelectGroup("País Extranjero", paisExtranjero, listOf("", "170", "238", "1"), { paisExtranjero = it }, icon = Icons.Default.LocationCity, optionLabel = { if (it == "170") "PANAMÁ" else if (it == "238") "VENEZUELA" else "NINGUNO" }, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Provincia", direccionNivel1, { direccionNivel1 = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FlowInputGroup("Distrito", direccionNivel2, { direccionNivel2 = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Corregimiento", direccionNivel3, { direccionNivel3 = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Ciudad", direccionCiudad, { direccionCiudad = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FlowInputGroup("Localidad", localidad, { localidad = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Sector", direccionSector, { direccionSector = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Urb.", direccionUrbanizacion, { direccionUrbanizacion = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FlowInputGroup("Calle / Av.", direccionCalleAv, { direccionCalleAv = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Casa / Edif.", direccionEdificioCasa, { direccionEdificioCasa = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Piso", direccionPiso, { direccionPiso = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FlowInputGroup("Oficina", direccionOficina, { direccionOficina = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Ref.", direccionReferencia, { direccionReferencia = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Alterna", alterna, { alterna = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        1 -> {
                            // ────────────────────────────────────────────────────────
                            // TAB 2: DATOS ADMINISTRATIVOS
                            // ────────────────────────────────────────────────────────
                            FlowSectionPortlet(title = "Datos Administrativos y Condiciones de Venta") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FlowSelectGroup("Créditos", permiteCredito, listOf("0", "1"), { permiteCredito = it }, icon = Icons.Default.AccountBalance, optionLabel = { if (it == "1") "SÍ" else "NO" }, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Límite", limite, { limite = it }, icon = Icons.Default.Description, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Días", dias, { dias = it }, icon = Icons.Default.DateRange, modifier = Modifier.weight(1f))
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FlowInputGroup("Tolerancia", tolerancia, { tolerancia = it }, icon = Icons.Default.DateRange, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Desc. Parcial", porcParcial, { porcParcial = it }, icon = Icons.Default.Assessment, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Desc. Global", porcDescuentoGlobal, { porcDescuentoGlobal = it }, icon = Icons.Default.Assessment, modifier = Modifier.weight(1f))
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FlowInputGroup("Lista", codTipoPrecio, { codTipoPrecio = it }, icon = Icons.Default.Description, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Cuenta CXC", cuentaContable, { cuentaContable = it }, icon = Icons.Default.AccountBalance, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Vendedor", codVendedor, { codVendedor = it }, icon = Icons.Default.Person, modifier = Modifier.weight(1f))
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FlowInputGroup("Zona", codZona, { codZona = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1f))
                                        FlowSelectGroup("Contrib. Especial", contribuyenteEspecial, listOf("0", "1"), { contribuyenteEspecial = it }, icon = Icons.Default.Description, optionLabel = { if (it == "1") "SÍ" else "NO" }, modifier = Modifier.weight(1f))
                                        FlowSelectGroup("Aplica ITBMS", aplicaItbms, listOf("1", "0"), { aplicaItbms = it }, icon = Icons.Default.Receipt, optionLabel = { if (it == "1") "SÍ" else "NO" }, modifier = Modifier.weight(1f))
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FlowInputGroup("Entrega", lugarEntrega, { lugarEntrega = it }, icon = Icons.Default.Place, modifier = Modifier.weight(1.5f))
                                        FlowInputGroup("Cuota", cuotaMensual, { cuotaMensual = it }, icon = Icons.Default.Description, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Total", total, { total = it }, icon = Icons.Default.Description, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        2 -> {
                            // ────────────────────────────────────────────────────────
                            // TAB 3: DATOS COMERCIALES (Bancos y Contactos)
                            // ────────────────────────────────────────────────────────
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                // Información Bancaria
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Información Bancaria", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                        FlowInputGroup("Banco", "", {}, icon = Icons.Default.AccountBalance, placeholder = "Nombre del Banco")
                                        FlowInputGroup("N° Cuenta", "", {}, icon = Icons.Default.Receipt, placeholder = "Número de Cuenta")
                                        FlowInputGroup("Tipo Cta", "", {}, icon = Icons.Default.Description, placeholder = "Corriente / Ahorro")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = {}, shape = RoundedCornerShape(8.dp)) { Text("Limpiar") }
                                            Button(onClick = {}, shape = RoundedCornerShape(8.dp)) { Text("Agregar") }
                                        }
                                    }
                                }

                                // Información de Contacto
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Información de Contacto", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
                                        FlowInputGroup("Contacto", "", {}, icon = Icons.Default.Person, placeholder = "Nombre Contacto")
                                        FlowInputGroup("Área / Cargo", "", {}, icon = Icons.Default.Badge, placeholder = "Gerencia / Compras")
                                        FlowInputGroup("Teléfono", "", {}, icon = Icons.Default.Phone, placeholder = "Teléfono")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = {}, shape = RoundedCornerShape(8.dp)) { Text("Limpiar") }
                                            Button(onClick = {}, shape = RoundedCornerShape(8.dp)) { Text("Agregar") }
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // ────────────────────────────────────────────────────────
                            // TAB 4: DOCUMENTOS
                            // ────────────────────────────────────────────────────────
                            FlowSectionPortlet(title = "Documentos Digitales Adjuntos") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(260.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(onClick = {}, shape = RoundedCornerShape(8.dp)) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Seleccionar documento a adjuntar...")
                                        }
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f)
                                                    .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(8.dp))
                                                    .padding(12.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("No hay documentos adjuntos.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                        }
                                    }

                                    Box(
                                        modifier =
                                            Modifier
                                                .width(240.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFEEEEEE))
                                                .border(BorderStroke(1.dp, Color(0xFFCBD5E1)), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("Vista previa", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        4 -> {
                            // ────────────────────────────────────────────────────────
                            // TAB 5: PUNTOS / FIDELIZACIÓN
                            // ────────────────────────────────────────────────────────
                            FlowSectionPortlet(title = "Cliente Frecuente / Puntos") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = if (clienteFidelizacion) "Cliente Frecuente: SÍ" else "Cliente Frecuente: NO",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A),
                                        )
                                        Switch(checked = clienteFidelizacion, onCheckedChange = { clienteFidelizacion = it })
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FlowInputGroup("Sucursal", clienteFidelizacionSucursal, { clienteFidelizacionSucursal = it }, icon = Icons.Default.Business, modifier = Modifier.weight(1f))
                                        FlowInputGroup("Nivel", clienteFidelizacionNivel, { clienteFidelizacionNivel = it }, icon = Icons.Default.Star, modifier = Modifier.weight(1f))
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FlowInputGroup("Fecha Fideliz.", clienteFidelizacionFecha, { clienteFidelizacionFecha = it }, icon = Icons.Default.DateRange, modifier = Modifier.weight(1f), placeholder = "DD/MM/AAAA")
                                        FlowInputGroup("Puntos", clienteFidelizacionPuntos, { clienteFidelizacionPuntos = it }, icon = Icons.Default.Receipt, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        5 -> {
                            // ────────────────────────────────────────────────────────
                            // TAB 6: SUCURSALES
                            // ────────────────────────────────────────────────────────
                            FlowSectionPortlet(title = "Sucursales del Cliente") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FlowInputGroup("Sucursal *", "", {}, icon = Icons.Default.Business, modifier = Modifier.weight(1f), placeholder = "Nombre Sucursal")
                                        FlowInputGroup("Contacto", "", {}, icon = Icons.Default.Person, modifier = Modifier.weight(1f), placeholder = "Nombre Contacto")
                                        FlowInputGroup("Teléfono", "", {}, icon = Icons.Default.Phone, modifier = Modifier.weight(1f), placeholder = "Teléfono")
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {}, shape = RoundedCornerShape(8.dp)) { Text("Limpiar") }
                                        Button(onClick = {}, shape = RoundedCornerShape(8.dp)) { Text("Guardar Sucursal") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
