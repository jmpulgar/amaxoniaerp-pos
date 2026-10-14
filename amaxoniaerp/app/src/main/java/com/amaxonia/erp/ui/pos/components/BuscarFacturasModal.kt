package com.amaxonia.erp.ui.pos.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amaxonia.erp.domain.model.SavedInvoiceFilterTab
import com.amaxonia.erp.domain.model.SavedInvoiceSummary
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.util.forceShowKeyboardOnTouch
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

// --- Tokens Oficiales Flow ERP (1:1 Web administrativo.amaxoniaerp.com / flow-tokens.css) ---
private val FlowBrandBlue = Color(0xFF2488FA)
private val FlowBrandNavy = Color(0xFF201B82)
private val FlowBrandCyan = Color(0xFF00C5DC)
private val FlowSuccessEmerald = Color(0xFF10B981)
private val FlowDangerRose = Color(0xFFF43F5E)
private val FlowTableBgHeader = Color(0xFFF8FAFC)
private val FlowTableBorder = Color(0xFFE2E8F0)
private val FlowTableTextDark = Color(0xFF1E293B)
private val FlowTableTextMuted = Color(0xFF64748B)
private val FlowRowSelectedBg = Color(0xFFEAF3FF)
private val FlowSegmentedContainerBg = Color(0xFFF1F5F9)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BuscarFacturasModal(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    invoices: List<SavedInvoiceSummary>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilter: SavedInvoiceFilterTab,
    onFilterChange: (SavedInvoiceFilterTab) -> Unit,
    onRefresh: () -> Unit,
    onSelectRestore: (SavedInvoiceSummary) -> Unit,
    showConfirmRestoreDialog: Boolean = false,
    invoicePendingRestore: SavedInvoiceSummary? = null,
    onConfirmRestore: () -> Unit = {},
    onDismissConfirmRestore: () -> Unit = {},
) {
    if (!isOpen) return

    var selectedInvoice by remember { mutableStateOf<SavedInvoiceSummary?>(null) }
    var currentPage by remember { mutableIntStateOf(1) }

    // Estados de filtros web (factura/listado-modal)
    var filtroMes by remember { mutableStateOf("") }
    var filtroAnio by remember { mutableStateOf("") }
    var filtroFechaInicio by remember { mutableStateOf("") }
    var filtroMesFin by remember { mutableStateOf("") }
    var filtroAnioFin by remember { mutableStateOf("") }
    var filtroFechaFin by remember { mutableStateOf("") }
    var filtroCliente by remember { mutableStateOf("") }
    var filtroNumero by remember { mutableStateOf("") }
    var filtroFiscal by remember { mutableStateOf("") }
    var filtroTodasSucursales by remember { mutableStateOf(false) }

    val pageSize = 12

    // Filtrar facturas según búsqueda y filtros web
    val filteredInvoices = remember(
        invoices,
        searchQuery,
        filtroMes,
        filtroAnio,
        filtroFechaInicio,
        filtroMesFin,
        filtroAnioFin,
        filtroFechaFin,
        filtroCliente,
        filtroNumero,
        filtroFiscal,
        filtroTodasSucursales,
    ) {
        val query = searchQuery.trim().lowercase(Locale.ROOT)
        val numQuery = filtroNumero.trim().lowercase(Locale.ROOT)
        val fiscalQuery = filtroFiscal.trim().lowercase(Locale.ROOT)
        val cliQuery = filtroCliente.trim().lowercase(Locale.ROOT)
        val inicioQuery = filtroFechaInicio.trim()
        val finQuery = filtroFechaFin.trim()

        invoices.filter { inv ->
            // Filtro por texto libre superior
            val matchesQuery = query.isEmpty() ||
                inv.code.lowercase(Locale.ROOT).contains(query) ||
                inv.codigoFiscal.lowercase(Locale.ROOT).contains(query) ||
                inv.clientName.lowercase(Locale.ROOT).contains(query) ||
                inv.clienteIdentificacion.lowercase(Locale.ROOT).contains(query) ||
                inv.sucursal.lowercase(Locale.ROOT).contains(query) ||
                inv.caja.lowercase(Locale.ROOT).contains(query) ||
                inv.vendedor.lowercase(Locale.ROOT).contains(query) ||
                inv.total.toString().contains(query)

            // Filtro por número
            val matchesNumero = numQuery.isEmpty() || inv.code.lowercase(Locale.ROOT).contains(numQuery)

            // Filtro por N° Fiscal
            val matchesFiscal = fiscalQuery.isEmpty() || inv.codigoFiscal.lowercase(Locale.ROOT).contains(fiscalQuery)

            // Filtro por cliente
            val matchesCliente = cliQuery.isEmpty() ||
                inv.clientName.lowercase(Locale.ROOT).contains(cliQuery) ||
                inv.clienteIdentificacion.lowercase(Locale.ROOT).contains(cliQuery)

            // Filtro por mes / año inicio
            val matchesMes = filtroMes.isEmpty() || inv.date.contains("/$filtroMes/") || inv.date.contains("-$filtroMes-")
            val matchesAnio = filtroAnio.isEmpty() || inv.date.contains(filtroAnio)

            // Filtro por mes / año fin si están fijados
            val matchesMesFin = filtroMesFin.isEmpty() || inv.date.contains("/$filtroMesFin/") || inv.date.contains("-$filtroMesFin-")
            val matchesAnioFin = filtroAnioFin.isEmpty() || inv.date.contains(filtroAnioFin)

            // Filtro por fecha inicio / fin básica
            val matchesInicio = inicioQuery.isEmpty() || inv.date.contains(inicioQuery)
            val matchesFin = finQuery.isEmpty() || inv.date.contains(finQuery)

            matchesQuery && matchesNumero && matchesFiscal && matchesCliente &&
                matchesMes && matchesAnio && matchesMesFin && matchesAnioFin &&
                matchesInicio && matchesFin
        }
    }

    LaunchedEffect(filteredInvoices.size) {
        currentPage = 1
    }

    val totalPages = max(1, ceil(filteredInvoices.size.toDouble() / pageSize.toDouble()).toInt())
    val paginatedInvoices = remember(filteredInvoices, currentPage) {
        val start = (currentPage - 1) * pageSize
        filteredInvoices.drop(start).take(pageSize)
    }

    val limpiarFiltros = {
        onSearchQueryChange("")
        filtroMes = ""
        filtroAnio = ""
        filtroFechaInicio = ""
        filtroMesFin = ""
        filtroAnioFin = ""
        filtroFechaFin = ""
        filtroCliente = ""
        filtroNumero = ""
        filtroFiscal = ""
        filtroTodasSucursales = false
        onRefresh()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val isLandscape = isLandscape()

        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.96f else 0.99f)
                .fillMaxHeight(if (isLandscape) 0.94f else 0.98f)
                .clip(RoundedCornerShape(16.dp)),
            color = Color.White,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                // =========================================================================
                // 1. BARRA DE BÚSQUEDA 1:1 FLOW ERP (.producto-buscar con botones append)
                // =========================================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(1.5.dp, FlowBrandCyan, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Buscar Factura",
                                color = FlowTableTextMuted,
                                fontSize = 13.sp,
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = FlowTableTextDark,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            cursorBrush = SolidColor(FlowBrandBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .forceShowKeyboardOnTouch(),
                        )
                    }

                    // Botonera integrada oficial Flow ERP (.btn-accent, .btn-success, .btn-danger)
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 1. Buscar (Lupa)
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .fillMaxHeight()
                                .background(FlowBrandNavy)
                                .clickable { onRefresh() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp),
                            )
                        }

                        // 2. Limpiar (Backspace)
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .fillMaxHeight()
                                .background(FlowBrandCyan)
                                .clickable { limpiarFiltros() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Limpiar",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                        }

                        // 3. Aceptar (Check verde)
                        Box(
                            modifier = Modifier
                                .width(46.dp)
                                .fillMaxHeight()
                                .background(FlowSuccessEmerald)
                                .clickable {
                                    val target = selectedInvoice ?: paginatedInvoices.firstOrNull()
                                    if (target != null) {
                                        onSelectRestore(target)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Aceptar",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // 4. Cerrar (X roja)
                        Box(
                            modifier = Modifier
                                .width(46.dp)
                                .fillMaxHeight()
                                .background(FlowDangerRose)
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // =========================================================================
                // 2. PANEL DE FILTROS 1:1 CON LA WEB (factura/listado-modal)
                // =========================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Columna Izquierda: Fechas
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Mes / Año Inicio
                            WebMonthYearFilter(
                                label = "Mes / Año",
                                selectedMonth = filtroMes,
                                onMonthChange = { filtroMes = it },
                                yearValue = filtroAnio,
                                onYearChange = { filtroAnio = it },
                                modifier = Modifier.weight(1.3f),
                            )

                            // Fecha Inicio
                            WebTextInputFilter(
                                label = "Fecha Inicio",
                                icon = Icons.Default.CalendarMonth,
                                value = filtroFechaInicio,
                                onValueChange = { filtroFechaInicio = it },
                                placeholder = "DD/MM/AAAA",
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Mes / Año Fin
                            WebMonthYearFilter(
                                label = "Mes / Año Fin",
                                selectedMonth = filtroMesFin,
                                onMonthChange = { filtroMesFin = it },
                                yearValue = filtroAnioFin,
                                onYearChange = { filtroAnioFin = it },
                                modifier = Modifier.weight(1.3f),
                            )

                            // Fecha Fin
                            WebTextInputFilter(
                                label = "Fecha Fin",
                                icon = Icons.Default.CalendarMonth,
                                value = filtroFechaFin,
                                onValueChange = { filtroFechaFin = it },
                                placeholder = "DD/MM/AAAA",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    // Columna Derecha: Cliente, Número, N° Fiscal, Sucursales
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Cliente
                        WebTextInputFilter(
                            label = "Cliente",
                            icon = Icons.Default.AccountCircle,
                            value = filtroCliente,
                            onValueChange = { filtroCliente = it },
                            placeholder = "Buscar cliente o RIF...",
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Número
                            WebTextInputFilter(
                                label = "Número",
                                icon = Icons.Default.Numbers,
                                value = filtroNumero,
                                onValueChange = { filtroNumero = it },
                                placeholder = "N° Factura",
                                modifier = Modifier.weight(1f),
                            )

                            // N° Fiscal
                            WebTextInputFilter(
                                label = "N° Fiscal",
                                icon = Icons.Default.Pin,
                                value = filtroFiscal,
                                onValueChange = { filtroFiscal = it },
                                placeholder = "Fiscal",
                                modifier = Modifier.weight(1f),
                            )

                            // Checkbox Todas las Sucursales
                            Row(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(34.dp)
                                    .background(FlowSegmentedContainerBg, RoundedCornerShape(6.dp))
                                    .border(1.dp, FlowTableBorder, RoundedCornerShape(6.dp))
                                    .clickable { filtroTodasSucursales = !filtroTodasSucursales }
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = filtroTodasSucursales,
                                    onCheckedChange = { filtroTodasSucursales = it },
                                    colors = CheckboxDefaults.colors(checkedColor = FlowBrandBlue),
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Todas Sucursales",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FlowTableTextDark,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // =========================================================================
                // 3. TABLA DE FACTURAS (#m_table_list_modal_factura) CON LAS 8 COLUMNAS EXACTAS
                // Número | N° Fiscal | Fecha | Cliente | Total | Sucursal | Caja | Vendedor
                // =========================================================================
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, FlowTableBorder, RoundedCornerShape(12.dp))
                        .background(Color.White),
                ) {
                    if (isLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(color = FlowBrandBlue, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Cargando facturas guardadas...", fontSize = 12.sp, color = FlowTableTextMuted)
                        }
                    } else if (paginatedInvoices.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "No se encontraron facturas con los filtros indicados",
                                fontSize = 13.sp,
                                color = FlowTableTextMuted,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val tableWidth = if (maxWidth > 980.dp) maxWidth else 980.dp
                            val horizontalScrollState = rememberScrollState()

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .horizontalScroll(horizontalScrollState),
                            ) {
                                // Cabecera Oficial Flow ERP (#F8FAFC)
                                Row(
                                    modifier = Modifier
                                        .width(tableWidth)
                                        .height(36.dp)
                                        .background(FlowTableBgHeader)
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(modifier = Modifier.width(4.dp))
                                    TableHeaderCell("NÚMERO", width = 110.dp, textAlign = TextAlign.Center)
                                    TableHeaderCell("N° FISCAL", width = 110.dp, textAlign = TextAlign.Center)
                                    TableHeaderCell("FECHA", width = 100.dp, textAlign = TextAlign.Center)
                                    TableHeaderCell("CLIENTE", modifier = Modifier.weight(1f).widthIn(min = 220.dp), textAlign = TextAlign.Start)
                                    TableHeaderCell("TOTAL", width = 110.dp, textAlign = TextAlign.End)
                                    TableHeaderCell("SUCURSAL", width = 110.dp, textAlign = TextAlign.Center)
                                    TableHeaderCell("CAJA", width = 75.dp, textAlign = TextAlign.Center)
                                    TableHeaderCell("VENDEDOR", width = 130.dp, textAlign = TextAlign.Start)
                                }

                                HorizontalDivider(color = FlowTableBorder, thickness = 1.dp)

                                // Filas de Datos (Exactamente las 8 columnas)
                                LazyColumn(
                                    modifier = Modifier
                                        .width(tableWidth)
                                        .fillMaxHeight(),
                                ) {
                                    itemsIndexed(paginatedInvoices, key = { _, inv -> inv.id }) { index, inv ->
                                        val isSelected = selectedInvoice?.id == inv.id
                                        val rowBg = when {
                                            isSelected -> FlowRowSelectedBg
                                            index % 2 == 1 -> Color(0xFFFAFBFD)
                                            else -> Color.White
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(46.dp)
                                                .background(rowBg)
                                                .combinedClickable(
                                                    onClick = { selectedInvoice = inv },
                                                    onDoubleClick = {
                                                        selectedInvoice = inv
                                                        onSelectRestore(inv)
                                                    },
                                                )
                                                .padding(horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            // Franja indicadora Flow ERP en el borde izquierdo de la fila seleccionada
                                            Box(
                                                modifier = Modifier
                                                    .width(4.dp)
                                                    .fillMaxHeight()
                                                    .padding(vertical = 4.dp),
                                                contentAlignment = Alignment.CenterStart,
                                            ) {
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(3.5.dp)
                                                            .fillMaxHeight()
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .background(FlowBrandBlue),
                                                    )
                                                }
                                            }

                                            // 1. Número
                                            Text(
                                                text = inv.code,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FlowBrandNavy,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.width(110.dp),
                                            )

                                            // 2. N° Fiscal
                                            Text(
                                                text = inv.codigoFiscal.ifBlank { "-" },
                                                fontSize = 11.5.sp,
                                                color = FlowTableTextDark,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.width(110.dp),
                                            )

                                            // 3. Fecha
                                            Text(
                                                text = inv.date.ifBlank { "-" },
                                                fontSize = 11.sp,
                                                color = FlowTableTextMuted,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                modifier = Modifier.width(100.dp),
                                            )

                                            // 4. Cliente (Identificación + Nombre)
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .widthIn(min = 220.dp)
                                                    .padding(horizontal = 6.dp),
                                            ) {
                                                if (inv.clienteIdentificacion.isNotBlank()) {
                                                    Text(
                                                        text = inv.clienteIdentificacion,
                                                        fontSize = 9.5.sp,
                                                        color = FlowTableTextMuted,
                                                        maxLines = 1,
                                                    )
                                                }
                                                Text(
                                                    text = inv.clientName.ifBlank { "Consumidor Final" }.uppercase(),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = FlowTableTextDark,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }

                                            // 5. Total
                                            Text(
                                                text = "$${String.format(Locale.US, "%.2f", inv.total)}",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FlowTableTextDark,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier
                                                    .width(110.dp)
                                                    .padding(end = 4.dp),
                                            )

                                            // 6. Sucursal
                                            Text(
                                                text = inv.sucursal.ifBlank { "Principal" },
                                                fontSize = 11.sp,
                                                color = FlowTableTextDark,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.width(110.dp),
                                            )

                                            // 7. Caja
                                            Text(
                                                text = inv.caja.ifBlank { "-" },
                                                fontSize = 11.sp,
                                                color = FlowTableTextDark,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.width(75.dp),
                                            )

                                            // 8. Vendedor
                                            Column(
                                                modifier = Modifier
                                                    .width(130.dp)
                                                    .padding(horizontal = 6.dp),
                                            ) {
                                                Text(
                                                    text = inv.vendedor.ifBlank { "Caja" },
                                                    fontSize = 11.sp,
                                                    color = FlowTableTextDark,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }

                                        HorizontalDivider(color = Color(0xFFF0F3F8), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                // =========================================================================
                // 4. PAGINADOR INFERIOR EN PÍLDORA (1:1 Flow ERP)
                // =========================================================================
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .background(FlowSegmentedContainerBg, CircleShape)
                            .border(1.dp, FlowTableBorder, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { if (currentPage > 1) currentPage-- },
                            enabled = currentPage > 1,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Página anterior",
                                tint = if (currentPage > 1) FlowTableTextDark else Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        Text(
                            text = "Página $currentPage de $totalPages",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FlowTableTextDark,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )

                        IconButton(
                            onClick = { if (currentPage < totalPages) currentPage++ },
                            enabled = currentPage < totalPages,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Página siguiente",
                                tint = if (currentPage < totalPages) FlowTableTextDark else Color(0xFFCBD5E1),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogo de Confirmación para Cargar Factura al Carrito
    if (showConfirmRestoreDialog && invoicePendingRestore != null) {
        AlertDialog(
            onDismissRequest = onDismissConfirmRestore,
            title = {
                Text(
                    text = "Cargar Factura Guardada",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = FlowBrandNavy,
                )
            },
            text = {
                Text(
                    text = "¿Desea cargar la factura #${invoicePendingRestore.code} al carrito actual?",
                    fontSize = 13.sp,
                    color = FlowTableTextDark,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmRestore()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FlowSuccessEmerald),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Cargar en Carrito", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissConfirmRestore) {
                    Text("Cancelar", color = FlowTableTextMuted, fontSize = 12.sp)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(14.dp),
        )
    }
}

@Composable
private fun RowScope.TableHeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp? = null,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val finalModifier = if (width != null) modifier.width(width) else modifier
    Text(
        text = text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = FlowTableTextMuted,
        textAlign = textAlign,
        modifier = finalModifier,
    )
}

@Composable
private fun WebTextInputFilter(
    label: String,
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .border(1.dp, FlowTableBorder, RoundedCornerShape(6.dp))
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Prepend etiqueta e ícono
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .background(FlowSegmentedContainerBg)
                .border(BorderStroke(1.dp, FlowTableBorder))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = FlowTableTextMuted, modifier = Modifier.size(13.dp))
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = FlowTableTextMuted)
        }

        // Input
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(text = placeholder, color = FlowTableTextMuted, fontSize = 10.5.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = FlowTableTextDark, fontSize = 11.sp),
                cursorBrush = SolidColor(FlowBrandBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .forceShowKeyboardOnTouch(),
            )
        }
    }
}

@Composable
private fun WebMonthYearFilter(
    label: String,
    selectedMonth: String,
    onMonthChange: (String) -> Unit,
    yearValue: String,
    onYearChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedMonth by remember { mutableStateOf(false) }
    val months = listOf(
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
        modifier = modifier
            .height(34.dp)
            .border(1.dp, FlowTableBorder, RoundedCornerShape(6.dp))
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Prepend
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .background(FlowSegmentedContainerBg)
                .border(BorderStroke(1.dp, FlowTableBorder))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = label, tint = FlowTableTextMuted, modifier = Modifier.size(13.dp))
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = FlowTableTextMuted)
        }

        // Selector de Mes
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable { expandedMonth = true }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = displayMonth, fontSize = 10.5.sp, color = FlowTableTextDark)
            DropdownMenu(expanded = expandedMonth, onDismissRequest = { expandedMonth = false }) {
                months.forEach { (code, name) ->
                    DropdownMenuItem(
                        text = { Text(name, fontSize = 11.5.sp) },
                        onClick = {
                            onMonthChange(code)
                            expandedMonth = false
                        },
                    )
                }
            }
        }

        // Separador fino
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(FlowTableBorder),
        )

        // Input de Año
        Box(
            modifier = Modifier
                .width(55.dp)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (yearValue.isEmpty()) {
                Text(text = "Año", color = FlowTableTextMuted, fontSize = 10.sp)
            }
            BasicTextField(
                value = yearValue,
                onValueChange = onYearChange,
                singleLine = true,
                textStyle = TextStyle(color = FlowTableTextDark, fontSize = 11.sp, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(FlowBrandBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .forceShowKeyboardOnTouch(),
            )
        }
    }
}
