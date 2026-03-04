package com.amaxonia.pos.domain.model.caja

import com.amaxonia.pos.domain.model.seller.Seller
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class Caja(
    val idCaja: String,
    val codCaja: String?,
    val descripcion: String?,
    val estatus: Int,
    val idSucursal: Int?,
    val codAlmacen: Int? = null,
    @SerialName("default_warehouse_id")
    val defaultWarehouseId: Int? = null,
    @SerialName("default_vendedor_id")
    val defaultSellerId: Int? = null,
    @SerialName("default_vendedor_name")
    val defaultSellerName: String? = null,
    @SerialName("available_sellers")
    val availableSellers: List<Seller> = emptyList(),
    @SerialName("serie_sucursal")
    val serieSucursal: String? = null,
    @SerialName("default_tax_rate")
    val defaultTaxRate: Double? = null,
    @SerialName("default_forma_pago_id")
    val defaultFormaPagoId: Int? = null,
    val currency: CurrencyConfig? = null,
    val serieCaja: String,
    val sucursalNombre: String? = null,
    val sucursalCodigo: String? = null,
    val codigoSucursalEmisor: String? = null,
)

@Serializable
data class CurrencyConfig(
    @SerialName("multi_moneda")
    val multiMoneda: String,
    val tasa: Double,
    @SerialName("id_tasa")
    val idTasa: Int,
    @SerialName("moneda_base")
    val monedaBase: Int,
    @SerialName("abr_moneda_base")
    val abrMonedaBase: String,
    @SerialName("moneda_secundaria")
    val monedaSecundaria: Int,
    @SerialName("abr_moneda_secundaria")
    val abrMonedaSecundaria: String,
)
