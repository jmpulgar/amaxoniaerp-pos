package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CurrencyConfig
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.FormaPagoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LoadPaymentContextUseCaseTest {
    @Test
    fun `loads ordered methods and legacy multi-currency configuration`() =
        runTest {
            val caja = caja(currency = currency("SI", rate = 36.5))
            val repository = RecordingFormaPagoRepository(Result.success(listOf(method(2, 20, "B"), method(1, 10, "A"))))

            val context = LoadPaymentContextUseCase(activeCajaReader(caja), repository)()

            assertEquals("caja-1", repository.requestedCajaId)
            assertEquals(listOf(1, 2), (context.methods as PaymentMethodsResult.Loaded).methods.map { it.idFormaPago })
            assertEquals(PaymentCurrencyConfiguration(36.5, "Bs.", true), context.currency)
        }

    @Test
    fun `method failure keeps currency and fallback error behavior`() =
        runTest {
            val repository = RecordingFormaPagoRepository(Result.failure(IllegalStateException()))

            val context = LoadPaymentContextUseCase(activeCajaReader(null), repository)()

            assertEquals(
                PaymentMethodsResult.Failed("No se pudieron cargar las formas de pago"),
                context.methods,
            )
            assertFalse(context.currency.isMultiCurrency)
            assertEquals(0.0, context.currency.exchangeRate, 0.0)
        }

    private class RecordingFormaPagoRepository(
        private val result: Result<List<FormaPago>>,
    ) : FormaPagoRepository {
        var requestedCajaId: String? = null

        override suspend fun getFormasPago(cajaId: String?): Result<List<FormaPago>> {
            requestedCajaId = cajaId
            return result
        }
    }

    private fun method(
        id: Int,
        order: Int,
        code: String,
    ) = FormaPago(id, codigo = code, activo = 1, pos = 1, grupo = 1, orden = order, tipoMoneda = "BASE")

    private fun caja(currency: CurrencyConfig?) =
        Caja(
            idCaja = "caja-1",
            codCaja = "1",
            descripcion = "Caja",
            estatus = 1,
            idSucursal = 1,
            currency = currency,
            serieCaja = "A",
        )

    private fun currency(
        multiCurrency: String,
        rate: Double,
    ) = CurrencyConfig(multiCurrency, rate, 1, 1, "USD", 2, "Bs.")
}

private fun activeCajaReader(caja: Caja?): ActiveCajaReader =
    object : ActiveCajaReader {
        override val activeCaja = MutableStateFlow(caja)
    }
