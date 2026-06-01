package com.amaxonia.pos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.printer.GatewayOption
import com.amaxonia.pos.data.printer.HkaConnectionHelper
import com.amaxonia.pos.data.printer.TheFactoryRapidPayClient
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.PrinterTypePolicy
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val localStore: LocalStore,
    private val hkaConnectionHelper: HkaConnectionHelper? = null,
    private val rapidPayClient: TheFactoryRapidPayClient? = null
) : ViewModel() {

    private val _selectedPrinterType = MutableStateFlow(PrinterType.NONE)
    val selectedPrinterType = _selectedPrinterType.asStateFlow()

    private val _availablePrinterTypes = MutableStateFlow<List<PrinterType>>(emptyList())
    val availablePrinterTypes = _availablePrinterTypes.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _theFactorySettings = MutableStateFlow(TheFactorySettings())
    val theFactorySettings = _theFactorySettings.asStateFlow()
    private val _allowEditPrices = MutableStateFlow(false)
    val allowEditPrices = _allowEditPrices.asStateFlow()
    private val _allowDiscounts = MutableStateFlow(false)
    val allowDiscounts = _allowDiscounts.asStateFlow()
    private val _gatewayOptions = MutableStateFlow<List<GatewayOption>>(emptyList())
    val gatewayOptions = _gatewayOptions.asStateFlow()
    private val _isLoadingGateways = MutableStateFlow(false)
    val isLoadingGateways = _isLoadingGateways.asStateFlow()

    init {
        viewModelScope.launch {
            localStore.selectedPrinterTypeFlow().collect { printerType ->
                _selectedPrinterType.value = printerType
            }
        }
        viewModelScope.launch {
            localStore.selectedCountryFlow().collect { country ->
                _availablePrinterTypes.value = PrinterTypePolicy.availablePrinterTypes(country)
                val current = _selectedPrinterType.value
                if (!PrinterTypePolicy.isAllowed(country, current)) {
                    runCatching { localStore.saveSelectedPrinterType(PrinterType.NONE) }
                }
            }
        }
        viewModelScope.launch {
            localStore.theFactorySettingsFlow().collect { settings ->
                _theFactorySettings.value = settings
                if (settings.gatewayKey.isNotBlank() && _gatewayOptions.value.none { it.key == settings.gatewayKey }) {
                    _gatewayOptions.value = _gatewayOptions.value + GatewayOption(
                        key = settings.gatewayKey,
                        label = settings.gatewayLabel.ifBlank { "Pasarela ${settings.gatewayKey}" }
                    )
                }
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
    }

    fun onPrinterTypeSelected(printerType: PrinterType) {
        if (_selectedPrinterType.value == printerType) return
        viewModelScope.launch {
            runCatching {
                PrinterTypePolicy.validate(localStore.readSelectedCountry(), printerType)
                localStore.saveSelectedPrinterType(printerType)
            }.onFailure { throwable ->
                _errorMessage.update {
                    throwable.message ?: "No se pudo guardar la configuracion de impresora"
                }
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun onTheFactoryIpChanged(value: String) {
        _theFactorySettings.update { current ->
            current.copy(ipAddress = value.trim())
        }
    }

    fun onTheFactoryPortChanged(value: String) {
        _theFactorySettings.update { current ->
            current.copy(port = value.filter(Char::isDigit))
        }
    }

    fun onTheFactorySerialChanged(value: String) {
        _theFactorySettings.update { current ->
            current.copy(
                printerSerial = value
                    .uppercase()
                    .filter(Char::isLetterOrDigit)
                    .take(10)
            )
        }
    }

    fun onAllowEditPricesChanged(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localStore.saveAllowEditPrices(enabled) }
                .onFailure { throwable ->
                    _errorMessage.value = throwable.message ?: "No se pudo guardar permiso de edición de precios"
                }
        }
    }

    fun onAllowDiscountsChanged(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { localStore.saveAllowDiscounts(enabled) }
                .onFailure { throwable ->
                    _errorMessage.value = throwable.message ?: "No se pudo guardar permiso de descuentos"
                }
        }
    }

    fun onGatewaySelected(option: GatewayOption) {
        _theFactorySettings.update { current ->
            current.copy(
                gatewayKey = option.key.trim(),
                gatewayLabel = option.label.trim()
            )
        }
    }

    suspend fun loadGatewayOptions(): Result<List<GatewayOption>> {
        val client = rapidPayClient ?: return Result.failure(
            IllegalStateException("Cliente de pasarela HKA no disponible")
        )
        val current = _theFactorySettings.value
        val savedGateway = current.gatewayKey
            .takeIf { it.isNotBlank() }
            ?.let { key ->
                GatewayOption(
                    key = key,
                    label = current.gatewayLabel.ifBlank { "Pasarela $key" }
                )
            }
        if (current.ipAddress.isBlank() || current.port.toIntOrNull() == null) {
            return Result.failure(IllegalStateException("Guarda IP y puerto antes de consultar pasarelas"))
        }

        _isLoadingGateways.value = true
        return client.listGateways()
            .onSuccess { list ->
                val options = if (list.isNotEmpty()) list else savedGateway?.let(::listOf).orEmpty()
                _gatewayOptions.value = options
                if (current.gatewayKey.isBlank()) {
                    val first = options.firstOrNull()
                    if (first != null) {
                        onGatewaySelected(first)
                    }
                }
            }
            .onFailure { throwable ->
                _errorMessage.value = throwable.message ?: "No se pudo consultar pasarelas"
                if (_gatewayOptions.value.isEmpty()) {
                    _gatewayOptions.value = savedGateway?.let(::listOf).orEmpty()
                    if (current.gatewayKey.isBlank()) {
                        savedGateway?.let(::onGatewaySelected)
                    }
                }
            }
            .also {
                _isLoadingGateways.value = false
            }
    }

    suspend fun persistTheFactorySettings(
        showSuccessMessage: Boolean = false,
        requireGatewaySelection: Boolean = true
    ): Result<Unit> {
        val settings = _theFactorySettings.value.copy(
            ipAddress = _theFactorySettings.value.ipAddress.trim(),
            port = _theFactorySettings.value.port.trim(),
            openMode = _theFactorySettings.value.openMode.trim().ifBlank { "HKA20" },
            gatewayKey = _theFactorySettings.value.gatewayKey.trim(),
            gatewayLabel = _theFactorySettings.value.gatewayLabel.trim(),
            printerSerial = _theFactorySettings.value.printerSerial
                .trim()
                .uppercase()
                .filter(Char::isLetterOrDigit)
                .take(10)
        )

        return runCatching {
            require(settings.ipAddress.isNotBlank()) { "Ingresa la IP de The Factory HKA" }
            require(settings.port.isNotBlank()) { "Ingresa el puerto de The Factory HKA" }
            require(settings.port.toIntOrNull() != null) { "El puerto de The Factory HKA no es valido" }
            if (requireGatewaySelection) {
                require(settings.gatewayKey.isNotBlank()) { "Selecciona una pasarela HKA en configuración de impresora" }
            }
            localStore.saveTheFactorySettings(settings)
            _theFactorySettings.value = settings
            if (showSuccessMessage) {
                _statusMessage.value = "Configuracion de The Factory HKA guardada"
            }
        }.onFailure { throwable ->
            _errorMessage.value = throwable.message ?: "No se pudo guardar la configuracion de The Factory HKA"
        }
    }

    /**
     * Tests raw TCP connectivity to the HKA device.
     * Matches SDK's TCPClientTest.testConnection().
     */
    suspend fun testHkaConnection(): String {
        val helper = hkaConnectionHelper
            ?: return "HkaConnectionHelper no disponible"
        val settings = _theFactorySettings.value
        if (settings.ipAddress.isBlank() || settings.port.toIntOrNull() == null) {
            return "Configura la IP y el puerto primero"
        }
        val result = helper.testConnection(settings.ipAddress, settings.port.toInt())
        return if (result.success) {
            "Conexion exitosa (${result.latencyMs}ms)"
        } else {
            result.errorMessage ?: "No se pudo conectar"
        }
    }

    /**
     * Queries the printer's fiscal status and error state.
     * Matches SDK's MainController.checkStatus() → sends "05" encrypted.
     */
    suspend fun checkHkaPrinterStatus(): String {
        val helper = hkaConnectionHelper
            ?: return "HkaConnectionHelper no disponible"
        val settings = _theFactorySettings.value
        if (settings.ipAddress.isBlank() || settings.port.toIntOrNull() == null) {
            return "Configura la IP y el puerto primero"
        }
        val result = helper.checkPrinterStatus(settings.ipAddress, settings.port.toInt())
        return if (result.success) {
            "ESTADO: ${result.statusDescription}\nERROR: ${result.errorDescription}"
        } else {
            result.errorMessage ?: "No se pudo consultar el estado"
        }
    }
}
