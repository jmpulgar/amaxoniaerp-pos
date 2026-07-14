package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.ProductDao
import com.amaxonia.pos.data.local.db.PromocionCompleta
import com.amaxonia.pos.data.local.db.PromocionDao
import com.amaxonia.pos.data.local.db.toDomain
import com.amaxonia.pos.data.local.db.toEntity
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.Promocion
import com.amaxonia.pos.domain.model.PromocionDetalle
import com.amaxonia.pos.domain.repository.PromotionRepository
import java.math.BigDecimal

class PromotionRepositoryImpl(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    private val promocionDao: PromocionDao,
    private val productDao: ProductDao,
    private val networkMonitor: NetworkMonitor,
) : PromotionRepository {
    override suspend fun syncPromotions(): Result<Unit> {
        val token =
            localStore.readCompanySession()?.token
                ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        if (!networkMonitor.isOnline()) return Result.success(Unit)
        return runCatching {
            val promos = apiService.getPromotions(token)
            promocionDao.clearDetalles()
            promocionDao.clearPromociones()
            promocionDao.insertPromociones(promos.map { it.toEntity() })
            promocionDao.insertDetalles(promos.flatMap { promo -> promo.detalle.map { it.toEntity(promo.id) } })
        }
    }

    override suspend fun getActivePromotionsForProduct(productId: String): Result<List<Promocion>> =
        runCatching {
            promocionDao.getActiveByParentProduct(productId).map { it.toDomainPromotion() }
        }

    override suspend fun getPromotionById(promotionId: String): Result<Promocion> =
        runCatching {
            promocionDao.getById(promotionId)?.toDomainPromotion() ?: error("Promoción no encontrada")
        }

    private suspend fun PromocionCompleta.toDomainPromotion(): Promocion =
        Promocion(
            id = promocion.id,
            codigo = promocion.codigo,
            inicio = promocion.inicio,
            fin = promocion.fin,
            nombre = promocion.nombre,
            imagen = promocion.imagen,
            descuentoGlobal = promocion.descuentoGlobal.bd(),
            idItem = promocion.idItem,
            activo = promocion.activo,
            detalles =
                detalles.map { detalle ->
                    val product = productDao.getById(detalle.idItem)?.toDomain() ?: fallbackProduct(detalle.idItem)
                    val totalConIva = detalle.importe.bd()
                    val impuesto = detalle.impuesto.bd()
                    PromocionDetalle(
                        id = detalle.id,
                        promocionId = detalle.promocionId,
                        idItem = detalle.idItem,
                        productName = product.description.ifBlank { "Producto ${detalle.idItem}" },
                        productCode = product.code,
                        productReference = product.reference,
                        idTipoPrecio = detalle.idTipoPrecio,
                        cantidad = detalle.cantidad.bd(),
                        cantidadTotal = detalle.cantidadTotal.bd(),
                        unidadEmpaque = detalle.unidadEmpaque,
                        descuento = detalle.descuento.bd(),
                        descuentoMonto = detalle.descuentoMonto.bd(),
                        precio = detalle.precio.bd(),
                        impuesto = impuesto,
                        iva = detalle.impuestoPorcentaje.bd(),
                        totalConIva = totalConIva,
                        totalSinIva = totalConIva - impuesto,
                        grupo = detalle.grupo,
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

    private fun Double.bd(): BigDecimal = BigDecimal.valueOf(this)
}
