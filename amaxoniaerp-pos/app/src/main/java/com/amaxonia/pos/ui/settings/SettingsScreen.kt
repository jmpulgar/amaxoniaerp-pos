package com.amaxonia.pos.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.AmaxoniaBlue
import com.amaxonia.pos.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = injectedViewModel {
        SettingsViewModel(
            localStore = DependencyContainer.localStore,
            hkaConnectionHelper = DependencyContainer.hkaConnectionHelper,
            rapidPayClient = DependencyContainer.theFactoryRapidPayClient
        )
    }
) {
    val selectedPrinterType by viewModel.selectedPrinterType.collectAsState()
    val availablePrinterTypes by viewModel.availablePrinterTypes.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val theFactorySettings by viewModel.theFactorySettings.collectAsState()
    val gatewayOptions by viewModel.gatewayOptions.collectAsState()
    val isLoadingGateways by viewModel.isLoadingGateways.collectAsState()
    val allowEditPrices by viewModel.allowEditPrices.collectAsState()
    val allowDiscounts by viewModel.allowDiscounts.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isTestingPrint by remember { mutableStateOf(false) }
    var isSavingTheFactorySettings by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var isCheckingStatus by remember { mutableStateOf(false) }
    val isVE = PrinterType.THE_FACTORY_HKA in availablePrinterTypes
    val isPA = PrinterType.SUNMI_V2 in availablePrinterTypes

    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        viewModel.clearErrorMessage()
    }

    LaunchedEffect(statusMessage) {
        val message = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        viewModel.clearStatusMessage()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Configuracion POS",
                        fontWeight = FontWeight.Bold,
                        color = AmaxoniaBlue
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = AmaxoniaBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Section header
            Text(
                text = "Tipo de Impresora",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Selecciona la impresora conectada a tu dispositivo",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            // Printer option cards
            PrinterOptionCard(
                icon = Icons.Rounded.Cancel,
                iconTint = Color(0xFF9E9E9E),
                title = "Sin Impresora",
                description = "No se imprimiran recibos. Los comprobantes se envian solo de forma digital.",
                isSelected = selectedPrinterType == PrinterType.NONE,
                onSelect = { viewModel.onPrinterTypeSelected(PrinterType.NONE) }
            )

            if (isVE) {
                Spacer(modifier = Modifier.height(12.dp))

                PrinterOptionCard(
                    icon = Icons.Rounded.Receipt,
                    iconTint = Color(0xFF1565C0),
                    title = "The Factory HKA (Fiscal)",
                    description = "Impresora fiscal homologada. Requiere la app The Factory HKA instalada en el dispositivo.",
                    isSelected = selectedPrinterType == PrinterType.THE_FACTORY_HKA,
                    onSelect = { viewModel.onPrinterTypeSelected(PrinterType.THE_FACTORY_HKA) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            PrinterOptionCard(
                icon = Icons.Rounded.Bluetooth,
                iconTint = Color(0xFF0277BD),
                title = "Generica (Bluetooth)",
                description = "Impresora termica generica conectada por Bluetooth. Compatible con la mayoria de impresoras ESC/POS.",
                isSelected = selectedPrinterType == PrinterType.GENERIC_BLUETOOTH,
                onSelect = { viewModel.onPrinterTypeSelected(PrinterType.GENERIC_BLUETOOTH) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isPA) {
                PrinterOptionCard(
                    icon = Icons.Rounded.PhoneAndroid,
                    iconTint = Color(0xFF2E7D32),
                    title = "SUNMI",
                    description = "Impresora integrada en terminales Sunmi V2 y V2 Pro. Conexion directa sin Bluetooth.",
                    isSelected = selectedPrinterType == PrinterType.SUNMI_V2,
                    onSelect = { viewModel.onPrinterTypeSelected(PrinterType.SUNMI_V2) }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Permisos de venta POS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Controla si en carrito se puede editar precio y aplicar descuentos.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Permite editar precios", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Habilita cambiar precio unitario desde el carrito",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowEditPrices,
                            onCheckedChange = viewModel::onAllowEditPricesChanged
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Permite agregar descuentos", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Habilita descuento porcentual por ítem en carrito",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowDiscounts,
                            onCheckedChange = viewModel::onAllowDiscountsChanged
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedPrinterType == PrinterType.THE_FACTORY_HKA) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Configuracion HKA POS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Guarda la IP y el puerto que usa la app fiscal de The Factory para evitar que quede en espera al abrir.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                        Text(
                            text = "Modelo operativo para cobro + impresion: HKA20",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = theFactorySettings.ipAddress,
                            onValueChange = viewModel::onTheFactoryIpChanged,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("IP de HKA POS") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmaxoniaBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = theFactorySettings.port,
                            onValueChange = viewModel::onTheFactoryPortChanged,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Puerto") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmaxoniaBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = theFactorySettings.printerSerial,
                            onValueChange = viewModel::onTheFactorySerialChanged,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Serial fiscal (iI*)") },
                            placeholder = { Text("Ej: Z1B9999999") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmaxoniaBlue,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Opcional recomendado: si lo defines aqui, se usa para notas de credito fiscales.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Pasarela de pago (HKA20)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Selecciona aquí la pasarela que se usará al cobrar desde POS.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    val saveResult = viewModel.persistTheFactorySettings(requireGatewaySelection = false)
                                    if (saveResult.isSuccess) {
                                        viewModel.loadGatewayOptions().onFailure { error ->
                                            snackbarHostState.showSnackbar(
                                                error.message ?: "No se pudo consultar pasarelas",
                                                duration = SnackbarDuration.Long
                                            )
                                        }
                                    }
                                }
                            },
                            enabled = !isLoadingGateways,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            if (isLoadingGateways) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Consultando...")
                            } else {
                                Text("Consultar pasarelas disponibles")
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (gatewayOptions.isEmpty()) {
                            Text(
                                text = "No hay pasarelas cargadas. Pulsa \"Consultar pasarelas disponibles\".",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                gatewayOptions.forEach { option ->
                                    OutlinedButton(
                                        onClick = { viewModel.onGatewaySelected(option) },
                                        modifier = Modifier.fillMaxWidth(),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (theFactorySettings.gatewayKey == option.key) AmaxoniaBlue else MaterialTheme.colorScheme.outline
                                        )
                                    ) {
                                        Text(
                                            text = "${option.label} (${option.key})",
                                            color = if (theFactorySettings.gatewayKey == option.key) AmaxoniaBlue else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                isSavingTheFactorySettings = true
                                scope.launch {
                                    viewModel.persistTheFactorySettings(showSuccessMessage = true)
                                    isSavingTheFactorySettings = false
                                }
                            },
                            enabled = !isSavingTheFactorySettings,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmaxoniaBlue)
                        ) {
                            if (isSavingTheFactorySettings) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Text(if (isSavingTheFactorySettings) "Guardando..." else "Guardar configuracion HKA")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- Verificar Conexion & Verificar Estado ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                        ) {
                            // Verificar Conexion
                            Button(
                                onClick = {
                                    isTestingConnection = true
                                    scope.launch {
                                        viewModel.persistTheFactorySettings(requireGatewaySelection = false)
                                        val msg = viewModel.testHkaConnection()
                                        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                                        isTestingConnection = false
                                    }
                                },
                                enabled = !isTestingConnection,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                )
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.Wifi,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Conexion", fontSize = 13.sp)
                                }
                            }

                            // Verificar Estado
                            Button(
                                onClick = {
                                    isCheckingStatus = true
                                    scope.launch {
                                        viewModel.persistTheFactorySettings(requireGatewaySelection = false)
                                        val msg = viewModel.checkHkaPrinterStatus()
                                        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
                                        isCheckingStatus = false
                                    }
                                },
                                enabled = !isCheckingStatus,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                )
                            ) {
                                if (isCheckingStatus) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Estado", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }

            // Test print section
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Prueba de Impresion",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Envia un recibo de prueba a la impresora seleccionada para verificar la conexion.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AmaxoniaBlue.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Print,
                                contentDescription = null,
                                tint = AmaxoniaBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Impresora Actual",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedPrinterType.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Status indicator
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selectedPrinterType != PrinterType.NONE)
                                        SuccessGreen.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (selectedPrinterType != PrinterType.NONE) "Configurada" else "No activa",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedPrinterType != PrinterType.NONE) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (selectedPrinterType == PrinterType.NONE) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Selecciona una impresora primero",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                                return@Button
                            }
                            isTestingPrint = true
                            scope.launch {
                                if (selectedPrinterType == PrinterType.THE_FACTORY_HKA) {
                                    val saveResult = viewModel.persistTheFactorySettings(requireGatewaySelection = false)
                                    if (saveResult.isFailure) {
                                        isTestingPrint = false
                                        return@launch
                                    }
                                }
                                if (selectedPrinterType == PrinterType.SUNMI_V2) {
                                    val ticketPrinter = DependencyContainer.printerFactory.getActiveTicketPrinter()
                                    val result = ticketPrinter?.printText("Prueba de impresión SUNMI\nAmaxonia POS")
                                    if (result is com.amaxonia.pos.domain.model.printer.PrintResult.Success) {
                                        snackbarHostState.showSnackbar(
                                            "Impresion de prueba enviada correctamente",
                                            duration = SnackbarDuration.Short
                                        )
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            (result as? com.amaxonia.pos.domain.model.printer.PrintResult.Error)?.message
                                                ?: "Impresora SUNMI no disponible",
                                            duration = SnackbarDuration.Long
                                        )
                                    }
                                } else {
                                    val printer = DependencyContainer.printerFactory.getActivePrinter()
                                    if (printer == null) {
                                        snackbarHostState.showSnackbar(
                                            "Impresora no disponible. Verifica la conexion.",
                                            duration = SnackbarDuration.Short
                                        )
                                        isTestingPrint = false
                                        return@launch
                                    }
                                    val testTransaction = com.amaxonia.pos.domain.model.Transaction(
                                        id = "test-print",
                                        invoiceNumber = "TEST-001",
                                        time = "Ahora",
                                        amount = 0.01,
                                        dateHeader = "Prueba",
                                        status = com.amaxonia.pos.domain.model.TransactionStatus.PAID
                                    )
                                    printer.printReceipt(testTransaction).fold(
                                        onSuccess = {
                                            snackbarHostState.showSnackbar(
                                                "Impresion de prueba enviada correctamente",
                                                duration = SnackbarDuration.Short
                                            )
                                        },
                                        onFailure = { e ->
                                            snackbarHostState.showSnackbar(
                                                "Error: ${e.message}",
                                                duration = SnackbarDuration.Long
                                            )
                                        }
                                    )
                                }
                                isTestingPrint = false
                            }
                        },
                        enabled = !isTestingPrint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmaxoniaBlue,
                            disabledContainerColor = AmaxoniaBlue.copy(alpha = 0.5f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        if (isTestingPrint) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Enviando prueba...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Print,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Probar Impresion",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrinterOptionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    ElevatedCard(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Selection indicator
            if (isSelected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Seleccionada",
                    tint = AmaxoniaBlue,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                RadioButton(
                    selected = false,
                    onClick = onSelect,
                    colors = RadioButtonDefaults.colors(
                        unselectedColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
    }
}
