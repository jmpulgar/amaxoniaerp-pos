package com.amaxonia.erp.data.repository

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.local.db.ProductDao
import com.amaxonia.erp.data.local.db.PromocionDao
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.NetworkMonitor
import com.amaxonia.erp.data.remote.dto.PromocionDetalleDto
import com.amaxonia.erp.data.remote.dto.PromocionDto
import com.amaxonia.erp.data.remote.getPromotions
import com.amaxonia.erp.data.sync.toDomain
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
    private val promocionDao: PromocionDao? = null,
    private val productDao: ProductDao? = null,
    private val networkMonitor: NetworkMonitor? = null,
) : PromotionRepository {

    private var cachedPromotions: List<Promocion>? = null

    override suspend fun getPromotions(forceRefresh: Boolean): Result<List<Promocion>> = runCatching {
        if (!forceRefresh && cachedPromotions != null) {
            return@runCatching cachedPromotions!!
        }

        val isOnline = networkMonitor?.isOnline() ?: true
        if (!isOnline) {
            val fromRoom = loadFromRoom()
            if (fromRoom.isNotEmpty()) {
                cachedPromotions = fromRoom
                return@runCatching fromRoom
            }
        }

        val token = localStore.readCompanySession()?.token
        if (token == null) {
            val fromRoom = loadFromRoom()
            cachedPromotions = fromRoom
            return@runCatching fromRoom
        }

        val dtos = try {
            apiService.getPromotions(token)
        } catch (e: Exception) {
            val fromRoom = loadFromRoom()
            if (fromRoom.isNotEmpty()) {
                cachedPromotions = fromRoom
                return@runCatching fromRoom
            }
            throw e
        }

        val neededProductIds = dtos.flatMap { p -> p.detalle.map { it.idItem } + listOfNotNull(p.idItem) }
            .filter { it.isNotBlank() }
            .toSet()
        val productsMap = mutableMapOf<String, Product>()
        for (id in neededProductIds) {
            val local = productDao?.getById(id)?.toDomain()
                ?: productRepository.getAllProducts(page = 1, pageSize = 20).getOrNull()?.firstOrNull { it.id == id }
            if (local != null) {
                productsMap[id] = local
            }
        }

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

    private suspend fun loadFromRoom(): List<Promocion> {
        val dao = promocionDao ?: return emptyList()
        val entities = dao.getAllActive()
        if (entities.isEmpty()) return emptyList()

        val productsMap = mutableMapOf<String, Product>()

        return entities.map { promoEntity ->
            val detalles = dao.getDetallesForPromocion(promoEntity.id)
            Promocion(
                id = promoEntity.id,
                codigo = promoEntity.codigo,
                inicio = promoEntity.inicio,
                fin = promoEntity.fin,
                nombre = promoEntity.nombre,
                imagen = promoEntity.imagen,
                descuentoGlobal = BigDecimal.valueOf(promoEntity.descuentoGlobal),
                idItem = promoEntity.idItem,
                activo = promoEntity.activo,
                detalles = detalles.map { d ->
                    val product = productsMap[d.idItem]
                        ?: productDao?.getById(d.idItem)?.toDomain()?.also { productsMap[d.idItem] = it }
                        ?: productRepository.getAllProducts(page = 1, pageSize = 20).getOrNull()?.firstOrNull { it.id == d.idItem }?.also { productsMap[d.idItem] = it }
                        ?: fallbackProduct(d.idItem)

                    val totalConIva = BigDecimal.valueOf(d.importe)
                    val impuesto = BigDecimal.valueOf(d.impuesto)
                    PromocionDetalle(
                        id = d.id,
                        promocionId = d.promocionId,
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
                        iva = BigDecimal.valueOf(d.impuestoPorcentaje),
                        totalConIva = totalConIva,
                        totalSinIva = totalConIva - impuesto,
                        grupo = d.grupo,
                        product = product,
                    )
                },
            )
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
