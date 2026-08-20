package com.amaxonia.pos.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.R
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.InfoBlue
import com.amaxonia.pos.ui.theme.InfoCyan
import com.amaxonia.pos.ui.theme.NeutralGray
import com.amaxonia.pos.ui.theme.SuccessGreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel =
        injectedViewModel {
            AppGraph.settings.settingsViewModel()
        },
) {
    val selectedPrinterType by viewModel.selectedPrinterType.collectAsStateWithLifecycle()
    val availablePrinterTypes by viewModel.availablePrinterTypes.collectAsStateWithLifecycle()
    val allowEditPrices by viewModel.allowEditPrices.collectAsStateWithLifecycle()
    val allowDiscounts by viewModel.allowDiscounts.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isVE = PrinterType.THE_FACTORY_HKA in availablePrinterTypes
    val isPA = PrinterType.SUNMI_V2 in availablePrinterTypes

    SnackbarMessageEffects(viewModel = viewModel, snackbarHostState = snackbarHostState)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SettingsTopAppBar(onBack = onBack) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            PrinterTypeSection(
                selectedPrinterType = selectedPrinterType,
                isVE = isVE,
                isPA = isPA,
                onSelectPrinterType = viewModel::onPrinterTypeSelected,
            )

            Spacer(modifier = Modifier.height(28.dp))

            SalesPermissionsCard(
                allowEditPrices = allowEditPrices,
                allowDiscounts = allowDiscounts,
                onAllowEditPricesChange = viewModel::onAllowEditPricesChanged,
                onAllowDiscountsChange = viewModel::onAllowDiscountsChanged,
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (selectedPrinterType == PrinterType.THE_FACTORY_HKA) {
                TheFactoryConfigCard(
                    viewModel = viewModel,
                    scope = scope,
                    snackbarHostState = snackbarHostState,
                )

                Spacer(modifier = Modifier.height(28.dp))
            }

            PrintTestDivider()

            PrintTestSection(
                selectedPrinterType = selectedPrinterType,
                viewModel = viewModel,
                scope = scope,
                snackbarHostState = snackbarHostState,
            )
        }
    }
}

/** Efectos que muestran los mensajes de error/estado del ViewModel como snackbars. */
@Composable
private fun SnackbarMessageEffects(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopAppBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "Configuracion POS",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Encabezado estándar de sección (título + subtítulo). */
@Composable
internal fun SettingsSectionHeader(
    title: String,
    subtitle: String,
    bottomPadding: Int,
) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = subtitle,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = bottomPadding.dp),
    )
}

/** Sección de selección de tipo de impresora según el país. */
@Composable
private fun PrinterTypeSection(
    selectedPrinterType: PrinterType,
    isVE: Boolean,
    isPA: Boolean,
    onSelectPrinterType: (PrinterType) -> Unit,
) {
    SettingsSectionHeader(
        title = "Tipo de Impresora",
        subtitle = "Selecciona la impresora conectada a tu dispositivo",
        bottomPadding = 16,
    )

    PrinterOptionCard(
        visual = PrinterOptionVisual(icon = Icons.Rounded.Cancel, iconTint = NeutralGray),
        title = "Sin Impresora",
        description = "No se imprimiran recibos. Los comprobantes se envian solo de forma digital.",
        isSelected = selectedPrinterType == PrinterType.NONE,
        onSelect = { onSelectPrinterType(PrinterType.NONE) },
    )

    if (isVE) {
        Spacer(modifier = Modifier.height(12.dp))

        PrinterOptionCard(
            visual = PrinterOptionVisual(icon = Icons.Rounded.Receipt, iconTint = InfoBlue),
            title = "The Factory HKA (Fiscal)",
            description = "Impresora fiscal homologada. Requiere la app The Factory HKA instalada en el dispositivo.",
            isSelected = selectedPrinterType == PrinterType.THE_FACTORY_HKA,
            onSelect = { onSelectPrinterType(PrinterType.THE_FACTORY_HKA) },
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    PrinterOptionCard(
        visual = PrinterOptionVisual(icon = Icons.Rounded.Bluetooth, iconTint = InfoCyan),
        title = "Generica (Bluetooth)",
        description = "Impresora termica generica conectada por Bluetooth. Compatible con la mayoria de impresoras ESC/POS.",
        isSelected = selectedPrinterType == PrinterType.GENERIC_BLUETOOTH,
        onSelect = { onSelectPrinterType(PrinterType.GENERIC_BLUETOOTH) },
    )

    Spacer(modifier = Modifier.height(12.dp))

    if (isPA) {
        PrinterOptionCard(
            visual = PrinterOptionVisual(icon = Icons.Rounded.PhoneAndroid, iconTint = SuccessGreen),
            title = "SUNMI",
            description = "Impresora integrada en terminales Sunmi V2 y V2 Pro. Conexion directa sin Bluetooth.",
            isSelected = selectedPrinterType == PrinterType.SUNMI_V2,
            onSelect = { onSelectPrinterType(PrinterType.SUNMI_V2) },
        )
    }
}

/** Tarjeta de permisos de venta (edición de precios y descuentos en carrito). */
@Composable
private fun SalesPermissionsCard(
    allowEditPrices: Boolean,
    allowDiscounts: Boolean,
    onAllowEditPricesChange: (Boolean) -> Unit,
    onAllowDiscountsChange: (Boolean) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            SettingsSectionHeader(
                title = "Permisos de venta POS",
                subtitle = "Controla si en carrito se puede editar precio y aplicar descuentos.",
                bottomPadding = 14,
            )

            PermissionSwitchRow(
                label = "Permite editar precios",
                description = "Habilita cambiar precio unitario desde el carrito",
                checked = allowEditPrices,
                onCheckedChange = onAllowEditPricesChange,
            )

            Spacer(modifier = Modifier.height(10.dp))

            PermissionSwitchRow(
                label = "Permite agregar descuentos",
                description = "Habilita descuento porcentual por ítem en carrito",
                checked = allowDiscounts,
                onCheckedChange = onAllowDiscountsChange,
            )
        }
    }
}

@Composable
private fun PermissionSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/** Separador antes de la sección de prueba de impresión. */
@Composable
private fun PrintTestDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(20.dp))
}

/** Sección de prueba de impresión para la impresora seleccionada. */
@Composable
private fun PrintTestSection(
    selectedPrinterType: PrinterType,
    viewModel: SettingsViewModel,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    var isTestingPrint by remember { mutableStateOf(false) }
    val brandPrintTestMessage = stringResource(R.string.brand_print_test_message)

    SettingsSectionHeader(
        title = "Prueba de Impresion",
        subtitle = "Envia un recibo de prueba a la impresora seleccionada para verificar la conexion.",
        bottomPadding = 16,
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CurrentPrinterRow(selectedPrinterType = selectedPrinterType)

            Spacer(modifier = Modifier.height(20.dp))

            TestPrintButton(
                isTestingPrint = isTestingPrint,
                onClick = {
                    if (selectedPrinterType == PrinterType.NONE) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Selecciona una impresora primero",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    } else {
                        isTestingPrint = true
                        scope.launch {
                            runPrintTest(
                                printerType = selectedPrinterType,
                                brandPrintTestMessage = brandPrintTestMessage,
                                viewModel = viewModel,
                                snackbarHostState = snackbarHostState,
                            )
                            isTestingPrint = false
                        }
                    }
                },
            )
        }
    }
}
