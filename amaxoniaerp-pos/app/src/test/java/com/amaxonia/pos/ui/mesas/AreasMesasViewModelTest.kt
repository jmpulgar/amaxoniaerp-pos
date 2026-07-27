package com.amaxonia.pos.ui.mesas

import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.mesas.Area
import com.amaxonia.pos.domain.model.mesas.AreasResult
import com.amaxonia.pos.domain.model.mesas.Mesa
import com.amaxonia.pos.domain.model.mesas.MesasResult
import com.amaxonia.pos.domain.model.mesas.SelectedTable
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.AreaRepository
import com.amaxonia.pos.domain.repository.ConnectivityStatus
import com.amaxonia.pos.domain.repository.SelectedTableHolder
import com.amaxonia.pos.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AreasMesasViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `carga las areas de la sucursal de la caja activa y abre la primera`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val viewModel = viewModel(repository)

            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(listOf(1, 2), state.areas.map { it.id })
            assertEquals(7, state.sucursalId)
            assertEquals("Sucursal Centro", state.sucursalNombre)
            assertEquals(1, state.selectedAreaId)
            assertEquals(listOf(10, 11), state.mesas.map { it.id })
            assertFalse(state.isLoadingAreas)
            assertFalse(state.isLoadingMesas)
            assertNull(state.areasError)
            // Solo lectura: una consulta de áreas y una de mesas, ninguna escritura.
            assertEquals(listOf(CAJA_ID), repository.areaCalls)
            assertEquals(listOf(CAJA_ID to 1), repository.mesaCalls)
        }

    @Test
    fun `sucursal sin areas muestra estado vacio y no consulta mesas`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository(areas = emptyList())
            val viewModel = viewModel(repository)

            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.isAreasEmpty)
            assertNull(state.selectedAreaId)
            assertTrue(repository.mesaCalls.isEmpty())
        }

    @Test
    fun `area sin mesas muestra estado vacio`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository(mesasByArea = mapOf(1 to emptyList()))
            val viewModel = viewModel(repository)

            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.mesas.isEmpty())
            assertTrue(state.isMesasEmpty)
            assertNull(state.mesasError)
        }

    @Test
    fun `error de areas se expone y el reintento vuelve a consultar`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository(areasError = IllegalStateException("Backend caído"))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            assertEquals("Backend caído", viewModel.state.value.areasError)

            repository.areasError = null
            viewModel.onRetryAreas()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertNull(state.areasError)
            assertEquals(listOf(1, 2), state.areas.map { it.id })
            assertEquals(2, repository.areaCalls.size)
        }

    @Test
    fun `error de mesas no borra las areas y se reintenta solo mesas`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository(mesasError = IllegalStateException("Área no encontrada"))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            val failed = viewModel.state.value
            assertEquals("Área no encontrada", failed.mesasError)
            assertEquals(listOf(1, 2), failed.areas.map { it.id })

            repository.mesasError = null
            viewModel.onRetryMesas()
            advanceUntilIdle()

            val retried = viewModel.state.value
            assertNull(retried.mesasError)
            assertEquals(listOf(10, 11), retried.mesas.map { it.id })
        }

    @Test
    fun `cambiar de area consulta las mesas de esa area`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.onAreaSelected(2)
            advanceUntilIdle()

            val switched = viewModel.state.value
            assertEquals(2, switched.selectedAreaId)
            assertEquals(listOf(20), switched.mesas.map { it.id })
            assertEquals(listOf(CAJA_ID to 1, CAJA_ID to 2), repository.mesaCalls)
        }

    @Test
    fun `pulsar repetidamente el mismo area no dispara consultas duplicadas`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            repeat(5) { viewModel.onAreaSelected(1) }
            advanceUntilIdle()

            assertEquals(listOf(CAJA_ID to 1), repository.mesaCalls)
        }

    @Test
    fun `refrescos repetidos mientras hay una carga en vuelo se ignoran`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val viewModel = viewModel(repository)

            // Sin advanceUntilIdle: la carga inicial sigue en vuelo.
            repeat(4) { viewModel.onRefresh() }
            advanceUntilIdle()

            assertEquals(1, repository.areaCalls.size)
        }

    @Test
    fun `sin caja activa se pide seleccionar caja y no se consulta nada`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val viewModel = viewModel(repository, caja = null)

            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.requiresCaja)
            assertTrue(state.areas.isEmpty())
            assertFalse(state.isAreasEmpty)
            assertNull(state.sucursalId)
            assertTrue(repository.areaCalls.isEmpty())
        }

    @Test
    fun `offline con snapshot marca los datos como cacheados`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository(fromCache = true)
            val viewModel = viewModel(repository, online = false)

            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.isOffline)
            assertTrue(state.showingCachedData)
            assertEquals(listOf(1, 2), state.areas.map { it.id })
        }

    @Test
    fun `offline sin snapshot muestra error explicito y no inventa datos`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository(areasError = IllegalStateException("Sin conexión y sin áreas descargadas para esta caja"))
            val viewModel = viewModel(repository, online = false)

            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("Sin conexión y sin áreas descargadas para esta caja", state.areasError)
            assertTrue(state.areas.isEmpty())
        }

    @Test
    fun `seleccionar una mesa solo guarda contexto en memoria y no escribe nada`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val holder = RecordingSelectedTableHolder()
            val viewModel = viewModel(repository, holder = holder)
            advanceUntilIdle()

            viewModel.onMesaSelected(10)

            assertEquals(10, viewModel.state.value.selectedMesaId)
            val selected = holder.selectedTable.value
            assertEquals(10, selected?.mesa?.id)
            assertEquals(1, selected?.area?.id)
            assertEquals(7, selected?.sucursalId)
            // Ninguna consulta adicional: seleccionar no abre venta ni crea registros.
            assertEquals(1, repository.areaCalls.size)
            assertEquals(1, repository.mesaCalls.size)
        }

    @Test
    fun `quitar la seleccion limpia el contexto en memoria`() =
        runTest(mainDispatcherRule.dispatcher) {
            val holder = RecordingSelectedTableHolder()
            val viewModel = viewModel(FakeAreaRepository(), holder = holder)
            advanceUntilIdle()

            viewModel.onMesaSelected(10)
            viewModel.onClearSelection()

            assertNull(viewModel.state.value.selectedMesaId)
            assertNull(holder.selectedTable.value)
            assertEquals(1, holder.clearCount)
        }

    @Test
    fun `una mesa seleccionada que desaparece tras refrescar deja de estar seleccionada`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            viewModel.onMesaSelected(11)
            assertEquals(11, viewModel.state.value.selectedMesaId)

            // La mesa 11 se desactiva en el administrativo: el backend deja de devolverla.
            repository.mesasByArea = mapOf(1 to listOf(mesa(10, 1)), 2 to listOf(mesa(20, 2)))
            viewModel.onRefresh()
            advanceUntilIdle()

            val refreshed = viewModel.state.value
            assertNull(refreshed.selectedMesaId)
            assertEquals(listOf(10), refreshed.mesas.map { it.id })
        }

    @Test
    fun `refrescar conserva el area abierta si sigue existiendo`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            viewModel.onAreaSelected(2)
            advanceUntilIdle()

            viewModel.onRefresh()
            advanceUntilIdle()

            assertEquals(2, viewModel.state.value.selectedAreaId)
        }

    // ---------- helpers ----------

    private fun viewModel(
        repository: FakeAreaRepository,
        caja: Caja? = activeCaja(),
        online: Boolean = true,
        holder: SelectedTableHolder = RecordingSelectedTableHolder(),
    ) = AreasMesasViewModel(
        areaRepository = repository,
        activeCajaReader = FakeActiveCajaReader(caja),
        connectivity = ConnectivityStatus { online },
        selectedTableHolder = holder,
    )

    private class FakeActiveCajaReader(
        caja: Caja?,
    ) : ActiveCajaReader {
        override val activeCaja: StateFlow<Caja?> = MutableStateFlow(caja).asStateFlow()
    }

    private class RecordingSelectedTableHolder : SelectedTableHolder {
        private val _selectedTable = MutableStateFlow<SelectedTable?>(null)
        override val selectedTable: StateFlow<SelectedTable?> = _selectedTable.asStateFlow()
        var clearCount: Int = 0
            private set

        override fun select(table: SelectedTable) {
            _selectedTable.value = table
        }

        override fun clear() {
            clearCount++
            _selectedTable.value = null
        }
    }

    /**
     * Solo expone lectura, igual que [AreaRepository]: si el ViewModel intentara escribir algo,
     * no habría método al que llamar. Registra cada consulta para poder afirmar que no se
     * duplican peticiones.
     */
    private class FakeAreaRepository(
        var areas: List<Area> = listOf(area(1, "Salón principal", 1), area(2, "Terraza", 2)),
        var mesasByArea: Map<Int, List<Mesa>> =
            mapOf(
                1 to listOf(mesa(10, 1), mesa(11, 1)),
                2 to listOf(mesa(20, 2)),
            ),
        var areasError: Throwable? = null,
        var mesasError: Throwable? = null,
        private val fromCache: Boolean = false,
    ) : AreaRepository {
        val areaCalls = mutableListOf<String>()
        val mesaCalls = mutableListOf<Pair<String, Int>>()

        override suspend fun getAreas(cajaId: String): Result<AreasResult> {
            areaCalls += cajaId
            areasError?.let { return Result.failure(it) }
            return Result.success(AreasResult(sucursalId = 7, areas = areas, fromCache = fromCache))
        }

        override suspend fun getMesas(
            cajaId: String,
            areaId: Int,
        ): Result<MesasResult> {
            mesaCalls += cajaId to areaId
            mesasError?.let { return Result.failure(it) }
            return Result.success(
                MesasResult(areaId = areaId, mesas = mesasByArea[areaId].orEmpty(), fromCache = fromCache),
            )
        }
    }

    private companion object {
        const val CAJA_ID = "caja-1"

        fun activeCaja() =
            Caja(
                idCaja = CAJA_ID,
                codCaja = "C1",
                caja = "Caja 1",
                descripcion = "Caja principal",
                estatus = 1,
                idSucursal = 7,
                serieCaja = "1",
                sucursalNombre = "Sucursal Centro",
            )

        fun area(
            id: Int,
            nombre: String,
            orden: Int,
        ) = Area(id = id, nombre = nombre, orden = orden, cantidadMesasActivas = 2)

        fun mesa(
            id: Int,
            areaId: Int,
        ) = Mesa(
            id = id,
            areaId = areaId,
            codigo = "M$id",
            nombre = "Mesa $id",
            capacidad = 4,
            forma = "rectangular",
        )
    }
}
