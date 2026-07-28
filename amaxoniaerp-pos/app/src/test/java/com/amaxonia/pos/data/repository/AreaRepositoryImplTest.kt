package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.remote.api.AreasApi
import com.amaxonia.pos.domain.model.mesas.Area
import com.amaxonia.pos.domain.model.mesas.AreasResponse
import com.amaxonia.pos.domain.model.mesas.AreasResult
import com.amaxonia.pos.domain.model.mesas.Lienzo
import com.amaxonia.pos.domain.model.mesas.Mesa
import com.amaxonia.pos.domain.model.mesas.MesasResponse
import com.amaxonia.pos.domain.model.mesas.MesasResult
import com.amaxonia.pos.domain.repository.CompanyTokenReader
import com.amaxonia.pos.domain.repository.ConnectivityStatus
import com.amaxonia.pos.domain.repository.SalonConfigCache
import com.amaxonia.pos.domain.repository.SessionConfigurationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AreaRepositoryImplTest {
    @Test
    fun `online guarda el snapshot de la caja consultada`() =
        runTest {
            val cache = FakeSalonConfigCache()
            val repository = repository(cache = cache)

            val result = repository.getAreas(CAJA_A).getOrThrow()

            assertEquals(7, result.sucursalId)
            assertEquals(listOf(1, 2), result.areas.map { it.id })
            assertFalse(result.fromCache)
            assertEquals(CAJA_A to 7, cache.savedAreas?.let { it.cajaId to it.sucursalId })
        }

    @Test
    fun `sin conexion devuelve el snapshot de esa caja sin llamar a la red`() =
        runTest {
            val api = FakeAreasApi()
            val cache = FakeSalonConfigCache(cachedAreasByCaja = mapOf(CAJA_A to AreasResult(7, AREAS, fromCache = true)))
            val repository = repository(api = api, cache = cache, online = false)

            val result = repository.getAreas(CAJA_A).getOrThrow()

            assertTrue(result.fromCache)
            assertEquals(listOf(1, 2), result.areas.map { it.id })
            assertEquals(0, api.areaCalls)
        }

    @Test
    fun `sin conexion y sin snapshot falla en vez de inventar datos`() =
        runTest {
            val repository = repository(cache = FakeSalonConfigCache(), online = false)

            val result = repository.getAreas(CAJA_A)

            assertTrue(result.isFailure)
            assertEquals(
                "Sin conexión y sin áreas descargadas para esta caja",
                result.exceptionOrNull()?.message,
            )
        }

    /**
     * Evidencia del aislamiento en caché: el snapshot está indexado por caja, así que al cambiar
     * de caja (y por tanto de sucursal) no se reutiliza la configuración de la anterior.
     */
    @Test
    fun `el snapshot de otra caja no se reutiliza`() =
        runTest {
            val cache = FakeSalonConfigCache(cachedAreasByCaja = mapOf(CAJA_A to AreasResult(7, AREAS, fromCache = true)))
            val repository = repository(cache = cache, online = false)

            val otherCaja = repository.getAreas(CAJA_B)

            assertTrue(otherCaja.isFailure)
        }

    @Test
    fun `un fallo de red cae al snapshot cuando existe`() =
        runTest {
            val cache = FakeSalonConfigCache(cachedAreasByCaja = mapOf(CAJA_A to AreasResult(7, AREAS, fromCache = true)))
            val repository = repository(api = FakeAreasApi(areasError = IllegalStateException("timeout")), cache = cache)

            val result = repository.getAreas(CAJA_A).getOrThrow()

            assertTrue(result.fromCache)
        }

    @Test
    fun `un fallo de red sin snapshot propaga el mensaje del backend`() =
        runTest {
            val repository =
                repository(api = FakeAreasApi(areasError = IllegalStateException("La caja no pertenece al usuario")))

            val result = repository.getAreas(CAJA_A)

            assertEquals("La caja no pertenece al usuario", result.exceptionOrNull()?.message)
        }

    @Test
    fun `las mesas se cachean por area`() =
        runTest {
            val cache = FakeSalonConfigCache()
            val repository = repository(cache = cache)

            val result = repository.getMesas(CAJA_A, areaId = 1).getOrThrow()

            assertEquals(listOf(10), result.mesas.map { it.id })
            // El plan incluye lienzo e imagen; ya llegan en la respuesta y se persisten.
            assertEquals(LIENZO, result.lienzo)
            assertEquals(IMAGEN_URL, result.imagenUrl)
            assertEquals(
                Triple(CAJA_A, 1, listOf(10)),
                cache.savedMesas?.let { Triple(it.cajaId, it.areaId, it.mesas.map { m -> m.id }) },
            )
        }

    @Test
    fun `las mesas cacheadas de otra area no se mezclan`() =
        runTest {
            val cache =
                FakeSalonConfigCache(
                    cachedMesas =
                        mapOf(
                            (CAJA_A to 1) to
                                MesasResult(1, LIENZO, IMAGEN_URL, listOf(mesa(10, 1)), fromCache = true),
                        ),
                )
            val repository = repository(cache = cache, online = false)

            assertTrue(repository.getMesas(CAJA_A, areaId = 1).isSuccess)
            assertTrue(repository.getMesas(CAJA_A, areaId = 2).isFailure)
        }

    @Test
    fun `sin empresa seleccionada no se consulta el backend`() =
        runTest {
            val api = FakeAreasApi()
            val repository = repository(api = api, token = null)

            val result = repository.getAreas(CAJA_A)

            assertTrue(result.exceptionOrNull() is SessionConfigurationException)
            assertEquals(0, api.areaCalls)
        }

    @Test
    fun `una respuesta con success false se trata como error`() =
        runTest {
            val api = FakeAreasApi(areasResponse = AreasResponse(success = false, error = "Área no encontrada"))
            val repository = repository(api = api)

            val result = repository.getAreas(CAJA_A)

            assertEquals("Área no encontrada", result.exceptionOrNull()?.message)
        }

    // ---------- helpers ----------

    private fun repository(
        api: FakeAreasApi = FakeAreasApi(),
        cache: SalonConfigCache = FakeSalonConfigCache(),
        online: Boolean = true,
        token: String? = "token-123",
    ) = AreaRepositoryImpl(
        areasApi = api,
        cache = cache,
        session = CompanyTokenReader { token },
        connectivity = ConnectivityStatus { online },
    )

    private class FakeAreasApi(
        private val areasResponse: AreasResponse = AreasResponse(success = true, sucursalId = 7, data = AREAS),
        private val mesasResponse: MesasResponse =
            MesasResponse(success = true, areaId = 1, lienzo = LIENZO, imagenUrl = IMAGEN_URL, data = listOf(mesa(10, 1))),
        private val areasError: Throwable? = null,
    ) : AreasApi {
        var areaCalls: Int = 0
            private set

        override suspend fun getAreas(
            cajaId: String,
            authHeader: String,
        ): Result<AreasResponse> {
            areaCalls++
            areasError?.let { return Result.failure(it) }
            return Result.success(areasResponse)
        }

        override suspend fun getMesas(
            cajaId: String,
            areaId: Int,
            authHeader: String,
        ): Result<MesasResponse> = Result.success(mesasResponse)
    }

    private class FakeSalonConfigCache(
        private val cachedAreasByCaja: Map<String, AreasResult> = emptyMap(),
        private val cachedMesas: Map<Pair<String, Int>, MesasResult> = emptyMap(),
    ) : SalonConfigCache {
        data class SavedAreas(
            val cajaId: String,
            val sucursalId: Int,
            val areas: List<Area>,
        )

        data class SavedMesas(
            val cajaId: String,
            val areaId: Int,
            val lienzo: Lienzo,
            val imagenUrl: String?,
            val mesas: List<Mesa>,
        )

        var savedAreas: SavedAreas? = null
            private set
        var savedMesas: SavedMesas? = null
            private set

        override suspend fun readCachedAreas(cajaId: String): AreasResult? = cachedAreasByCaja[cajaId]

        override suspend fun cacheAreas(
            cajaId: String,
            sucursalId: Int,
            areas: List<Area>,
        ) {
            savedAreas = SavedAreas(cajaId, sucursalId, areas)
        }

        override suspend fun readCachedMesas(
            cajaId: String,
            areaId: Int,
        ): MesasResult? = cachedMesas[cajaId to areaId]

        override suspend fun cacheMesas(
            cajaId: String,
            areaId: Int,
            lienzo: Lienzo,
            imagenUrl: String?,
            mesas: List<Mesa>,
        ) {
            savedMesas = SavedMesas(cajaId, areaId, lienzo, imagenUrl, mesas)
        }
    }

    private companion object {
        const val CAJA_A = "caja-a"
        const val CAJA_B = "caja-b"
        val LIENZO = Lienzo(2000, 1200)
        const val IMAGEN_URL = "https://cdn.amaxonia.com/areas/1.jpg"

        val AREAS =
            listOf(
                Area(id = 1, nombre = "Salón principal", orden = 1, cantidadMesasActivas = 2),
                Area(id = 2, nombre = "Terraza", orden = 2, cantidadMesasActivas = 1),
            )

        fun mesa(
            id: Int,
            areaId: Int,
        ) = Mesa(id = id, areaId = areaId, codigo = "M$id", nombre = "Mesa $id", capacidad = 4)
    }
}
