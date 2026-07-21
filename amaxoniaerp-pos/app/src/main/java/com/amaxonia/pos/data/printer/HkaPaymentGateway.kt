package com.amaxonia.pos.data.printer

import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.payment.EXTERNAL_GATEWAY_MARKER
import com.amaxonia.pos.domain.model.payment.GatewayApproval
import com.amaxonia.pos.domain.model.payment.GatewayLaunchPayload
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.repository.PaymentGateway

class HkaPaymentGateway(
    private val client: TheFactoryRapidPayClient,
    private val localStore: LocalStore,
) : PaymentGateway {
    override suspend fun validateConfiguration(methods: List<TransactionPaymentMethod>): Result<Unit> =
        runCatching {
            if (methods.none(::requiresGateway)) return@runCatching
            val configuration = readConfiguration()
            check(configuration.gatewayKey.isNotBlank()) {
                "Configura una pasarela HKA en configuración de impresora"
            }
            check(configuration.commerceRif.isNotBlank()) {
                "No se encontró RIF comercio desde parametros_generales.rif"
            }
        }

    override suspend fun prepare(
        method: TransactionPaymentMethod,
        customerIdentifier: String,
        exchangeRate: Double,
        isMultiCurrency: Boolean,
    ): Result<GatewayLaunchPayload?> {
        if (!isEnabled() || !requiresGateway(method)) return Result.success(null)
        val configuration = readConfiguration()
        val amount = resolveAmount(method.amount, exchangeRate, isMultiCurrency)
        SafeLog.d(TAG, "Preparing external gateway payment")
        return client
            .buildGatewayLaunchPayload(
                amount = amount,
                commandPrefix = "K${configuration.gatewayKey}V",
                customerIdentifier = customerIdentifier,
                commerceRif = configuration.commerceRif,
            ).map { it }
    }

    override suspend fun awaitApproval(): GatewayApproval {
        val result = RapidPayBridge.awaitResult()
        return GatewayApproval(approved = result.approved, message = result.message)
    }

    private suspend fun isEnabled(): Boolean {
        if (localStore.readSelectedPrinterType() != PrinterType.THE_FACTORY_HKA) return false
        val mode = localStore.readTheFactorySettings().openMode.trim()
        return mode.isBlank() || mode.equals("HKA20", ignoreCase = true)
    }

    private suspend fun readConfiguration(): Configuration {
        val settings = localStore.readTheFactorySettings()
        val commerceRif =
            localStore
                .readCompanySession()
                ?.company
                ?.rif
                .orEmpty()
                .trim()
        return Configuration(settings.gatewayKey.trim(), commerceRif)
    }

    private fun requiresGateway(method: TransactionPaymentMethod): Boolean = method.gatewayCommandPrefix == EXTERNAL_GATEWAY_MARKER

    internal fun resolveAmount(
        amount: Double,
        exchangeRate: Double,
        isMultiCurrency: Boolean,
    ): Double =
        // Auditoría ítem 8 (MONEY-001): delegate to the stateless helper so
        // the rounding rule is centralized and unit-tested in pure JVM.
        GatewayCurrencyConversion.apply(
            amount = amount,
            exchangeRate = exchangeRate,
            isMultiCurrency = isMultiCurrency,
        )

    private data class Configuration(
        val gatewayKey: String,
        val commerceRif: String,
    )

    companion object {
        private const val TAG = "HkaPaymentGateway"
    }
}
