package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.FormaPagoRepository
import com.amaxonia.pos.domain.repository.PaymentCountryReader
import kotlinx.coroutines.flow.first

data class PaymentCurrencyConfiguration(
    val exchangeRate: Double,
    val secondaryCurrency: String,
    val isMultiCurrency: Boolean,
)

sealed interface PaymentMethodsResult {
    data class Loaded(
        val methods: List<FormaPago>,
    ) : PaymentMethodsResult

    data class Failed(
        val message: String,
    ) : PaymentMethodsResult
}

data class PaymentContext(
    val methods: PaymentMethodsResult,
    val currency: PaymentCurrencyConfiguration,
)

class LoadPaymentContextUseCase(
    private val activeCajaReader: ActiveCajaReader,
    private val formaPagoRepository: FormaPagoRepository,
) {
    suspend operator fun invoke(): PaymentContext {
        val caja = activeCajaReader.activeCaja.first()
        val methods =
            formaPagoRepository.getFormasPago(caja?.idCaja).fold(
                onSuccess = { values ->
                    PaymentMethodsResult.Loaded(
                        values.sortedWith(compareBy<FormaPago> { it.orden }.thenBy { it.codigo.orEmpty() }),
                    )
                },
                onFailure = { error ->
                    PaymentMethodsResult.Failed(error.message ?: "No se pudieron cargar las formas de pago")
                },
            )
        val currency = caja?.currency
        val isMultiCurrency = currency?.multiMoneda.equals("SI", ignoreCase = true)
        return PaymentContext(
            methods = methods,
            currency =
                PaymentCurrencyConfiguration(
                    exchangeRate = if (isMultiCurrency) currency?.tasa?.takeIf { it > 0.0 } ?: 0.0 else 0.0,
                    secondaryCurrency = if (isMultiCurrency) currency?.abrMonedaSecundaria.orEmpty() else "",
                    isMultiCurrency = isMultiCurrency,
                ),
        )
    }
}

class LoadPaymentCountryUseCase(
    private val countryReader: PaymentCountryReader,
) {
    suspend operator fun invoke(): String = countryReader.currentCountryCode()
}
