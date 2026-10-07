package com.amaxonia.erp.data.repository

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.dto.PromocionDetalleDto
import com.amaxonia.erp.data.remote.dto.PromocionDto
import com.amaxonia.erp.data.remote.getPromotions
import com.amaxonia.erp.domain.model.PriceLevel
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.domain.model.Promocion
import com.amaxonia.erp.domain.model.PromocionDetalle
import com.amaxonia.erp.domain.repository.ProductRepository
import com.amaxonia.erp.domain.repository.PromotionRepository
import java.math.BigDecimal

class PromotionRepositoryImpl(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val productRepository: ProductRepository,
) : PromotionRepository {

    private var cachedPromotions: List<Promocion>? = null

    override suspend fun getPromotions(forceRefresh: Boolean): Result<List<Promocion>> = runCatching {
        if (!forceRefresh && cachedPromotions != null) {
            return@runCatching cachedPromotions!!
        }
        val token = localStore.readCompanySession()?.token
            ?: return@runCatching emptyList()

        val dtos = apiService.getPromotions(token)
        val allProducts = productRepository.getAllProducts(page = 1, pageSize = 300)
            .getOrElse { emptyList() }
        val productsMap = allProducts.associateBy { it.id }

        val domainPromos = dtos.map { it.toDomain(productsMap) }
        cachedPromotions = domainPromos
        domainPromos
    }

    override suspend fun getActivePromotionsForProduct(productId: String): Result<List<Promocion>> = runCatching {
        val all = getPromotions().getOrElse { emptyList() }
        all.filter { promo ->
            promo.activo && (promo.idItem == productId || promo.detalles.any { it.idItem == productId })
        }
    }

    private fun PromocionDto.toDomain(productsMap: Map<String, Product>): Promocion =
        Promocion(
            id = id,
            codigo = codigo,
            inicio = inicio,
            fin = fin,
            nombre = promocion,
            imagen = imagen,
            descuentoGlobal = BigDecimal.valueOf(descuentoGlobal),
            idItem = idItem,
            activo = activo,
            detalles = detalle.map { d ->
                val product = productsMap[d.idItem] ?: fallbackProduct(d.idItem)
                val totalConIva = BigDecimal.valueOf(d.importe)
                val impuesto = BigDecimal.valueOf(d.impuesto)
                PromocionDetalle(
                    id = d.idPromocionDetalle,
                    promocionId = id,
                    idItem = d.idItem,
                    productName = product.description.ifBlank { "Producto ${d.idItem}" },
                    productCode = product.code,
                    productReference = product.reference,
                    idTipoPrecio = d.idTipoPrecio,
                    cantidad = BigDecimal.valueOf(d.cantidad),
                    cantidadTotal = BigDecimal.valueOf(d.cantidadTotal),
                    unidadEmpaque = d.unidadEmpaque,
                    descuento = BigDecimal.valueOf(d.descuento),
                    descuentoMonto = BigDecimal.valueOf(d.descuentoMonto),
                    precio = BigDecimal.valueOf(d.precio),
                    impuesto = impuesto,
                    iva = BigDecimal.valueOf(d.resolvedTaxPercent),
                    totalConIva = totalConIva,
                    totalSinIva = totalConIva - impuesto,
                    grupo = d.grupo,
                    product = product,
                )
            },
        )

    private fun fallbackProduct(id: String): Product =
        Product(
            id = id,
            description = "Producto $id",
            prices = listOf(PriceLevel(label = "A")),
        )
}
