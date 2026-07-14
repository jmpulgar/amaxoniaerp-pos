package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.domain.repository.ProductLotAvailability
import com.amaxonia.pos.domain.repository.ProductLotConfiguration
import com.amaxonia.pos.domain.repository.ProductLotRepository

class RemoteProductLotRepository(
    private val apiService: ApiService,
    private val localStore: LocalStore,
) : ProductLotRepository {
    override suspend fun getForProduct(productId: String): Result<ProductLotConfiguration> =
        runCatching {
            val session =
                localStore.readCompanySession()
                    ?: error("No hay empresa seleccionada")
            val response = apiService.getItemLots(session.token, productId)
            ProductLotConfiguration(
                isConfigured = response.poseeConfiguracionLote,
                lots =
                    response.lotes.map { lot ->
                        ProductLotAvailability(
                            id = lot.idLoteItem,
                            code = lot.codigoLoteItem,
                            expiration = lot.vencimiento,
                            availableQuantity = lot.disponibilidad,
                            warehouseId = lot.idAlmacen,
                        )
                    },
            )
        }
}
