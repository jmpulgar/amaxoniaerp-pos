package com.amaxonia.pos.data.local

import androidx.datastore.preferences.core.edit
import com.amaxonia.pos.data.local.LocalStore.Companion.SECURE_GATEWAY_KEY
import com.amaxonia.pos.domain.model.ServerCountries
import com.amaxonia.pos.domain.model.ServerCountry
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.model.printer.PrinterTypePolicy
import com.amaxonia.pos.domain.model.printer.TheFactorySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

// Ajustes del POS sobre LocalStore: país/selección de impresora, configuración
// TheFactory (HKA) y permisos de edición/descuento. Extensiones sobre LocalStore:
// mismo comportamiento que cuando eran miembros.

suspend fun LocalStore.saveSelectedPrinterType(printerType: PrinterType) {
    PrinterTypePolicy.validate(readSelectedCountry(), printerType)
    dataStore.edit { prefs ->
        prefs[selectedPrinterTypeKey] = printerType.name
    }
}

suspend fun LocalStore.readSelectedPrinterType(): PrinterType = selectedPrinterTypeFlow().first()

fun LocalStore.selectedPrinterTypeFlow(): Flow<PrinterType> =
    dataStore.data.map { prefs ->
        val code = prefs[selectedCountryKey] ?: com.amaxonia.pos.BuildConfig.DEFAULT_COUNTRY_CODE
        val country = ServerCountries.fromCode(code)
        val storedPrinter =
            prefs[selectedPrinterTypeKey]
                ?.let { storedValue -> PrinterType.entries.firstOrNull { it.name == storedValue } }
                ?: PrinterType.NONE
        PrinterTypePolicy.coerce(country, storedPrinter)
    }

suspend fun LocalStore.saveTheFactorySettings(settings: TheFactorySettings) {
    val gatewayKey = settings.gatewayKey.trim()
    if (gatewayKey.isEmpty()) {
        removeSecureValue(SECURE_GATEWAY_KEY)
    } else {
        secureWriter.write(SECURE_GATEWAY_KEY, gatewayKey)
    }
    dataStore.edit { prefs ->
        prefs[theFactoryIpKey] = settings.ipAddress.trim()
        prefs[theFactoryPortKey] = settings.port.trim()
        prefs[theFactoryModeKey] = settings.openMode.trim()
        prefs.remove(theFactoryGatewayKey)
        prefs[theFactoryGatewayLabelKey] = settings.gatewayLabel.trim()
        prefs[theFactoryPrinterSerialKey] = settings.printerSerial.trim()
    }
}

suspend fun LocalStore.readTheFactorySettings(): TheFactorySettings = theFactorySettingsFlow().first()

fun LocalStore.theFactorySettingsFlow(): Flow<TheFactorySettings> =
    flow {
        readSecureOrMigrate(SECURE_GATEWAY_KEY, theFactoryGatewayKey)
        emitAll(
            dataStore.data.map { prefs ->
                TheFactorySettings(
                    ipAddress = prefs[theFactoryIpKey].orEmpty(),
                    port = prefs[theFactoryPortKey].orEmpty(),
                    openMode = prefs[theFactoryModeKey].orEmpty(),
                    gatewayKey = readSecureValue(SECURE_GATEWAY_KEY).orEmpty(),
                    gatewayLabel = prefs[theFactoryGatewayLabelKey].orEmpty(),
                    printerSerial = prefs[theFactoryPrinterSerialKey].orEmpty(),
                )
            },
        )
    }

suspend fun LocalStore.saveAllowEditPrices(enabled: Boolean) {
    dataStore.edit { prefs ->
        prefs[allowEditPricesKey] = enabled
    }
}

suspend fun LocalStore.saveAllowDiscounts(enabled: Boolean) {
    dataStore.edit { prefs ->
        prefs[allowDiscountsKey] = enabled
    }
}

fun LocalStore.allowEditPricesFlow(): Flow<Boolean> =
    dataStore.data.map { prefs ->
        prefs[allowEditPricesKey] ?: true
    }

fun LocalStore.allowDiscountsFlow(): Flow<Boolean> =
    dataStore.data.map { prefs ->
        prefs[allowDiscountsKey] ?: true
    }

suspend fun LocalStore.readAllowEditPrices(): Boolean = allowEditPricesFlow().first()

suspend fun LocalStore.readAllowDiscounts(): Boolean = allowDiscountsFlow().first()

/**
 * Flow observable del país seleccionado
 */
fun LocalStore.selectedCountryFlow(): Flow<ServerCountry?> =
    dataStore.data.map { prefs ->
        val code = prefs[selectedCountryKey] ?: com.amaxonia.pos.BuildConfig.DEFAULT_COUNTRY_CODE
        ServerCountries.fromCode(code)
    }

/**
 * Limpia la selección de país (logout o reset)
 */
suspend fun LocalStore.clearSelectedCountry() {
    dataStore.edit { prefs ->
        prefs.remove(selectedCountryKey)
    }
}
