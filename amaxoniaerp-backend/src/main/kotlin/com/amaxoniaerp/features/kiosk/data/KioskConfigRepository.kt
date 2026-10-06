package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableFactory
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTableVE
import com.amaxoniaerp.features.companies.data.TasasCambioTableFactory
import com.amaxoniaerp.features.companies.data.TasasCambioTableVE
import com.amaxoniaerp.features.kiosk.domain.KioskConfigResponse
import com.amaxoniaerp.features.kiosk.domain.KioskCurrencyConfig
import com.amaxoniaerp.features.kiosk.domain.KioskMediaItem
import com.amaxoniaerp.features.kiosk.domain.KioskPaymentMethod
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.math.BigDecimal

class KioskConfigRepository {

    suspend fun getKioskConfig(
        database: Database,
        countryCode: String,
        companyDb: String,
    ): KioskConfigResponse = dbQuery(database) {
        // 1. Parametros Kiosco
        val paramsRow = KioskParametrosTable
            .selectAll()
            .limit(1)
            .singleOrNull()

        val version = paramsRow?.get(KioskParametrosTable.kioscoConfigVersion) ?: 1
        val brandColor = paramsRow?.get(KioskParametrosTable.kioscoColorMarca)
        val dispatch = paramsRow?.get(KioskParametrosTable.kioscoDestinoPedido) ?: "RETIRO_MOSTRADOR"
        val modalidadesRaw = paramsRow?.get(KioskParametrosTable.kioscoModalidades) ?: "COMER_AQUI,PARA_LLEVAR"
        val diningModes = modalidadesRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val defaultCustomerId = paramsRow?.get(KioskParametrosTable.defaultCodClienteFactura) ?: "CF"

        // 2. Media
        val mediaList = KioskMediaTable
            .selectAll()
            .where { KioskMediaTable.activo eq true }
            .orderBy(KioskMediaTable.orden to SortOrder.ASC)
            .map { row ->
                val archivo = row[KioskMediaTable.archivo]
                val type = row[KioskMediaTable.tipo].uppercase()
                KioskMediaItem(
                    type = type,
                    url = "/api/data/$countryCode/$companyDb/banners/$archivo",
                    durationSec = row[KioskMediaTable.duracionSeg],
                )
            }

        // 3. Multimoneda y tasas
        val currencyConfig = resolveCurrencyConfig(countryCode)

        KioskConfigResponse(
            version = version,
            brandColor = brandColor,
            logoUrl = null,
            media = mediaList,
            diningModes = diningModes,
            dispatch = dispatch,
            defaultCustomerId = defaultCustomerId,
            currency = currencyConfig,
            country = countryCode.uppercase(),
            paymentMethods = listOf(KioskPaymentMethod.CARD),
        )
    }

    private fun resolveCurrencyConfig(countryCode: String): KioskCurrencyConfig {
        val normalizedCountry = countryCode.uppercase()
        if (normalizedCountry != "VE") {
            return KioskCurrencyConfig(
                base = "USD",
                secondary = null,
                rate = "1.0000",
            )
        }

        // Venezuela
        val paramsTable = ParametrosGeneralesTableFactory.forCountry("VE")
        val paramsVE = paramsTable as? ParametrosGeneralesTableVE
        val paramsRow = paramsTable.selectAll().limit(1).singleOrNull()

        if (paramsVE == null || paramsRow == null) {
            return KioskCurrencyConfig(
                base = "USD",
                secondary = null,
                rate = "1.0000",
            )
        }

        val multiMonedaVal = paramsRow[paramsVE.multiMoneda]
        val multiMonedaEnabled = multiMonedaVal.equals("SI", ignoreCase = true) || multiMonedaVal == "1"

        if (!multiMonedaEnabled) {
            return KioskCurrencyConfig(
                base = paramsRow[paramsVE.abrMonedaBase].ifBlank { "USD" },
                secondary = null,
                rate = "1.0000",
            )
        }

        val monedaBaseId = paramsRow[paramsVE.monedaBase] ?: 1
        val monedaSecundariaId = paramsRow[paramsVE.monedaSecundaria]
        val secondaryAbr = paramsRow[paramsVE.abrMonedaSecundaria].ifBlank { "VES" }

        val tasasTableVE = TasasCambioTableFactory.forCountry("VE") as? TasasCambioTableVE
        val tasaRow = if (tasasTableVE != null) {
            tasasTableVE
                .selectAll()
                .where {
                    (tasasTableVE.divisa eq monedaSecundariaId) and
                        (tasasTableVE.monedabase eq monedaBaseId)
                }
                .orderBy(tasasTableVE.id to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
        } else {
            null
        }

        val rateValue = tasaRow?.get(tasasTableVE!!.tasaInversa) ?: BigDecimal("1.0000")

        return KioskCurrencyConfig(
            base = "USD",
            secondary = secondaryAbr,
            rate = rateValue.stripTrailingZeros().toPlainString(),
        )
    }
}
