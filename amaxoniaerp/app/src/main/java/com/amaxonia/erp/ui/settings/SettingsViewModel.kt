package com.amaxonia.erp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.printer.DefaultInvoicePrintGateway
import com.amaxonia.erp.data.printer.panama.PanamaInvoiceTicketFormatter
import com.amaxonia.erp.data.printer.venezuela.VenezuelaInvoiceTicketFormatter
import com.amaxonia.erp.data.remote.dto.ClientePrintDto
import com.amaxonia.erp.data.remote.dto.EmpresaPrintDto
import com.amaxonia.erp.data.remote.dto.FacturaPrintPayloadDto
import com.amaxonia.erp.data.remote.dto.PagoPrintDto
import com.amaxonia.erp.data.remote.dto.ProductoPrintDto
import com.amaxonia.erp.domain.model.printer.FiscalDeviceDiagnostics
import com.amaxonia.erp.domain.model.printer.PrinterType
import com.amaxonia.erp.domain.model.printer.PrinterTypePolicy
import com.amaxonia.erp.domain.model.printer.TheFactorySettings
import com.amaxonia.erp.ui.customerdisplay.CustomerDisplayManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SettingsViewModel(
    private val localStore: LocalStore,
    private val fiscalDiagnostics: FiscalDeviceDiagnostics? = null,
    private val printGateway: DefaultInvoicePrintGateway? = null,
    private val customerDisplayManager: CustomerDisplayManager? = null,
) : ViewModel() {

    private val _selectedPrinterType = MutableStateFlow(PrinterType.NONE)
    val selectedPrinterType = _selectedPrinterType.asStateFlow()

    private val _availablePrinterTypes = MutableStateFlow<List<PrinterType>>(emptyList())
    val availablePrinterTypes = _availablePrinterTypes.asStateFlow()

    private val _theFactorySettings = MutableStateFlow(TheFactorySettings())
    val theFactorySettings = _theFactorySettings.asStateFlow()

    private val _allowEditPrices = MutableStateFlow(true)
    val allowEditPrices = _allowEditPrices.asStateFlow()

    private val _allowDiscounts = MutableStateFlow(true)
    val allowDiscounts = _allowDiscounts.asStateFlow()

    private val _autoPrintReceipt = MutableStateFlow(true)
    val autoPrintReceipt = _autoPrintReceipt.asStateFlow()

    private val _customerDisplayEnabled = MutableStateFlow(false)
    val customerDisplayEnabled = _customerDisplayEnabled.asStateFlow()

    val isSecondaryDisplayAvailable = customerDisplayManager?.isSecondaryDisplayAvailable
        ?: MutableStateFlow(false).asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            localStore.selectedPrinterTypeFlow().collect { printerType ->
                _selectedPrinterType.value = printerType
            }
        }
        viewModelScope.launch {
            localStore.selectedCountryFlow().collect { country ->
                val available = PrinterTypePolicy.availablePrinterTypes(country)
                _availablePrinterTypes.value = available
                val current = _selectedPrinterType.value
                if (!PrinterTypePolicy.isAllowed(country, current)) {
                    runCatching { localStore.saveSelectedPrinterType(PrinterType.NONE) }
                }
            }
        }
        viewModelScope.launch {
            localStore.theFactorySettingsFlow().collect { settings ->
                _theFactorySettings.value = settings
            }
        }
        viewModelScope.launch {
            localStore.allowEditPricesFlow().collect { enabled ->
                _allowEditPrices.value = enabled
            }
        }
        viewModelScope.launch {
            localStore.allowDiscountsFlow().collect { enabled ->
                _allowDiscounts.value = enabled
            }
        }
        viewModelScope.launch {
            localStore.autoPrintReceiptFlow().collect { enabled ->
                _autoPrintReceipt.value = enabled
            }
        }
        viewModelScope.launch {
            localStore.customerDisplayEnabledFlow().collect { enabled ->
                _customerDisplayEnabled.value = enabled
            }
        }
    }

    fun onPrinterTypeSelected(printerType: PrinterType) {
        if (_selectedPrinterType.value == printerType) return
        viewModelScope.launch {
            runCatching {
                localStore.saveSelectedPrinterType(printerType)
                _statusMessage.value = "Impresora configurada: ${printerType.displayName}"
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "No se pudo guardar la configuración de impresora"
            }
        }
    }

    fun onTheFactoryIpChanged(value: String) {
        _theFactorySettings.update { it.copy(ipAddress = value.trim()) }
    }

    fun onTheFactoryPortChanged(value: String) {
        _theFactorySettings.update { it.copy(port = value.filter(Char::isDigit)) }
    }

    fun onTheFactorySerialChanged(value: String) {
        _theFactorySettings.update {
            it.copy(
                printerSerial = value.uppercase().filter(Char::isLetterOrDigit).take(10),
            )
        }
    }

    fun onAllowEditPricesChanged(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localStore.saveAllowEditPrices(enabled) }
                .onFailure { _errorMessage.value = "Error al guardar permiso de modificación de precios" }
        }
    }

    fun onAllowDiscountsChanged(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localStore.saveAllowDiscounts(enabled) }
                .onFailure { _errorMessage.value = "Error al guardar permiso de descuentos" }
        }
    }

    fun onAutoPrintReceiptChanged(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localStore.saveAutoPrintReceipt(enabled) }
                .onFailure { _errorMessage.value = "Error al guardar opción de auto-impresión" }
        }
    }

    fun onCustomerDisplayToggled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localStore.saveCustomerDisplayEnabled(enabled) }
                .onFailure { _errorMessage.value = "Error al guardar opción de pantalla de cliente" }
        }
    }

    suspend fun persistTheFactorySettings(
        showSuccessMessage: Boolean = false,
        requireGatewaySelection: Boolean = false,
    ): Result<Unit> {
        val current = _theFactorySettings.value.copy(
            ipAddress = _theFactorySettings.value.ipAddress.trim(),
            port = _theFactorySettings.value.port.trim(),
            printerSerial = _theFactorySettings.value.printerSerial.trim().uppercase(),
        )
        return runCatching {
            require(current.ipAddress.isNotBlank()) { "Ingresa la IP de The Factory HKA" }
            require(current.port.isNotBlank()) { "Ingresa el puerto de The Factory HKA" }
            requireNotNull(current.port.toIntOrNull()) { "El puerto de The Factory HKA no es válido" }
            localStore.saveTheFactorySettings(current)
            _theFactorySettings.value = current
            if (showSuccessMessage) {
                _statusMessage.value = "Configuración HKA guardada correctamente"
            }
        }.onFailure {
            _errorMessage.value = it.message ?: "No se pudo guardar la configuración HKA"
        }
    }

    suspend fun testHkaConnection(): String {
        val diagnostics = fiscalDiagnostics ?: return "Herramienta de diagnóstico HKA no disponible"
        val settings = _theFactorySettings.value
        if (settings.ipAddress.isBlank() || settings.port.toIntOrNull() == null) {
            return "Configura la IP y puerto primero"
        }
        val res = diagnostics.testConnection(settings.ipAddress, settings.port.toInt())
        return if (res.success) {
            "Conexión exitosa con impresora fiscal (${res.latencyMs}ms)"
        } else {
            res.errorMessage ?: "Fallo al conectar con impresora fiscal"
        }
    }

    suspend fun checkHkaPrinterStatus(): String {
        val diagnostics = fiscalDiagnostics ?: return "Herramienta de diagnóstico HKA no disponible"
        val settings = _theFactorySettings.value
        if (settings.ipAddress.isBlank() || settings.port.toIntOrNull() == null) {
            return "Configura la IP y puerto primero"
        }
        val res = diagnostics.printerStatus(settings.ipAddress, settings.port.toInt())
        return if (res.success) {
            "ESTADO: ${res.statusDescription}\nERROR: ${res.errorDescription}"
        } else {
            res.errorMessage ?: "No se pudo consultar el estado fiscal"
        }
    }

    suspend fun printTestReceipt(): String {
        val gateway = printGateway ?: return "Gateway de impresión no configurado"
        val country = localStore.readSelectedCountry()?.code ?: "PA"
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val samplePayload = FacturaPrintPayloadDto(
            facturaId = "TEST-001",
            numeroFactura = "TEST-0001",
            fecha = now,
            empresa = EmpresaPrintDto(
                nombre = "AMAXONIA ERP POS",
                ruc = "155000000-2-2024",
                direccion = "Sucursal Principal - Terminal 01",
                telefono = "+507 000-0000",
                tienda = "01",
                caja = "01",
            ),
            cliente = ClientePrintDto(
                nombre = "CLIENTE DE PRUEBA",
                documento = "CF",
            ),
            vendedor = "ADMINISTRADOR",
            productos = listOf(
                ProductoPrintDto(
                    nombre = "PRODUCTO DEMO PRUEBA",
                    cantidad = "1.00",
                    unidad = "UND",
                    precioUnitario = "10.00",
                    descuento = "0.00",
                    impuesto = "0.70",
                    total = "10.70",
                    codigo = "DEMO-01",
                    tasaImpuesto = "7.0",
                ),
            ),
            subtotal = "10.00",
            totalImpuesto = "0.70",
            total = "10.70",
            descuento = "0.00",
            pagos = listOf(
                PagoPrintDto(metodo = "EFECTIVO", monto = "10.70"),
            ),
            cambio = "0.00",
            qrUrl = "https://listoerp.app/test",
        )

        val feedback = gateway.print(country, "TEST-001", samplePayload)
        return feedback?.displayMessage ?: "Prueba de impresión enviada"
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
