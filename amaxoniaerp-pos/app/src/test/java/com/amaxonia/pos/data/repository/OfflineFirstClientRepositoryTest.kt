package com.amaxonia.pos.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.db.toDomain
import com.amaxonia.pos.data.local.db.toEntity
import com.amaxonia.pos.data.local.saveCompanySession
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.data.remote.ApiConfigManager
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.data.remote.dto.ClientDto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Politica offline-first del directorio de clientes (TASK-082): la caida de red
 * degrada a la caché Room cuando existe y falla con el error original cuando no.
 * El fallo de red se fuerza apuntando el ApiService real a un puerto local
 * cerrado (conexión rechazada determinista, sin red real).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineFirstClientRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var store: LocalStore
    private lateinit var secure: FakeSecureKeyValueStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        secure = FakeSecureKeyValueStore()
        store = LocalStore(context, secure)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `getAllClients con fallo de red sirve la pagina cacheada`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            seedCache(client("1", name = "Ana"), client("2", name = "Pedro"))
            val repository = repository(online = true)

            val result = repository.getAllClients(page = 1, pageSize = 10)

            assertEquals(listOf("Ana", "Pedro"), result.getOrThrow().map { it.firstName })
        }

    @Test
    fun `getAllClients con fallo de red y cache vacia propaga el error`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            val repository = repository(online = true)

            val result = repository.getAllClients(page = 1, pageSize = 10)

            assertTrue(result.isFailure)
        }

    @Test
    fun `getAllClients offline sirve la cache paginada`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            seedCache(client("1", name = "Ana"), client("2", name = "Pedro"))
            val repository = repository(online = false)

            val page1 = repository.getAllClients(page = 1, pageSize = 1).getOrThrow()
            val page2 = repository.getAllClients(page = 2, pageSize = 1).getOrThrow()

            assertEquals("Ana", page1.single().firstName)
            assertEquals("Pedro", page2.single().firstName)
        }

    @Test
    fun `getAllClients offline sin cache falla con error de negocio`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            val repository = repository(online = false)

            val result = repository.getAllClients(page = 1, pageSize = 10)

            assertEquals("No hay empresa seleccionada", result.exceptionOrNull()?.message)
        }

    @Test
    fun `searchClients offline busca en cache por nombre`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            seedCache(client("1", name = "Ana", lastName = "Perez"), client("2", name = "Pedro", lastName = "Lopez"))
            val repository = repository(online = false)

            val result = repository.searchClients("ana").getOrThrow()

            assertEquals(listOf("Ana"), result.map { it.firstName })
        }

    @Test
    fun `getClientById resuelve desde cache sin tocar la red`() =
        runTest {
            seedCache(client("42", name = "Ana"))
            val repository = repository(online = true)

            val found = repository.getClientById("42")
            val missing = repository.getClientById("missing")

            assertEquals("Ana", found.getOrThrow().firstName)
            assertEquals("Cliente no encontrado", missing.exceptionOrNull()?.message)
        }

    @Test
    fun `saveClient sin sesion falla con error de negocio`() =
        runTest {
            val repository = repository(online = true)

            val result = repository.saveClient(domainClient("1", name = "Ana"))

            assertEquals("No hay empresa seleccionada", result.exceptionOrNull()?.message)
        }

    @Test
    fun `getDefaultClient con fallo de red cae al primer cliente cacheado`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            seedCache(client("2", name = "Pedro"), client("1", name = "Ana"))
            val repository = repository(online = true)

            val result = repository.getDefaultClient()

            assertEquals("Ana", result.getOrThrow().firstName)
        }

    @Test
    fun `getAllClients offline respeta el alcance de visibilidad de sucursales`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            db.clientDao().insertAll(
                listOf(
                    entity("1", name = "Cliente Sucursal 10", idSucursal = 10),
                    entity("2", name = "Cliente Sucursal 20", idSucursal = 20),
                ),
            )
            val repository = repository(online = false, scope = com.amaxonia.pos.data.sync.OfflineSyncScope(enabled = true, branchIds = setOf(10)))

            val result = repository.getAllClients(page = 1, pageSize = 10)

            assertEquals(listOf("Cliente Sucursal 10"), result.getOrThrow().map { it.firstName })
        }

    @Test
    fun `searchClients offline respeta el alcance de visibilidad de sucursales`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            db.clientDao().insertAll(
                listOf(
                    entity("1", name = "Juan Sucursal 10", idSucursal = 10),
                    entity("2", name = "Juan Sucursal 20", idSucursal = 20),
                ),
            )
            val repository = repository(online = false, scope = com.amaxonia.pos.data.sync.OfflineSyncScope(enabled = true, branchIds = setOf(10)))

            val result = repository.searchClients("Juan", page = 1, pageSize = 10)

            assertEquals(listOf("Juan Sucursal 10"), result.getOrThrow().map { it.firstName })
        }

    private fun repository(
        online: Boolean,
        scope: com.amaxonia.pos.data.sync.OfflineSyncScope = com.amaxonia.pos.data.sync.OfflineSyncScope.ALL,
    ): OfflineFirstClientRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configManager =
            ApiConfigManager().apply {
                updateBaseUrl(closedPortBaseUrl())
            }
        val apiService = ApiService(ApiClient(configManager))
        return OfflineFirstClientRepository(
            apiService = apiService,
            localStore = store,
            clientDao = db.clientDao(),
            networkMonitor = SettableNetworkMonitor(context, online),
            offlineScopeProvider = { scope },
        )
    }

    private suspend fun seedCache(vararg clients: ClientDto) {
        db.clientDao().insertAll(clients.map { it.toEntity() })
    }

    private fun client(
        id: String,
        name: String,
        lastName: String = "",
    ): ClientDto = ClientDto(id = id, code = id, name = name, lastName = lastName)

    private fun entity(
        id: String,
        name: String,
        lastName: String = "",
        idSucursal: Int? = null,
    ): com.amaxonia.pos.data.local.db.ClientEntity =
        com.amaxonia.pos.data.local.db.ClientEntity(
            id = id,
            code = id,
            identification = "RUC-$id",
            dv = "0",
            name = name,
            lastName = lastName,
            address = "Calle 1",
            phone = "123456",
            email = "client@test.com",
            status = true,
            clientTypeId = 1,
            taxpayerTypeId = 1,
            countryId = 1,
            addressLevel1 = "",
            addressLevel2 = "",
            addressLevel3 = "",
            idSucursal = idSucursal,
        )

    private fun domainClient(
        id: String,
        name: String,
    ) = client(id, name).toEntity().toDomain()
}
