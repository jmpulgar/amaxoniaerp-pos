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
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DesktopWindows
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
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
import com.amaxonia.erp.data.remote.dto.FormaPagoDto
import com.amaxonia.erp.domain.model.Caja
import com.amaxonia.erp.domain.model.CierreCajaFormaPagoItem
import com.amaxonia.erp.domain.model.CierreCajaRequest
import com.amaxonia.erp.domain.model.CierreCajaSummary
import com.amaxonia.erp.domain.model.DenominacionesCatalogo
import com.amaxonia.erp.ui.components.AdaptiveImage
import com.amaxonia.erp.ui.pos.isCashPaymentMethod
import com.amaxonia.erp.ui.theme.FlowHeaderGradient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Pantalla / Modal de Cierre de Caja idéntica al módulo Web "caja/cierre" (Flow UI).
 * Réplica exacta de la estructura, 5 tarjetas superiores en colores pastel,
 * lista unificada de formas de pago y teclado numérico sin duplicación de Efectivo.
 */
@Composable
fun CajaCierreModal(
    activeCaja: Caja?,
    summary: CierreCajaSummary?,
    isLoadingSummary: Boolean = false,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    baseUrl: String = "",
    countryCode: String = "",
    companyDb: String = "",
    paymentMethods: List<FormaPagoDto> = emptyList(),
    onDismiss: () -> Unit,
    onConfirmCierre: (CierreCajaRequest) -> Unit,
) {
    val currentTimeStr = remember {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        sdf.format(Date())
    }

    val resolvedBaseUrl = remember(baseUrl) {
        if (baseUrl.isNotBlank()) baseUrl
        else try { DependencyContainer.apiService.baseUrl.trimEnd('/') } catch (_: Exception) { "" }
    }
    val paymentMethodsMap = remember(paymentMethods) {
        paymentMethods.associateBy { it.idFormaPago }
    }
    val cashMethod = remember(paymentMethods) {
        paymentMethods.firstOrNull { isCashPaymentMethod(it) }
    }

    var numeroCierreFiscal by remember { mutableStateOf("") }
    var observacionCierre by remember { mutableStateOf("") }

    // Map de montos contados ingresados por el cajero para cada forma de pago (idFormaPago -> monto)
    // El id especial 0 representa EFECTIVO (CASH)
    val montosFormasPago = remember { mutableStateMapOf<Int, Double>() }

    // Id de forma de pago actualmente seleccionada/enfocada (0 = Efectivo)
    var activeFormaPagoId by remember { mutableStateOf<Int?>(0) }
    var showConfirmZeroAmount by remember { mutableStateOf(false) }

    // Metadatos de Efectivo
    val efApertura = summary?.openAmount ?: 0.0
    val efVentas = summary?.montoEfectivoVentas ?: 0.0
    val efEntrada = summary?.montoEfectivoEntrada ?: 0.0
    val efSalida = summary?.montoEfectivoSalida ?: 0.0
    val efTotalSistema = summary?.montoEfectivoTotal ?: (efApertura + efVentas + efEntrada - efSalida)
    val efCierreDeclarado = montosFormasPago[0] ?: 0.0
    val efDiferencia = efCierreDeclarado - efTotalSistema

    // Otras formas de pago (excluyendo efectivo de la lista para NO duplicar)
    val todasFormasPago = summary?.paymentLines.orEmpty()
    val otrasLineasPago = remember(todasFormasPago) {
        todasFormasPago.filter { fp ->
            !fp.siglas.equals("CASH", ignoreCase = true) &&
                !fp.siglas.equals("EF", ignoreCase = true) &&
                !fp.siglas.equals("EFE", ignoreCase = true) &&
                !fp.siglas.equals("EFECTIVO", ignoreCase = true) &&
                !fp.label.equals("EFECTIVO", ignoreCase = true)
        }
    }

    // Totales calculados
    val totalOtrasContadas = otrasLineasPago.sumOf { fp -> montosFormasPago[fp.idFormaPago] ?: 0.0 }
    val totalCierreDeclarado = efCierreDeclarado + totalOtrasContadas
    val totalSistema = summary?.montoTotal ?: (efTotalSistema + otrasLineasPago.sumOf { it.amount })
    val diferenciaTotal = totalCierreDeclarado - totalSistema

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
                            text = "Cierre de Caja",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "#${activeCaja?.codCaja ?: "CJ-01"}-${summary?.idCajaSecuencia?.takeLast(4) ?: "0001"}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }

                    // Botones superiores idénticos a Facturación (Limpiar, Guardar, Regresar)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HeaderActionModalButton(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Limpiar",
                            onClick = {
                                montosFormasPago.clear()
                                observacionCierre = ""
                                numeroCierreFiscal = ""
                            },
                        )
                        HeaderActionModalButton(
                            icon = Icons.Default.Save,
                            label = if (isSubmitting) "Guardando…" else "Guardar",
                            isLoading = isSubmitting,
                            onClick = {
                                if (totalCierreDeclarado <= 0.0) {
                                    showConfirmZeroAmount = true
                                } else {
                                    val request = buildCierreRequest(
                                        summary = summary,
                                        activeCaja = activeCaja,
                                        totalEfectivoContado = efCierreDeclarado,
                                        difEfectivo = efDiferencia,
                                        totalOtrasContadas = totalOtrasContadas,
                                        difOtras = totalOtrasContadas - (summary?.montoOtrosTotal ?: 0.0),
                                        totalCierre = totalCierreDeclarado,
                                        diferenciaTotal = diferenciaTotal,
                                        otrasLineasPago = otrasLineasPago,
                                        montosFormasPago = montosFormasPago,
                                        observacionCierre = observacionCierre,
                                        numeroCierreFiscal = numeroCierreFiscal,
                                    )
                                    onConfirmCierre(request)
                                }
                            },
                        )
                        HeaderActionModalButton(
                            icon = Icons.AutoMirrored.Filled.Reply,
                            label = "Regresar",
                            onClick = onDismiss,
                        )
                    }
                }

                if (isLoadingSummary) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF2488FA))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Cargando balance y arqueo de caja…", color = Color(0xFF64748B))
                        }
                    }
                } else {
                    // Contenedor principal blanco interior
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .padding(14.dp),
                    ) {
                        if (errorMessage != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFEBEE))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = errorMessage,
                                    color = Color(0xFFC62828),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // ═════════════════════════════════════════════════════════════
                        // 2. FILA DE METADATOS: APERTURA, CIERRE, ARQUEO Z, OBSERVACIONES
                        // ═════════════════════════════════════════════════════════════
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WebInputPrepend(
                                modifier = Modifier.weight(1.3f),
                                icon = Icons.Default.CalendarToday,
                                label = "Apertura",
                                value = summary?.openedAt?.ifBlank { null } ?: currentTimeStr,
                                readOnly = true,
                            )
                            WebInputPrepend(
                                modifier = Modifier.weight(1.3f),
                                icon = Icons.Default.CalendarToday,
                                label = "Cierre",
                                value = currentTimeStr,
                                readOnly = true,
                            )
                            WebInputPrepend(
                                modifier = Modifier.weight(1.1f),
                                icon = Icons.Default.ConfirmationNumber,
                                label = "Nº Arqueo Z",
                                value = numeroCierreFiscal,
                                onValueChange = { numeroCierreFiscal = it },
                            )
                        }

                        // Fila Observaciones
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                        ) {
                            WebInputPrepend(
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.AutoMirrored.Filled.Notes,
                                label = "Observaciones",
                                value = observacionCierre,
                                onValueChange = { observacionCierre = it },
                            )
                        }

                        // ═════════════════════════════════════════════════════════════
                        // 3. BARRA SUPERIOR DE BALANCE (5 TARJETAS EN TONOS PASTEL - IMAGE 2)
                        // ═════════════════════════════════════════════════════════════
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // 1. TOTAL (Lavanda / Azul oscuro #201B82)
                            FlowBalanceCard(
                                modifier = Modifier.weight(1f),
                                title = "TOTAL",
                                value = String.format(Locale.US, "%.2f", totalSistema),
                                headerBg = Color(0xFFEDE7F6),
                                headerTextCol = Color(0xFF201B82),
                                valueCol = Color(0xFF201B82),
                            )

                            // 2. VENTAS - CRÉDITO (Rosa pastel #FDE8EF / Rosa fuerte #DB2777)
                            FlowBalanceCard(
                                modifier = Modifier.weight(1f),
                                title = "VENTAS - CRÉDITO",
                                value = "0.00",
                                headerBg = Color(0xFFFDE8EF),
                                headerTextCol = Color(0xFFDB2777),
                                valueCol = Color(0xFFDB2777),
                            )

                            // 3. NOTAS DE DÉBITO (Menta pastel #E0F7FA / Cian #0891B2)
                            FlowBalanceCard(
                                modifier = Modifier.weight(1f),
                                title = "NOTAS DE DÉBITO",
                                value = "0.00",
                                headerBg = Color(0xFFE0F7FA),
                                headerTextCol = Color(0xFF0891B2),
                                valueCol = Color(0xFF0891B2),
                            )

                            // 4. CIERRE (Violeta pastel #F3E5F5 / Violeta oscuro #7C3AED)
                            FlowBalanceCard(
                                modifier = Modifier.weight(1f),
                                title = "CIERRE",
                                value = String.format(Locale.US, "%.2f", totalCierreDeclarado),
                                headerBg = Color(0xFFF3E5F5),
                                headerTextCol = Color(0xFF7C3AED),
                                valueCol = Color(0xFF7C3AED),
                            )

                            // 5. SOBRANTE / FALTANTE / DIFERENCIA (Azul pastel #E3F2FD / Azul #1565C0)
                            val difLabel = when {
                                abs(diferenciaTotal) < 0.005 -> "DIFERENCIA"
                                diferenciaTotal > 0 -> "SOBRANTE"
                                else -> "FALTANTE"
                            }
                            val (difHeaderBg, difColor) = when {
                                abs(diferenciaTotal) < 0.005 -> Pair(Color(0xFFE8F5E9), Color(0xFF16A34A))
                                diferenciaTotal > 0 -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0))
                                else -> Pair(Color(0xFFFFEBEE), Color(0xFFE11D48))
                            }

                            FlowBalanceCard(
                                modifier = Modifier.weight(1f),
                                title = difLabel,
                                value = String.format(Locale.US, "%.2f", abs(diferenciaTotal)),
                                headerBg = difHeaderBg,
                                headerTextCol = difColor,
                                valueCol = difColor,
                            )
                        }

                        // ═════════════════════════════════════════════════════════════
                        // 4. CUERPO: LISTADO UNIFICADO DE FORMAS DE PAGO (IZQ) + TECLADO (DER)
                        // ═════════════════════════════════════════════════════════════
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            // Columna Izquierda: Formas de Pago continuas (Image 2)
                            LazyColumn(
                                modifier = Modifier
                                    .weight(0.72f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // ═════════════════════════════════════════════════════════
                                // 1. FILA DE EFECTIVO (ÚNICA VEZ CON TODAS SUS COLUMNAS)
                                // ═════════════════════════════════════════════════
                                item {
                                    val isEfFocused = activeFormaPagoId == 0
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                            .background(Color.White, RoundedCornerShape(12.dp))
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Nombre e ícono a la izquierda
                                        Row(
                                            modifier = Modifier
                                                .width(150.dp)
                                                .padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            val cashImageUrl = remember(cashMethod?.idFormaPago, cashMethod?.imagen, resolvedBaseUrl, countryCode, companyDb) {
                                                ImageUrlHelper.paymentMethodImageUrl(
                                                    baseUrl = resolvedBaseUrl,
                                                    countryCode = countryCode,
                                                    companyDb = companyDb,
                                                    idFormaPago = cashMethod?.idFormaPago ?: 1,
                                                    imageValue = cashMethod?.imagen,
                                                )
                                            }
                                            if (cashImageUrl.isNotBlank()) {
                                                AdaptiveImage(
                                                    source = cashImageUrl,
                                                    contentDescription = "EFECTIVO",
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(4.dp)),
                                                    contentScale = ContentScale.Fit,
                                                    fallback = {},
                                                )
                                            }
                                            Text(
                                                text = "EFECTIVO",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = Color(0xFF1E293B),
                                            )
                                        }

                                        // Bloque de columnas interconectadas de Efectivo
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                                .clip(RoundedCornerShape(8.dp)),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            // APERTURA
                                            FlowColCell(
                                                modifier = Modifier.weight(1f),
                                                title = "APERTURA",
                                                value = String.format(Locale.US, "%.2f", efApertura),
                                                headerBg = Color(0xFFF1F5F9),
                                                textCol = Color(0xFF2488FA),
                                            )
                                            // VENTAS
                                            FlowColCell(
                                                modifier = Modifier.weight(1f),
                                                title = "VENTAS",
                                                value = String.format(Locale.US, "%.2f", efVentas),
                                                headerBg = Color(0xFFF1F5F9),
                                                textCol = Color(0xFF2488FA),
                                            )
                                            // MOV. ENTRADA
                                            FlowColCell(
                                                modifier = Modifier.weight(1.1f),
                                                title = "MOV. ENTRADA",
                                                value = String.format(Locale.US, "%.2f", efEntrada),
                                                headerBg = Color(0xFFF1F5F9),
                                                textCol = Color(0xFF2488FA),
                                            )
                                            // MOV. SALIDA
                                            FlowColCell(
                                                modifier = Modifier.weight(1.1f),
                                                title = "MOV. SALIDA",
                                                value = String.format(Locale.US, "%.2f", efSalida),
                                                headerBg = Color(0xFFFDE8EF),
                                                textCol = Color(0xFFE11D48),
                                            )
                                            // Símbolo =
                                            Box(
                                                modifier = Modifier
                                                    .width(22.dp)
                                                    .height(46.dp)
                                                    .background(Color(0xFFF8FAFC)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(text = "=", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                            }
                                            // TOTAL
                                            FlowColCell(
                                                modifier = Modifier.weight(1f),
                                                title = "TOTAL",
                                                value = String.format(Locale.US, "%.2f", efTotalSistema),
                                                headerBg = Color(0xFFEDE7F6),
                                                textCol = Color(0xFF201B82),
                                            )
                                            // CIERRE (Editable input)
                                            FlowEditableInputCell(
                                                modifier = Modifier.weight(1.1f),
                                                title = "CIERRE",
                                                value = if (efCierreDeclarado > 0.0) String.format(Locale.US, "%.2f", efCierreDeclarado) else "",
                                                isFocused = isEfFocused,
                                                onFocus = { activeFormaPagoId = 0 },
                                                onValueChange = { newVal ->
                                                    montosFormasPago[0] = newVal.toDoubleOrNull() ?: 0.0
                                                },
                                            )
                                            // SOBRANTE / DIFERENCIA
                                            val difCol = if (abs(efDiferencia) < 0.005) Color(0xFF16A34A) else (if (efDiferencia > 0) Color(0xFF1565C0) else Color(0xFFE11D48))
                                            FlowColCell(
                                                modifier = Modifier.weight(1f),
                                                title = if (efDiferencia >= 0) "SOBRANTE" else "FALTANTE",
                                                value = String.format(Locale.US, "%.2f", abs(efDiferencia)),
                                                headerBg = Color(0xFFF1F5F9),
                                                textCol = difCol,
                                            )
                                        }
                                    }
                                }

                                // ═════════════════════════════════════════════════════════
                                // 2. OTRAS FORMAS DE PAGO (UNA FILA POR CADA UNA - IMAGE 2)
                                // ═════════════════════════════════════════════════
                                itemsIndexed(otrasLineasPago) { _, fp ->
                                    val isFocused = activeFormaPagoId == fp.idFormaPago
                                    val contado = montosFormasPago[fp.idFormaPago] ?: 0.0
                                    val difFp = contado - fp.amount

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                            .background(Color.White, RoundedCornerShape(12.dp))
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Logo/Icono y Nombre de la Forma de Pago
                                        Row(
                                            modifier = Modifier
                                                .width(150.dp)
                                                .padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            val matchedMethod = paymentMethodsMap[fp.idFormaPago]
                                            val fpImageUrl = remember(fp.idFormaPago, matchedMethod?.imagen, resolvedBaseUrl, countryCode, companyDb) {
                                                ImageUrlHelper.paymentMethodImageUrl(
                                                    baseUrl = resolvedBaseUrl,
                                                    countryCode = countryCode,
                                                    companyDb = companyDb,
                                                    idFormaPago = fp.idFormaPago,
                                                    imageValue = matchedMethod?.imagen,
                                                )
                                            }
                                            if (fpImageUrl.isNotBlank()) {
                                                AdaptiveImage(
                                                    source = fpImageUrl,
                                                    contentDescription = fp.label,
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(4.dp)),
                                                    contentScale = ContentScale.Fit,
                                                    fallback = {},
                                                )
                                            }
                                            Text(
                                                text = fp.label.uppercase(Locale.ROOT),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF1E293B),
                                                lineHeight = 14.sp,
                                            )
                                        }

                                        // Bloque de 3 columnas (TOTAL | CIERRE | DIFERENCIA)
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                                .clip(RoundedCornerShape(8.dp)),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            // TOTAL (Sistema)
                                            FlowColCell(
                                                modifier = Modifier.weight(1.3f),
                                                title = "TOTAL",
                                                value = String.format(Locale.US, "%.2f", fp.amount),
                                                headerBg = Color(0xFFEDE7F6),
                                                textCol = Color(0xFF201B82),
                                            )

                                            // CIERRE (Editable input - se ilumina morado #8E24AA al enfocar)
                                            FlowEditableInputCell(
                                                modifier = Modifier.weight(1.3f),
                                                title = "CIERRE",
                                                value = if (contado > 0.0) String.format(Locale.US, "%.2f", contado) else "",
                                                isFocused = isFocused,
                                                onFocus = { activeFormaPagoId = fp.idFormaPago },
                                                onValueChange = { newVal ->
                                                    montosFormasPago[fp.idFormaPago] = newVal.toDoubleOrNull() ?: 0.0
                                                },
                                            )

                                            // DIFERENCIA
                                            val difColor = if (abs(difFp) < 0.005) Color(0xFF16A34A) else (if (difFp > 0) Color(0xFF1565C0) else Color(0xFFE11D48))
                                            FlowColCell(
                                                modifier = Modifier.weight(1f),
                                                title = "",
                                                value = String.format(Locale.US, "%.2f", abs(difFp)),
                                                headerBg = Color(0xFFF1F5F9),
                                                textCol = difColor,
                                            )
                                        }
                                    }
                                }
                            }

                            // ═════════════════════════════════════════════════════════
                            // 5. COLUMNA DERECHA: TECLADO TÁCTIL LIMPIO (IMAGE 2)
                            // ═════════════════════════════════════════════════
                            Column(
                                modifier = Modifier
                                    .weight(0.28f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // Barra visor blanca limpia arriba del teclado
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .border(width = 1.dp, color = Color(0xFFCBD5E1), shape = RoundedCornerShape(8.dp))
                                        .background(Color.White, shape = RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    val currentVal = activeFormaPagoId?.let { montosFormasPago[it] } ?: 0.0
                                    val displayStr = if (currentVal > 0.0) String.format(Locale.US, "%.2f", currentVal) else "0.00"
                                    Text(
                                        text = displayStr,
                                        color = Color(0xFF1E293B),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                // Teclado numérico interactivo web idéntico
                                WebPosKeypad(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    onKeyPress = { key ->
                                        val activeId = activeFormaPagoId ?: return@WebPosKeypad
                                        val currentVal = montosFormasPago[activeId] ?: 0.0
                                        val currentStr = if (currentVal > 0.0) String.format(Locale.US, "%.2f", currentVal) else ""

                                        val newStr = when (key) {
                                            "CE" -> ""
                                            "DEL" -> if (currentStr.isNotEmpty()) currentStr.dropLast(1) else ""
                                            "ENTER" -> {
                                                // Pasar al siguiente campo
                                                val nextId = if (activeId == 0) {
                                                    otrasLineasPago.firstOrNull()?.idFormaPago ?: 0
                                                } else {
                                                    val currentIdx = otrasLineasPago.indexOfFirst { it.idFormaPago == activeId }
                                                    if (currentIdx in 0 until otrasLineasPago.lastIndex) {
                                                        otrasLineasPago[currentIdx + 1].idFormaPago
                                                    } else 0
                                                }
                                                activeFormaPagoId = nextId
                                                currentStr
                                            }
                                            else -> currentStr + key
                                        }
                                        montosFormasPago[activeId] = newStr.toDoubleOrNull() ?: 0.0
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de confirmación si el monto de cierre es cero
    if (showConfirmZeroAmount) {
        AlertDialog(
            onDismissRequest = { showConfirmZeroAmount = false },
            title = { Text("Cierre de Caja", fontWeight = FontWeight.Bold, color = Color(0xFF1E293B)) },
            text = {
                Text(
                    text = "No ha realizado la carga de los montos del cierre.\n\n¿Está seguro que desea cerrar la caja con monto cero?",
                    fontSize = 14.sp,
                    color = Color(0xFF475569),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmZeroAmount = false
                        val request = buildCierreRequest(
                            summary = summary,
                            activeCaja = activeCaja,
                            totalEfectivoContado = efCierreDeclarado,
                            difEfectivo = efDiferencia,
                            totalOtrasContadas = totalOtrasContadas,
                            difOtras = totalOtrasContadas - (summary?.montoOtrosTotal ?: 0.0),
                            totalCierre = totalCierreDeclarado,
                            diferenciaTotal = diferenciaTotal,
                            otrasLineasPago = otrasLineasPago,
                            montosFormasPago = montosFormasPago,
                            observacionCierre = observacionCierre,
                            numeroCierreFiscal = numeroCierreFiscal,
                        )
                        onConfirmCierre(request)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                ) {
                    Text("Cerrar con Monto Cero")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmZeroAmount = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

/**
 * Tarjeta superior de Balance en colores pastel suaves (Flow UI - Image 2).
 */
@Composable
private fun FlowBalanceCard(
    title: String,
    value: String,
    headerBg: Color,
    headerTextCol: Color,
    valueCol: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    color = headerTextCol,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = valueCol,
                )
            }
        }
    }
}

/**
 * Celda no editable de la fila de forma de pago.
 */
@Composable
private fun FlowColCell(
    title: String,
    value: String,
    headerBg: Color,
    textCol: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(width = 0.5.dp, color = Color(0xFFF1F5F9)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(vertical = 3.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = textCol,
            )
        }
    }
}

/**
 * Celda editable de CIERRE (se ilumina morado vibrante #8E24AA al enfocar, como Tarjeta Crédito en Image 2).
 */
@Composable
private fun FlowEditableInputCell(
    title: String,
    value: String,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(width = 0.5.dp, color = Color(0xFFF1F5F9)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3E5F5))
                .padding(vertical = 3.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7C3AED),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(if (isFocused) Color(0xFF8E24AA) else Color(0xFFFFFBEB))
                .clickable { onFocus() }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                textStyle = TextStyle(
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isFocused) Color.White else Color(0xFF7C3AED),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun buildCierreRequest(
    summary: CierreCajaSummary?,
    activeCaja: Caja?,
    totalEfectivoContado: Double,
    difEfectivo: Double,
    totalOtrasContadas: Double,
    difOtras: Double,
    totalCierre: Double,
    diferenciaTotal: Double,
    otrasLineasPago: List<com.amaxonia.erp.domain.model.CierreCajaPaymentLine>,
    montosFormasPago: Map<Int, Double>,
    observacionCierre: String,
    numeroCierreFiscal: String,
): CierreCajaRequest {
    val idSecuencia = summary?.idCajaSecuencia ?: activeCaja?.idCaja ?: "1"

    val detalleFormaPago = otrasLineasPago.map { fp ->
        val contado = montosFormasPago[fp.idFormaPago] ?: 0.0
        CierreCajaFormaPagoItem(
            id_forma_pago = fp.idFormaPago,
            monto = fp.amount,
            monto_cierre = contado,
            monto_diferencia = contado - fp.amount,
        )
    }

    return CierreCajaRequest(
        id = idSecuencia,
        monto_efectivo_ventas = summary?.montoEfectivoVentas ?: 0.0,
        monto_efectivo_entrada = summary?.montoEfectivoEntrada ?: 0.0,
        monto_efectivo_salida = summary?.montoEfectivoSalida ?: 0.0,
        monto_efectivo_total = summary?.montoEfectivoTotal ?: 0.0,
        monto_efectivo_cierre = totalEfectivoContado,
        monto_efectivo_diferencia = difEfectivo,
        monto_otros_total = summary?.montoOtrosTotal ?: 0.0,
        monto_otros_cierre = totalOtrasContadas,
        monto_otros_diferencia = difOtras,
        monto_total = summary?.montoTotal ?: 0.0,
        monto_cierre = totalCierre,
        monto_diferencia = diferenciaTotal,
        detalle = emptyList(),
        detalle_formapago = detalleFormaPago,
        observacion_cierre = observacionCierre.ifBlank { null },
        numero_cierre_fiscal = numeroCierreFiscal.ifBlank { null },
    )
}
