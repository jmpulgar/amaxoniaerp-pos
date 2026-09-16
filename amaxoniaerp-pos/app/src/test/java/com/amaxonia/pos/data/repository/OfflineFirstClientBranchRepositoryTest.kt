package com.amaxonia.pos.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.data.local.db.ClientSucursalEntity
import com.amaxonia.pos.data.local.saveCompanySession
import com.amaxonia.pos.data.remote.ApiClient
import com.amaxonia.pos.data.remote.ApiConfigManager
import com.amaxonia.pos.data.remote.ApiService
import com.amaxonia.pos.domain.model.Client
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineFirstClientBranchRepositoryTest {
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
    fun `findFor offline sirve las sucursales cacheadas en Room`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            seedCache(
                branchEntity(1, "C001", "Sucursal Norte"),
                branchEntity(2, "C001", "Sucursal Sur"),
            )
            val repository = repository(baseUrl = closedPortBaseUrl(), online = false)

            val branches = repository.findFor(Client(id = "c1", code = "C001"))

            assertEquals(2, branches.size)
            assertEquals(listOf("Sucursal Norte", "Sucursal Sur"), branches.map { it.nombreSucursal })
        }

    @Test
    fun `findFor con fallo de red hace fallback a las sucursales cacheadas`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            seedCache(
                branchEntity(5, "C002", "Sucursal Este"),
            )
            val repository = repository(baseUrl = closedPortBaseUrl(), online = true)

            val branches = repository.findFor(Client(id = "c2", code = "C002"))

            assertEquals(1, branches.size)
            assertEquals("Sucursal Este", branches.single().nombreSucursal)
        }

    @Test
    fun `findFor con fallo de red y sin cache retorna lista vacia`() =
        runTest {
            store.saveCompanySession(testCompanySession())
            val repository = repository(baseUrl = closedPortBaseUrl(), online = true)

            val branches = repository.findFor(Client(id = "c3", code = "C003"))

            assertTrue(branches.isEmpty())
        }

    @Test
    fun `findFor online con respuesta remota exitosa guarda en Room y retorna sucursales remotas`() =
        kotlinx.coroutines.runBlocking {
            store.saveCompanySession(testCompanySession())
            val server = ServerSocket(0)
            val port = server.localPort
            val thread =
                Thread {
                    runCatching {
                        val socket = server.accept()
                        val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val body =
                            """[{"sucursalId":10,"clienteCodigo":"C004","nombreSucursal":"Remota 1","direccion":"Calle 50"},{"sucursalId":11,"clienteCodigo":"C004","nombreSucursal":"Remota 2","direccion":"Via Espana"}]"""
                        val bytes = body.toByteArray(Charsets.UTF_8)
                        val out = socket.getOutputStream()
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8))
                        out.write(bytes)
                        out.flush()
                        socket.close()
                    }
                }
            thread.start()

            try {
                val baseUrl = "http://127.0.0.1:$port/"
                val repository = repository(baseUrl = baseUrl, online = true)

                val branches = repository.findFor(Client(id = "c4", code = "C004"))

                assertEquals(2, branches.size)
                assertEquals(listOf("Remota 1", "Remota 2"), branches.map { it.nombreSucursal })

                // Verificar que se persistio en la base de datos Room local
                val cached = db.clientSucursalDao().getByClientCode("C004")
                assertEquals(2, cached.size)
                assertEquals(listOf("Remota 1", "Remota 2"), cached.map { it.nombreSucursal })
            } finally {
                server.close()
                thread.join(1000)
            }
        }

    private fun repository(
        baseUrl: String,
        online: Boolean,
    ): OfflineFirstClientBranchRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configManager =
            ApiConfigManager().apply {
                updateBaseUrl(baseUrl)
            }
        val apiService = ApiService(ApiClient(configManager))
        return OfflineFirstClientBranchRepository(
            apiService = apiService,
            localStore = store,
            dao = db.clientSucursalDao(),
            networkMonitor = SettableNetworkMonitor(context, online),
        )
    }

    private suspend fun seedCache(vararg branches: ClientSucursalEntity) {
        db.clientSucursalDao().insertAll(branches.toList())
    }

    private fun branchEntity(
        id: Int,
        clientCode: String,
        name: String,
    ) = ClientSucursalEntity(
        sucursalId = id,
        clienteCodigo = clientCode,
        nombreSucursal = name,
    )
}
