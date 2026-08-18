package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableFactory
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableVE
import com.amaxoniaerp.features.companies.data.TasasCambioTableFactory
import com.amaxoniaerp.features.companies.data.TasasCambioTableVE
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal
import java.math.RoundingMode

private const val SHORT_CODE_LENGTH = 10
private const val EXCHANGE_RATE_SCALE = 8

internal fun Double.toMoney(): BigDecimal = toScaledBigDecimal(2)

internal fun Double.toScaledBigDecimal(scale: Int): BigDecimal =
    BigDecimal.valueOf(this).setScale(scale, RoundingMode.HALF_UP)

internal data class MonetaryContext(
    val countryCode: String,
    val multiMoneda: String,
    val tasa: BigDecimal,
    val idTasa: Int,
    val monedaBase: Int,
    val abrMonedaBase: String,
    val monedaSecundaria: Int,
    val abrMonedaSecundaria: String,
    val totalRef: Double,
    val validarStock: String,
    val defaultTaxRate: Double,
    val defaultFormaPagoId: Int,
    val diasVencimiento: Int,
) {
    fun toBase(amountRef: Double): BigDecimal {
        val normalizedRef = BigDecimal.valueOf(amountRef)
        return if (multiMoneda == "SI") {
            normalizedRef.multiply(tasa).setScale(2, RoundingMode.HALF_UP)
        } else {
            normalizedRef.setScale(2, RoundingMode.HALF_UP)
        }
    }

    fun toBase(amountRef: BigDecimal): BigDecimal {
        val normalizedRef = amountRef.setScale(2, RoundingMode.HALF_UP)
        return if (multiMoneda == "SI") {
            normalizedRef.multiply(tasa).setScale(2, RoundingMode.HALF_UP)
        } else {
            normalizedRef
        }
    }

    fun shouldValidateStock(): Boolean = validarStock.trim().equals("SI", ignoreCase = true)
}

internal fun resolveMonetaryContext(
    countryCode: String,
    request: ProcessSaleRequest,
): MonetaryContext {
    val pgTable = ParametrosGeneralesTableFactory.forCountry(countryCode)
    val params = loadParamsGenerales(pgTable)

    val paramsMulti =
        pgTable is ParametrosGeneralesTableVE && params[pgTable.multiMoneda].equals("Si", ignoreCase = true)
    val multiMoneda = if (paramsMulti) "SI" else "NO"

    val monedaBase = params[pgTable.monedaBase] ?: 1
    val abrMonedaBase = params[pgTable.abrMonedaBase].take(SHORT_CODE_LENGTH)
    val monedaSecundaria =
        if (pgTable is ParametrosGeneralesTableVE) {
            params[pgTable.monedaSecundaria]
        } else {
            monedaBase
        }
    val abrMonedaSecundaria =
        if (pgTable is ParametrosGeneralesTableVE) {
            params[pgTable.abrMonedaSecundaria].take(SHORT_CODE_LENGTH)
        } else {
            abrMonedaBase
        }

    val (tasa, idTasa) = resolveTasaAndId(countryCode, request, paramsMulti, monedaSecundaria, monedaBase)

    return MonetaryContext(
        countryCode = countryCode,
        multiMoneda = multiMoneda,
        tasa = BigDecimal.valueOf(tasa).setScale(EXCHANGE_RATE_SCALE, RoundingMode.HALF_UP),
        idTasa = idTasa,
        monedaBase = monedaBase,
        abrMonedaBase = abrMonedaBase,
        monedaSecundaria = monedaSecundaria,
        abrMonedaSecundaria = abrMonedaSecundaria,
        totalRef = request.moneda?.totalRef ?: request.factura.totalTotalFactura,
        validarStock = params[pgTable.validarStock],
        defaultTaxRate = params[pgTable.porcentajeImpuestoPrincipal].toDouble(),
        defaultFormaPagoId = params[pgTable.defaultIdFormaPagoFactura],
        diasVencimiento = params[pgTable.diasVencimiento],
    )
}

private fun loadParamsGenerales(pgTable: com.amaxoniaerp.features.companies.data.BaseParametrosGeneralesTable) =
    pgTable
        .selectAll()
        .orderBy(pgTable.codEmpresa)
        .limit(1)
        .firstOrNull()
        ?: throw InvalidSaleRequestException("No se encontró parametros_generales")

private fun resolveTasaAndId(
    countryCode: String,
    request: ProcessSaleRequest,
    paramsMulti: Boolean,
    monedaSecundaria: Int,
    monedaBase: Int,
): Pair<Double, Int> {
    val providedMoneda = request.moneda
    val tasaFromRequest = providedMoneda?.tasa?.takeIf { it > 0.0 }
    val idTasaFromRequest = providedMoneda?.idTasa?.takeIf { it > 0 }

    val tasasTable = TasasCambioTableFactory.forCountry(countryCode)
    val faltaParametrosTasa = paramsMulti && (tasaFromRequest == null || idTasaFromRequest == null)
    val tasaRow =
        if (faltaParametrosTasa && tasasTable is TasasCambioTableVE) {
            tasasTable
                .select(tasasTable.id, tasasTable.tasaInversa)
                .where {
                    (tasasTable.divisa eq monedaSecundaria) and
                        (tasasTable.monedabase eq monedaBase)
                }.orderBy(tasasTable.id to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
        } else {
            null
        }

    val tasa =
        if (paramsMulti) {
            tasaFromRequest
                ?: tasaRow?.get(tasasTable.tasaInversa)?.toDouble()
                ?: throw InvalidSaleRequestException("No se encontró tasa de cambio vigente")
        } else {
            1.0
        }

    val idTasa =
        if (paramsMulti) {
            idTasaFromRequest
                ?: tasaRow?.get(tasasTable.id)?.toInt()
                ?: throw InvalidSaleRequestException("No se encontró id de tasa vigente")
        } else {
            0
        }
    return tasa to idTasa
}
