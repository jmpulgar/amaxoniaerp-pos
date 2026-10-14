package com.amaxonia.erp.ui.caja

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import com.amaxonia.erp.composition.DependencyContainer
import com.amaxonia.erp.data.remote.ImageUrlHelper
import com.amaxonia.erp.domain.model.AperturaRequest
import com.amaxonia.erp.domain.model.Caja
import com.amaxonia.erp.domain.model.DenominacionesCatalogo
import com.amaxonia.erp.domain.model.MonedaDenominacion
import com.amaxonia.erp.domain.model.SellerSummary
import com.amaxonia.erp.ui.components.AdaptiveImage
import com.amaxonia.erp.ui.theme.FlowHeaderGradient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla / Modal de Apertura de Caja idéntica al módulo Web "caja/apertura".
 * Réplica exacta de la estructura, colores, badges Metronic y layout de la Web POS.
 */
@Composable
fun CajaAperturaModal(
    activeCaja: Caja?,
    availableSellers: List<SellerSummary>,
    currentSeller: SellerSummary?,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    baseUrl: String = "",
    countryCode: String = "PA",
    companyDb: String = "",
    onDismiss: () -> Unit,
    onConfirmApertura: (AperturaRequest) -> Unit,
) {
    val currentTimeStr = remember {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        sdf.format(Date())
    }
    val currentDateOnly = remember {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        sdf.format(Date())
    }

    val resolvedBaseUrl = remember(baseUrl) {
        if (baseUrl.isNotBlank()) baseUrl
        else try { DependencyContainer.apiService.baseUrl.trimEnd('/') } catch (_: Exception) { "" }
    }

    var selectedSeller by remember {
        mutableStateOf(currentSeller ?: availableSellers.firstOrNull() ?: SellerSummary(1, "001 VENDEDOR/SALONERO #1"))
    }
    var secuenciaText by remember { mutableStateOf("000009") }
    var observacion by remember { mutableStateOf("") }
    val baseFondoApertura = activeCaja?.fondoApertura ?: 100.0

    // Denominaciones
    var denominaciones by remember {
        mutableStateOf(DenominacionesCatalogo.getDenominacionesPanama())
    }

    // Active field for touch keypad: Pair(rowIndex, isRollo)
    var activeInputCell by remember { mutableStateOf<Pair<Int, Boolean>?>(Pair(2, false)) }
    var showWarningLowAmount by remember { mutableStateOf(false) }

    val totalCalculado = denominaciones.sumOf { it.monto }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F3F8)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF0F3F8)),
            ) {
                // ═════════════════════════════════════════════════════════════════════════
                // 1. CABECERA PORTLET FLOW HEADER GRADIENT (IDÉNTICO A FACTURACIÓN)
                // ═════════════════════════════════════════════════════════════════════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(FlowHeaderGradient)
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Lado izquierdo: Ícono cuadrado monitor + Título
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.DesktopWindows,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = "Apertura de Caja",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }

                    // Lado derecho: Botones de Acción Superiores con estilo idéntico a Facturación
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Limpiar
                        HeaderActionModalButton(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Limpiar",
                            onClick = {
                                denominaciones = DenominacionesCatalogo.getDenominacionesPanama()
                                observacion = ""
                            },
                        )

                        // Guardar
                        HeaderActionModalButton(
                            icon = Icons.Default.Save,
                            label = if (isLoading) "Guardando…" else "Guardar",
                            isLoading = isLoading,
                            onClick = {
                                if (totalCalculado < baseFondoApertura && baseFondoApertura > 0.0) {
                                    showWarningLowAmount = true
                                } else {
                                    val request = AperturaRequest(
                                        idCaja = activeCaja?.idCaja ?: "1",
                                        montoApertura = totalCalculado,
                                        idVendedor = selectedSeller.id,
                                        secuencia = secuenciaText,
                                        idSucursal = activeCaja?.idSucursal,
                                    )
                                    onConfirmApertura(request)
                                }
                            },
                        )

                        // Regresar / Cerrar (Pantalla eliminado)
                        HeaderActionModalButton(
                            icon = Icons.AutoMirrored.Filled.Reply,
                            label = "Regresar",
                            onClick = onDismiss,
                        )
                    }
                }

                // ═════════════════════════════════════════════════════════════════════════
                // 2. CONTENIDO PRINCIPAL: TARJETA BLANCA CON BÚSQUEDA Y CAMPOS
                // ═════════════════════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(14.dp),
                ) {
                    // Título azul centrado "Búsqueda Avanzada"
                    Text(
                        text = "Búsqueda Avanzada",
                        color = Color(0xFF1E88E5),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        textAlign = TextAlign.Center,
                    )

                    // Fila superior: Filtros de Fecha Inicio y Fecha Fin
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        WebInputPrepend(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CalendarToday,
                            label = "Fecha Inicio",
                            value = "01/10/2026",
                            readOnly = true,
                        )
                        WebInputPrepend(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CalendarToday,
                            label = "Fecha Fin",
                            value = currentDateOnly,
                            readOnly = true,
                        )
                    }

                    // ═════════════════════════════════════════════════════════════════════
                    // 3. CAMPOS DE CONFIGURACIÓN DE LA CAJA (2 COLUMNAS COMO EN LA WEB)
                    // ═════════════════════════════════════════════════════════════════════
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Columna Izquierda (Fecha, Caja, Secuencia, y Observación abajo)
                        Column(
                            modifier = Modifier.weight(0.68f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                WebInputPrepend(
                                    modifier = Modifier.weight(1.3f),
                                    icon = Icons.Default.CalendarToday,
                                    label = "Fecha",
                                    value = currentTimeStr,
                                    readOnly = true,
                                )
                                WebInputPrepend(
                                    modifier = Modifier.weight(1.4f),
                                    icon = Icons.Default.DesktopWindows,
                                    label = "Caja",
                                    value = activeCaja?.let { "${it.codCaja ?: "001"} - ${it.displayName}" } ?: "001 - CAJA 1 SUC. 1",
                                    isDropdown = true,
                                    readOnly = true,
                                )
                                WebInputPrepend(
                                    modifier = Modifier.weight(0.9f),
                                    icon = Icons.Default.ConfirmationNumber,
                                    label = "Secuencia",
                                    value = secuenciaText,
                                    onValueChange = { secuenciaText = it },
                                )
                            }

                            // Fila Observación
                            WebInputPrepend(
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.AutoMirrored.Filled.Notes,
                                label = "Observación",
                                value = observacion,
                                onValueChange = { observacion = it },
                            )
                        }

                        // Columna Derecha (Cajero arriba, Fondo Apertura abajo alineado a la calculadora)
                        Column(
                            modifier = Modifier.weight(0.32f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WebInputPrepend(
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Default.Person,
                                label = "Cajero",
                                value = selectedSeller.nombre,
                                isDropdown = true,
                                readOnly = true,
                            )
                            WebInputPrepend(
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Default.MonetizationOn,
                                label = "Fondo Apertura",
                                value = String.format(Locale.US, "$%.2f", baseFondoApertura),
                                textAlign = TextAlign.End,
                                readOnly = true,
                            )
                        }
                    }

                    // ═════════════════════════════════════════════════════════════════════
                    // 4. TABLA DE DENOMINACIONES (IZQ) Y TOTALIZADOR + TECLADO (DER)
                    // ═════════════════════════════════════════════════════════════════════
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Columna Izquierda: Tabla con Billetes y Monedas reales
                        Column(
                            modifier = Modifier
                                .weight(0.68f)
                                .fillMaxHeight()
                                .border(width = 0.5.dp, color = Color(0xFFE2E8F0)),
                        ) {
                            // Cabecera de la tabla idéntica a la web
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(width = 1.dp, color = Color(0xFF1E88E5))
                                    .background(Color.White)
                                    .padding(vertical = 5.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "DENOMINACIÓN",
                                    modifier = Modifier.weight(1.8f),
                                    color = Color(0xFF1E88E5),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                )
                                Text(
                                    text = "VALOR",
                                    modifier = Modifier.weight(1.1f),
                                    color = Color(0xFF1E88E5),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = "CANTIDAD\nROLLOS",
                                    modifier = Modifier.weight(1.1f),
                                    color = Color(0xFF1E88E5),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 11.sp,
                                )
                                Text(
                                    text = "CANTIDAD\nSUELTA",
                                    modifier = Modifier.weight(1.1f),
                                    color = Color(0xFF1E88E5),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 11.sp,
                                )
                                Text(
                                    text = "CANTIDAD\nTOTAL",
                                    modifier = Modifier.weight(1.1f),
                                    color = Color(0xFF1E88E5),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 11.sp,
                                )
                                Text(
                                    text = "MONTO",
                                    modifier = Modifier.weight(1.2f),
                                    color = Color(0xFF1E88E5),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    textAlign = TextAlign.End,
                                )
                            }

                            // Filas de denominaciones
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                itemsIndexed(denominaciones) { index, item ->
                                    val isFocusedRollo = activeInputCell?.first == index && activeInputCell?.second == true
                                    val isFocusedSuelta = activeInputCell?.first == index && activeInputCell?.second == false

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .background(if (index % 2 == 0) Color.White else Color(0xFFFAFAFA))
                                            .border(width = 0.5.dp, color = Color(0xFFF1F5F9))
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // 1. Imagen miniatura del billete / moneda real
                                        Box(
                                            modifier = Modifier.weight(1.8f),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            CurrencyThumbnail(
                                                item = item,
                                                baseUrl = resolvedBaseUrl,
                                                countryCode = countryCode,
                                                companyDb = companyDb,
                                            )
                                        }

                                        // 2. Valor
                                        Column(
                                            modifier = Modifier.weight(1.1f),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = String.format(Locale.US, "%.2f", item.valor),
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E293B),
                                            )
                                            Text(
                                                text = "Rollo de ${if (item.rollo > 0) item.rollo else 100} Unidad(es)",
                                                fontSize = 8.5.sp,
                                                color = Color(0xFF94A3B8),
                                            )
                                        }

                                        // 3. Cantidad Rollos
                                        Box(
                                            modifier = Modifier
                                                .weight(1.1f)
                                                .fillMaxHeight()
                                                .padding(horizontal = 2.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (item.rollo > 0) {
                                                WebTableCellInput(
                                                    value = if (item.cantidadRollo > 0) item.cantidadRollo.toString() else "",
                                                    isFocused = isFocusedRollo,
                                                    onFocus = { activeInputCell = Pair(index, true) },
                                                    onValueChange = { newVal ->
                                                        val intVal = newVal.toIntOrNull() ?: 0
                                                        denominaciones = denominaciones.toMutableList().also {
                                                            it[index] = item.copy(cantidadRollo = intVal)
                                                        }
                                                    },
                                                )
                                            }
                                        }

                                        // 4. Cantidad Suelta (resaltada en azul pastel suave al enfocar)
                                        Box(
                                            modifier = Modifier
                                                .weight(1.1f)
                                                .fillMaxHeight()
                                                .padding(horizontal = 2.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            WebTableCellInput(
                                                value = if (item.cantidadSuelta > 0) item.cantidadSuelta.toString() else "",
                                                isFocused = isFocusedSuelta,
                                                onFocus = { activeInputCell = Pair(index, false) },
                                                onValueChange = { newVal ->
                                                    val intVal = newVal.toIntOrNull() ?: 0
                                                    denominaciones = denominaciones.toMutableList().also {
                                                        it[index] = item.copy(cantidadSuelta = intVal)
                                                    }
                                                },
                                            )
                                        }

                                        // 5. Cantidad Total
                                        Text(
                                            text = if (item.cantidadTotal > 0) item.cantidadTotal.toString() else "",
                                            modifier = Modifier.weight(1.1f),
                                            textAlign = TextAlign.Center,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B),
                                        )

                                        // 6. Monto
                                        Text(
                                            text = String.format(Locale.US, "%.2f", item.monto),
                                            modifier = Modifier.weight(1.2f),
                                            textAlign = TextAlign.End,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF334155),
                                        )
                                    }
                                }
                            }
                        }

                        // Columna Derecha: Display Coral/Rojo + Teclado Táctil Web (32%)
                        Column(
                            modifier = Modifier
                                .weight(0.32f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // ═════════════════════════════════════════════════════════════
                            // DISPLAY TOTAL CORAL / ROJO VIBRANTE CON SOMBRA COMO EN LA WEB
                            // ═════════════════════════════════════════════════════════════
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            listOf(Color(0xFFFF3366), Color(0xFFFF2D55)),
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Text(
                                    text = String.format(Locale.US, "$%.2f", totalCalculado),
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                )
                            }

                            // Visor blanco redondeado debajo del display
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .border(width = 1.dp, color = Color(0xFFE2E8F0), shape = RoundedCornerShape(8.dp))
                                    .background(Color.White, shape = RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                val activeStr = activeInputCell?.let { (idx, isRollo) ->
                                    val itm = denominaciones[idx]
                                    if (isRollo) itm.cantidadRollo.toString() else itm.cantidadSuelta.toString()
                                } ?: ""
                                Text(
                                    text = activeStr,
                                    color = Color(0xFF475569),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            // ═════════════════════════════════════════════════════════════
                            // TECLADO NUMÉRICO TÁCTIL WEB EXACTO (5x4 CON COLORES IDÉNTICOS)
                            // ═════════════════════════════════════════════════════════════
                            WebPosKeypad(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                onKeyPress = { key ->
                                    activeInputCell?.let { (idx, isRollo) ->
                                        val currentItem = denominaciones[idx]
                                        val currentStr = if (isRollo) {
                                            if (currentItem.cantidadRollo > 0) currentItem.cantidadRollo.toString() else ""
                                        } else {
                                            if (currentItem.cantidadSuelta > 0) currentItem.cantidadSuelta.toString() else ""
                                        }

                                        val newStr = when (key) {
                                            "CE" -> ""
                                            "DEL" -> if (currentStr.isNotEmpty()) currentStr.dropLast(1) else ""
                                            "ENTER" -> {
                                                if (idx + 1 < denominaciones.size) {
                                                    activeInputCell = Pair(idx + 1, false)
                                                }
                                                currentStr
                                            }
                                            else -> currentStr + key
                                        }

                                        val intVal = newStr.toIntOrNull() ?: 0
                                        denominaciones = denominaciones.toMutableList().also {
                                            it[idx] = if (isRollo) currentItem.copy(cantidadRollo = intVal) else currentItem.copy(cantidadSuelta = intVal)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal de confirmación si el monto es inferior al fondo establecido
    if (showWarningLowAmount) {
        AlertDialog(
            onDismissRequest = { showWarningLowAmount = false },
            title = {
                Text(
                    text = "Apertura de Caja",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                )
            },
            text = {
                Text(
                    text = "El monto de apertura ($${String.format(Locale.US, "%.2f", totalCalculado)}) es inferior al fondo establecido ($${String.format(Locale.US, "%.2f", baseFondoApertura)}).\n\n¿Desea continuar con la apertura?",
                    fontSize = 14.sp,
                    color = Color(0xFF475569),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWarningLowAmount = false
                        val request = AperturaRequest(
                            idCaja = activeCaja?.idCaja ?: "1",
                            montoApertura = totalCalculado,
                            idVendedor = selectedSeller.id,
                            secuencia = secuenciaText,
                            idSucursal = activeCaja?.idSucursal,
                        )
                        onConfirmApertura(request)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningLowAmount = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

/**
 * Botones de acción del Header idénticos a los de Facturación (Flow Header Action Item).
 */
@Composable
fun HeaderActionModalButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isLoading: Boolean = false,
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
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
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

/**
 * Campo input con prepend azul idéntico a Metronic (input-group-prepend azul eléctrico).
 */
@Composable
fun WebInputPrepend(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    isDropdown: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    onValueChange: (String) -> Unit = {},
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .border(width = 1.dp, color = Color(0xFFCBD5E1), shape = RoundedCornerShape(4.dp))
            .background(if (readOnly) Color(0xFFF8FAFC) else Color.White, shape = RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bloque prepend azul
        Row(
            modifier = Modifier
                .background(Color(0xFF1E88E5), shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        // Input
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 12.5.sp,
                color = Color(0xFF1E293B),
                fontWeight = FontWeight.SemiBold,
                textAlign = textAlign,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )

        if (isDropdown) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Muestra la imagen real de la denominación si existe en el backend, sin inventar formas.
 */
@Composable
fun CurrencyThumbnail(
    item: MonedaDenominacion,
    baseUrl: String = "",
    countryCode: String = "PA",
    companyDb: String = "",
) {
    val imageUrl = remember(item.id, item.imagen, baseUrl, countryCode, companyDb) {
        if (!item.imagen.isNullOrBlank()) {
            item.imagen
        } else {
            ImageUrlHelper.denominationImageUrl(baseUrl, countryCode, companyDb, item.id)
        }
    }

    Box(
        modifier = Modifier
            .width(62.dp)
            .height(34.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AdaptiveImage(
            source = imageUrl,
            contentDescription = item.denominacion,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(2.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * Celda de la tabla para ingresar cantidades (resaltada en azul pastel #EBF3FE al seleccionar).
 */
@Composable
fun WebTableCellInput(
    value: String,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onValueChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(if (isFocused) Color(0xFFEBF3FE) else Color.Transparent)
            .border(
                width = if (isFocused) 1.dp else 0.5.dp,
                color = if (isFocused) Color(0xFF93C5FD) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(2.dp),
            )
            .clickable { onFocus() },
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = TextStyle(
                textAlign = TextAlign.Center,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Teclado táctil exacto (5 filas x 4 columnas con la paleta de colores de la web).
 */
@Composable
fun WebPosKeypad(
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .border(width = 0.5.dp, color = Color(0xFFE2E8F0), shape = RoundedCornerShape(8.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Fila 1: CE (rojo) | % (azul suave) | / (azul suave) | * (azul suave)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WebKeypadButton("CE", Modifier.weight(1f), textColor = Color(0xFFFF3366)) { onKeyPress("CE") }
            WebKeypadButton("%", Modifier.weight(1f), bgColor = Color(0xFFEBF3FE), textColor = Color(0xFF1E88E5)) { onKeyPress("%") }
            WebKeypadButton("/", Modifier.weight(1f), bgColor = Color(0xFFEBF3FE), textColor = Color(0xFF1E88E5)) { onKeyPress("/") }
            WebKeypadButton("*", Modifier.weight(1f), bgColor = Color(0xFFEBF3FE), textColor = Color(0xFF1E88E5)) { onKeyPress("*") }
        }

        // Fila 2: 1 | 2 | 3 | -
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WebKeypadButton("1", Modifier.weight(1f)) { onKeyPress("1") }
            WebKeypadButton("2", Modifier.weight(1f)) { onKeyPress("2") }
            WebKeypadButton("3", Modifier.weight(1f)) { onKeyPress("3") }
            WebKeypadButton("-", Modifier.weight(1f), bgColor = Color(0xFFEBF3FE), textColor = Color(0xFF1E88E5)) { onKeyPress("-") }
        }

        // Fila 3: 4 | 5 | 6 | +
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WebKeypadButton("4", Modifier.weight(1f)) { onKeyPress("4") }
            WebKeypadButton("5", Modifier.weight(1f)) { onKeyPress("5") }
            WebKeypadButton("6", Modifier.weight(1f)) { onKeyPress("6") }
            WebKeypadButton("+", Modifier.weight(1f), bgColor = Color(0xFFEBF3FE), textColor = Color(0xFF1E88E5)) { onKeyPress("+") }
        }

        // Fila 4: 7 | 8 | 9 | ⌫
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WebKeypadButton("7", Modifier.weight(1f)) { onKeyPress("7") }
            WebKeypadButton("8", Modifier.weight(1f)) { onKeyPress("8") }
            WebKeypadButton("9", Modifier.weight(1f)) { onKeyPress("9") }
            WebKeypadIconButton(Icons.AutoMirrored.Filled.Backspace, Modifier.weight(1f), bgColor = Color(0xFFEBF3FE), tint = Color(0xFF1E88E5)) { onKeyPress("DEL") }
        }

        // Fila 5: ⌨ | 0 | . | ↵
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WebKeypadIconButton(Icons.Default.Keyboard, Modifier.weight(1f), bgColor = Color(0xFFF1F5F9), tint = Color(0xFF64748B)) {}
            WebKeypadButton("0", Modifier.weight(1f)) { onKeyPress("0") }
            WebKeypadButton(".", Modifier.weight(1f)) { onKeyPress(".") }
            // Botón ENTER degradado azul rey idéntico a la web
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        brush = Brush.horizontalGradient(listOf(Color(0xFF1E88E5), Color(0xFF1565C0))),
                    )
                    .clickable { onKeyPress("ENTER") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                    contentDescription = "Enter",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun WebKeypadButton(
    label: String,
    modifier: Modifier = Modifier,
    bgColor: Color = Color.White,
    textColor: Color = Color(0xFF1E293B),
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(width = 0.5.dp, color = Color(0xFFCBD5E1), shape = RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}

@Composable
fun WebKeypadIconButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    bgColor: Color = Color(0xFFF1F5F9),
    tint: Color = Color(0xFF475569),
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(width = 0.5.dp, color = Color(0xFFCBD5E1), shape = RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}
