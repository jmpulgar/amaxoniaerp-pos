package com.amaxonia.erp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.erp.domain.model.printer.PrinterType
import com.amaxonia.erp.ui.theme.InfoBlue
import com.amaxonia.erp.ui.theme.InfoCyan
import com.amaxonia.erp.ui.theme.NeutralGray
import com.amaxonia.erp.ui.theme.SuccessGreen

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onNavigateToVisibilitySettings: (() -> Unit)? = null,
) {
    val selectedPrinterType by viewModel.selectedPrinterType.collectAsStateWithLifecycle()
    val availablePrinterTypes by viewModel.availablePrinterTypes.collectAsStateWithLifecycle()
    val allowEditPrices by viewModel.allowEditPrices.collectAsStateWithLifecycle()
    val allowDiscounts by viewModel.allowDiscounts.collectAsStateWithLifecycle()
    val autoPrintReceipt by viewModel.autoPrintReceipt.collectAsStateWithLifecycle()
    val customerDisplayEnabled by viewModel.customerDisplayEnabled.collectAsStateWithLifecycle()
    val isSecondaryDisplayAvailable by viewModel.isSecondaryDisplayAvailable.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Configuración del POS",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Administra las impresoras físicas, comprobantes y políticas de venta",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            // Sección 1: Selección de Impresora
            Text(
                text = "Tipo de Impresora",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))

            availablePrinterTypes.forEach { type ->
                val visual =
                    when (type) {
                        PrinterType.NONE -> PrinterOptionVisual(Icons.Rounded.Cancel, NeutralGray)
                        PrinterType.SUNMI_V2 -> PrinterOptionVisual(Icons.Rounded.PhoneAndroid, InfoBlue)
                        PrinterType.IMIN_SWIFT -> PrinterOptionVisual(Icons.Rounded.PhoneAndroid, InfoCyan)
                        PrinterType.GENERIC_BLUETOOTH -> PrinterOptionVisual(Icons.Rounded.Bluetooth, InfoBlue)
                        PrinterType.THE_FACTORY_HKA -> PrinterOptionVisual(Icons.Rounded.Receipt, SuccessGreen)
                    }
                val description =
                    when (type) {
                        PrinterType.NONE -> "No enviar comprobantes a impresión física"
                        PrinterType.SUNMI_V2 -> "Impresora térmica integrada en terminales SUNMI (58mm)"
                        PrinterType.IMIN_SWIFT -> "Impresora térmica integrada en terminales iMin Swift 2"
                        PrinterType.GENERIC_BLUETOOTH -> "Impresora térmica móvil estándar conectada por Bluetooth"
                        PrinterType.THE_FACTORY_HKA -> "Impresora fiscal The Factory HKA homologada SENIAT (TCP/IP)"
                    }

                PrinterOptionCard(
                    visual = visual,
                    title = type.displayName,
                    description = description,
                    isSelected = selectedPrinterType == type,
                    onSelect = { viewModel.onPrinterTypeSelected(type) },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sección 2: Configuración HKA si aplica
            if (selectedPrinterType == PrinterType.THE_FACTORY_HKA) {
                TheFactoryConfigCard(
                    viewModel = viewModel,
                    scope = scope,
                    snackbarHostState = snackbarHostState,
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Sección 3: Permisos y Opciones Operativas del POS
            Text(
                text = "Opciones y Permisos de Venta",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Impresión automática al cobrar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Text(
                                text = "Envía el comprobante a imprimir al confirmar la venta sin esperar confirmación",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoPrintReceipt,
                            onCheckedChange = viewModel::onAutoPrintReceiptChanged,
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Pantalla de cliente (Sunmi D3 Pro)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSecondaryDisplayAvailable) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ) {
                                    Text(
                                        text = if (isSecondaryDisplayAvailable) "Hardware detectado" else "No detectado",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSecondaryDisplayAvailable) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = "Muestra en la segunda pantalla el carrito en vivo, totales e información fiscal",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = customerDisplayEnabled,
                            onCheckedChange = viewModel::onCustomerDisplayToggled,
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permitir modificar precios al vender",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Text(
                                text = "Habilita la edición manual del precio unitario de items en el terminal",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = allowEditPrices,
                            onCheckedChange = viewModel::onAllowEditPricesChanged,
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permitir aplicar descuentos",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            Text(
                                text = "Permite aplicar descuentos por item o globales en el carrito",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = allowDiscounts,
                            onCheckedChange = viewModel::onAllowDiscountsChanged,
                        )
                    }
                }
            }

            if (onNavigateToVisibilitySettings != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Modo Offline y Visibilidad",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(10.dp))
                ElevatedCard(
                    onClick = onNavigateToVisibilitySettings,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ajustes de Visibilidad",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                            Text(
                                text = "Configura la descarga de catálogos y filtro de productos por departamento y clientes por sucursal.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sección 4: Prueba de Impresión
            Text(
                text = "Diagnóstico de Impresión",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))

            PrintTestSection(
                selectedPrinterType = selectedPrinterType,
                viewModel = viewModel,
                scope = scope,
                snackbarHostState = snackbarHostState,
            )
        }
    }
}
