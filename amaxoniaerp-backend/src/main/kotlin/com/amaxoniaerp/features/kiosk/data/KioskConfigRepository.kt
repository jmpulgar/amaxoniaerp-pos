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
import java.net.URLEncoder

/**
 * Configuración del kiosco: ajustes defensivos de `parametros_generales` ([KioskSettingsRepository]),
 * medios del attract loop desde los banners del ERP y multimoneda. `version` la calcula
 * [com.amaxoniaerp.features.kiosk.application.KioskService] a partir del contenido.
 */
class KioskConfigRepository(
    private val settingsRepository: KioskSettingsRepository = KioskSettingsRepository(),
) {
    suspend fun getKioskConfig(
        database: Database,
        countryCode: String,
        companyDb: String,
    ): KioskConfigResponse {
        val settings = settingsRepository.load(database)
        val currencyConfig = dbQuery(database) { resolveCurrencyConfig(countryCode) }
        val normalizedCountry = countryCode.uppercase()

        return KioskConfigResponse(
            version = 0,
            brandColor = null,
            logoUrl = null,
            media = settings.mediaFiles.map { toMediaItem(it, normalizedCountry, companyDb) },
            diningModes = settings.diningModes,
            dispatch = settings.dispatch,
            defaultCustomerId = settings.defaultCodClienteFactura ?: DEFAULT_CUSTOMER_CODE,
            currency = currencyConfig,
            country = normalizedCountry,
            paymentMethods = listOf(KioskPaymentMethod.CARD),
        )
    }

    private fun toMediaItem(
        storedValue: String,
        countryCode: String,
        companyDb: String,
    ): KioskMediaItem {
        val filename = storedValue.replace('\\', '/').substringAfterLast('/').ifBlank { storedValue }
        val isVideo = filename.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
        val encoded = URLEncoder.encode(filename, Charsets.UTF_8).replace("+", "%20")
        return KioskMediaItem(
            type = if (isVideo) MEDIA_VIDEO else MEDIA_IMAGE,
            url = "/api/data/$countryCode/$companyDb/banners/$encoded",
            durationSec = if (isVideo) 0 else IMAGE_DURATION_SEC,
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
        val tasaRow =
            if (tasasTableVE != null) {
                tasasTableVE
                    .selectAll()
                    .where {
                        (tasasTableVE.divisa eq monedaSecundariaId) and
                            (tasasTableVE.monedabase eq monedaBaseId)
                    }.orderBy(tasasTableVE.id to SortOrder.DESC)
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

private const val DEFAULT_CUSTOMER_CODE = "CF"
private const val MEDIA_IMAGE = "IMAGE"
private const val MEDIA_VIDEO = "VIDEO"
private const val IMAGE_DURATION_SEC = 8
private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov")
