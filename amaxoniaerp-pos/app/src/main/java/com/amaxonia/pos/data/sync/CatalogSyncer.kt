package com.amaxonia.pos.data.sync

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AddressLevel1Dao
import com.amaxonia.pos.data.local.db.AddressLevel2Dao
import com.amaxonia.pos.data.local.db.AddressLevel3Dao
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.db.ClientDao
import com.amaxonia.pos.data.local.db.ClientSucursalDao
import com.amaxonia.pos.data.local.db.ClientTypeDao
import com.amaxonia.pos.data.local.db.CountryDao
import com.amaxonia.pos.data.local.db.ProductDao
import com.amaxonia.pos.data.local.db.PromocionDao
import com.amaxonia.pos.data.local.db.toEntity
import com.amaxonia.pos.data.local.db.toLevel1Entity
import com.amaxonia.pos.data.local.db.toLevel2Entity
import com.amaxonia.pos.data.local.db.toLevel3Entity
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.CatalogPage

class CatalogSyncer(
    private val apiService: ApiService,
    private val localStore: LocalStore,
    daos: CatalogDaos,
) : com.amaxonia.pos.domain.repository.CatalogSynchronization {
    private val clientDao = daos.clientDao
    private val clientSucursalDao = daos.clientSucursalDao
    private val productDao = daos.productDao
    private val countryDao = daos.countryDao
    private val addressLevel1Dao = daos.addressLevel1Dao
    private val addressLevel2Dao = daos.addressLevel2Dao
    private val addressLevel3Dao = daos.addressLevel3Dao
    private val clientTypeDao = daos.clientTypeDao
    private val promocionDao = daos.promocionDao

    private companion object {
        /** Longitud máxima del código de cliente usada como clave de sus sucursales. */
        const val CLIENT_CODE_MAX_LENGTH = 9

        /** Niveles jerárquicos de dirección (país/estado/ciudad) sincronizados. */
        const val ADDRESS_LEVEL_1 = 1
        const val ADDRESS_LEVEL_2 = 2
        const val ADDRESS_LEVEL_3 = 3
    }

    override suspend fun syncAll(pageSize: Int): Result<Unit> {
        val session =
            localStore.readCompanySession()
                ?: return Result.failure(IllegalStateException("No hay empresa seleccionada"))
        return runCatching {
            // Critical POS data first: this is enough for most offline sales flows.
            syncClients(session.token, pageSize)
            syncProducts(session.token, pageSize)
            syncPromotions(session.token)
            localStore.setInitialSyncCompleted(session.company.id, true)

            // Secondary catalogs keep syncing after the app can already operate.
            syncClientTypes(session.token, pageSize)
            syncCountries(session.token, pageSize)
            syncAddressLevels(session.token, pageSize)
        }
    }

    override suspend fun isInitialSyncCompleted(): Boolean {
        val session = localStore.readCompanySession() ?: return false
        return localStore.isInitialSyncCompleted(session.company.id)
    }

    private suspend fun syncClients(
        token: String,
        pageSize: Int,
    ) {
        var offset = 0
        while (true) {
            val response =
                apiService.getClients(
                    token = token,
                    limit = pageSize,
                    offset = offset,
                    search = null,
                    includeTotal = false,
                )
            if (response.data.isEmpty()) break
            clientDao.insertAll(response.data.map { it.toEntity() })
            response.data.forEach { client ->
                val id = client.id ?: return@forEach
                val code = client.code.orEmpty().take(CLIENT_CODE_MAX_LENGTH)
                runCatching {
                    val sucursales = apiService.getClientSucursales(token, id)
                    if (code.isNotBlank()) {
                        clientSucursalDao.deleteByClientCode(code)
                    }
                    clientSucursalDao.insertAll(sucursales.map { it.toEntity() })
                }
            }
            offset += pageSize
        }
    }

    private suspend fun syncProducts(
        token: String,
        pageSize: Int,
    ) {
        var offset = 0
        while (true) {
            val response =
                apiService.getProducts(
                    token = token,
                    page = CatalogPage(limit = pageSize, offset = offset, includeTotal = false),
                )
            if (response.data.isEmpty()) break
            productDao.insertAll(response.data.map { it.toEntity() })
            offset += pageSize
        }
    }

    private suspend fun syncPromotions(token: String) {
        runCatching {
            val promos = apiService.getPromotions(token)
            promocionDao.clearDetalles()
            promocionDao.clearPromociones()
            promocionDao.insertPromociones(promos.map { it.toEntity() })
            promocionDao.insertDetalles(promos.flatMap { promo -> promo.detalle.map { it.toEntity(promo.id) } })
        }
    }

    private suspend fun syncCountries(
        token: String,
        pageSize: Int,
    ) {
        var offset = 0
        while (true) {
            val response =
                apiService.getCountries(
                    token = token,
                    limit = pageSize,
                    offset = offset,
                    includeTotal = false,
                )
            if (response.isEmpty()) break
            countryDao.insertAll(response.map { it.toEntity() })
            offset += pageSize
        }
    }

    private suspend fun syncAddressLevels(
        token: String,
        pageSize: Int,
    ) {
        syncAddressLevel(token, pageSize, ADDRESS_LEVEL_1)
        syncAddressLevel(token, pageSize, ADDRESS_LEVEL_2)
        syncAddressLevel(token, pageSize, ADDRESS_LEVEL_3)
    }

    private suspend fun syncAddressLevel(
        token: String,
        pageSize: Int,
        level: Int,
    ) {
        var offset = 0
        while (true) {
            val response =
                apiService.getAddressLevels(
                    token = token,
                    level = level,
                    limit = pageSize,
                    offset = offset,
                    includeTotal = false,
                )
            if (response.isEmpty()) break
            when (level) {
                ADDRESS_LEVEL_1 -> addressLevel1Dao.insertAll(response.map { it.toLevel1Entity() })
                ADDRESS_LEVEL_2 -> addressLevel2Dao.insertAll(response.map { it.toLevel2Entity() })
                ADDRESS_LEVEL_3 -> addressLevel3Dao.insertAll(response.map { it.toLevel3Entity() })
            }
            offset += pageSize
        }
    }

    private suspend fun syncClientTypes(
        token: String,
        pageSize: Int,
    ) {
        var offset = 0
        while (true) {
            val response =
                apiService.getClientTypes(
                    token = token,
                    limit = pageSize,
                    offset = offset,
                    includeTotal = false,
                )
            if (response.isEmpty()) break
            clientTypeDao.insertAll(response.map { it.toEntity() })
            offset += pageSize
        }
    }
}

/** DAOs de Room que alimentan el caché offline de los catálogos sincronizados. */
data class CatalogDaos(
    val clientDao: ClientDao,
    val clientSucursalDao: ClientSucursalDao,
    val productDao: ProductDao,
    val countryDao: CountryDao,
    val addressLevel1Dao: AddressLevel1Dao,
    val addressLevel2Dao: AddressLevel2Dao,
    val addressLevel3Dao: AddressLevel3Dao,
    val clientTypeDao: ClientTypeDao,
    val promocionDao: PromocionDao,
) {
    companion object {
        fun from(database: AppDatabase): CatalogDaos =
            CatalogDaos(
                clientDao = database.clientDao(),
                clientSucursalDao = database.clientSucursalDao(),
                productDao = database.productDao(),
                countryDao = database.countryDao(),
                addressLevel1Dao = database.addressLevel1Dao(),
                addressLevel2Dao = database.addressLevel2Dao(),
                addressLevel3Dao = database.addressLevel3Dao(),
                clientTypeDao = database.clientTypeDao(),
                promocionDao = database.promocionDao(),
            )
    }
}
