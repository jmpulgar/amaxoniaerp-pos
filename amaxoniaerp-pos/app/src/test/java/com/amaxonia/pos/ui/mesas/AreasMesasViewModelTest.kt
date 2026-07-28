package com.amaxonia.pos.ui.mesas

import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.mesas.Area
import com.amaxonia.pos.domain.model.mesas.AreasResult
import com.amaxonia.pos.domain.model.mesas.EstadoMesaOperativo
import com.amaxonia.pos.domain.model.mesas.EstadoMesaResponse
import com.amaxonia.pos.domain.model.mesas.Lienzo
import com.amaxonia.pos.domain.model.mesas.Mesa
import com.amaxonia.pos.domain.model.mesas.MesasResult
import com.amaxonia.pos.domain.model.mesas.SelectedTable
import com.amaxonia.pos.domain.model.mesas.SesionMesa
import com.amaxonia.pos.domain.repository.ActiveCajaReader
import com.amaxonia.pos.domain.repository.AreaRepository
import com.amaxonia.pos.domain.repository.ConnectivityStatus
import com.amaxonia.pos.domain.repository.SelectedTableHolder
import com.amaxonia.pos.domain.repository.SesionMesaRepository
import com.amaxonia.pos.test.MainDispatcherRule
import com.amaxonia.pos.ui.mesas.SalonViewMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    // ---------- Plano vs Lista y limpieza de selección ----------

    @Test
    fun `area con mesas sin geometria arranca en modo lista por distribucion invalida`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Las mesas default del helper no tienen posicionX/Y ni ancho/alto.
            val repository = FakeAreaRepository()
            val viewModel = viewModel(repository)

            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.hasDistribucionValida)
            assertEquals(SalonViewMode.LISTA, state.viewMode)
            assertFalse(state.canShowPlano)
        }

    @Test
    fun `area con geometria valida arranca en modo plano`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeAreaRepository(
                    mesasByArea =
                        mapOf(
                            1 to
                                listOf(
                                    mesaConPlano(10, 1, posicionX = 100.0, posicionY = 100.0),
                                    mesaConPlano(11, 1, posicionX = 400.0, posicionY = 200.0),
                                ),
                            2 to listOf(mesaConPlano(20, 2)),
                        ),
                )
            val viewModel = viewModel(repository)

            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.hasDistribucionValida)
            assertEquals(SalonViewMode.PLANO, state.viewMode)
            assertTrue(state.canShowPlano)
        }

    @Test
    fun `no se puede forzar modo plano si la distribucion no es valida`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository() // sin geometría
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.onViewModeChanged(SalonViewMode.PLANO)

            assertEquals(SalonViewMode.LISTA, viewModel.state.value.viewMode)
        }

    @Test
    fun `se puede conmutar entre lista y plano cuando el area soporta plano`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeAreaRepository(
                    mesasByArea = mapOf(1 to listOf(mesaConPlano(10, 1), mesaConPlano(11, 1))),
                )
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            assertEquals(SalonViewMode.PLANO, viewModel.state.value.viewMode)

            viewModel.onViewModeChanged(SalonViewMode.LISTA)
            assertEquals(SalonViewMode.LISTA, viewModel.state.value.viewMode)

            viewModel.onViewModeChanged(SalonViewMode.PLANO)
            assertEquals(SalonViewMode.PLANO, viewModel.state.value.viewMode)
        }

    @Test
    fun `cambiar de area limpia la seleccion de mesa en memoria`() =
        runTest(mainDispatcherRule.dispatcher) {
            val holder = RecordingSelectedTableHolder()
            val viewModel = viewModel(FakeAreaRepository(), holder = holder)
            advanceUntilIdle()
            viewModel.onMesaSelected(10)
            assertEquals(
                10,
                holder.selectedTable.value
                    ?.mesa
                    ?.id,
            )

            viewModel.onAreaSelected(2)
            advanceUntilIdle()

            assertNull(holder.selectedTable.value)
            assertNull(viewModel.state.value.selectedMesaId)
            assertEquals(1, holder.clearCount)
        }

    @Test
    fun `cambiar de caja limpia la seleccion y el contexto del area anterior`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cajaFlow = MutableStateFlow(activeCaja())
            val repository = FakeAreaRepository()
            val holder = RecordingSelectedTableHolder()
            val viewModel =
                AreasMesasViewModel(
                    areaRepository = repository,
                    activeCajaReader =
                        object : ActiveCajaReader {
                            override val activeCaja: StateFlow<Caja?> = cajaFlow
                        },
                    connectivity = ConnectivityStatus { true },
                    selectedTableHolder = holder,
                )
            advanceUntilIdle()
            viewModel.onMesaSelected(10)
            assertEquals(
                10,
                holder.selectedTable.value
                    ?.mesa
                    ?.id,
            )

            // Nueva caja en otra sucursal.
            cajaFlow.value = activeCaja().copy(idCaja = "caja-otra", idSucursal = 9)
            advanceUntilIdle()

            assertNull(holder.selectedTable.value)
            val state = viewModel.state.value
            assertNull(state.selectedMesaId)
            assertNull(state.selectedAreaId)
            assertTrue(state.mesas.isEmpty())
            assertEquals(1, holder.clearCount)
        }

    @Test
    fun `seleccionar una mesa no genera llamadas de escritura`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            val areaCallsAntes = repository.areaCalls.size
            val mesaCallsAntes = repository.mesaCalls.size

            viewModel.onMesaSelected(10)
            advanceUntilIdle()

            // Exactamente las mismas consultas que antes: seleccionar es solo memoria.
            assertEquals(areaCallsAntes, repository.areaCalls.size)
            assertEquals(mesaCallsAntes, repository.mesaCalls.size)
        }

    // ---------- Sesión operativa (fase 2) ----------

    @Test
    fun `abrir sesion hace POST y marca la mesa como ocupada`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val sesion = FakeSesionMesaRepository()
            val viewModel = viewModel(repository, sesionRepository = sesion)
            advanceUntilIdle()

            viewModel.onAbrirSesion(mesaId = 10, cantidadPersonas = 4)
            advanceUntilIdle()

            assertEquals(listOf(Triple(CAJA_ID, 10, 4)), sesion.abrirCalls)
            val state = viewModel.state.value
            assertFalse(state.isLoadingSesion)
            assertEquals(EstadoMesaOperativo.OCUPADA, state.estadosMesas[10])
            assertEquals(10, state.activeSesion?.mesaId)
            assertEquals(4, state.activeSesion?.cantidadPersonas)
            assertNull(state.sesionError)
        }

    @Test
    fun `abrir sesion con cantidad invalida corta en cliente sin llamar al backend`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val sesion = FakeSesionMesaRepository()
            val viewModel = viewModel(repository, sesionRepository = sesion)
            advanceUntilIdle()

            viewModel.onAbrirSesion(mesaId = 10, cantidadPersonas = 0)
            advanceUntilIdle()

            // Nunca viaja al backend: cortocircuito en el ViewModel.
            assertTrue(sesion.abrirCalls.isEmpty())
            assertEquals("Cantidad de personas inválida", viewModel.state.value.sesionError)
        }

    @Test
    fun `abrir sesion sobre una mesa ya ocupada se bloquea en cliente`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val sesion = FakeSesionMesaRepository(ocupadas = setOf(10))
            val viewModel = viewModel(repository, sesionRepository = sesion)
            advanceUntilIdle()
            // Precarga el estado operacional de la mesa 10 = OCUPADA.
            sesion.estados = listOf(EstadoMesaResponse(mesaId = 10, estado = EstadoMesaOperativo.OCUPADA))
            viewModel.onRefreshEstados()
            advanceUntilIdle()

            viewModel.onAbrirSesion(mesaId = 10, cantidadPersonas = 2)
            advanceUntilIdle()

            // Se bloqueó localmente sin POST.
            assertTrue(sesion.abrirCalls.isEmpty())
            assertEquals("La mesa ya tiene una sesión abierta", viewModel.state.value.sesionError)
        }

    @Test
    fun `abrir sesion con error del backend expone el mensaje`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val sesion =
                FakeSesionMesaRepository(
                    abrirError = IllegalStateException("La mesa ya tiene una sesión abierta"),
                )
            val viewModel = viewModel(repository, sesionRepository = sesion)
            advanceUntilIdle()

            viewModel.onAbrirSesion(mesaId = 10, cantidadPersonas = 4)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isLoadingSesion)
            assertEquals("La mesa ya tiene una sesión abierta", state.sesionError)
            assertNull(state.activeSesion)
            // No marcamos OCUPADA porque la apertura falló.
            assertFalse(state.estadosMesas[10] == EstadoMesaOperativo.OCUPADA)
        }

    @Test
    fun `recuperar sesion activa trae la sesion de la mesa ocupada`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val sesion = FakeSesionMesaRepository(activa = sesionFixture(mesaId = 10, sesionId = 55))
            val viewModel = viewModel(repository, sesionRepository = sesion)
            advanceUntilIdle()

            viewModel.onRecuperarSesionActiva(mesaId = 10)
            advanceUntilIdle()

            assertEquals(listOf(CAJA_ID to 10), sesion.activaCalls)
            val state = viewModel.state.value
            assertEquals(55, state.activeSesion?.id)
            assertEquals(10, state.activeSesion?.mesaId)
            assertEquals(EstadoMesaOperativo.OCUPADA, state.estadosMesas[10])
        }

    @Test
    fun `recuperar sesion activa cuando ya no existe limpia el indicador de ocupada`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val sesion = FakeSesionMesaRepository(activa = null)
            val viewModel = viewModel(repository, sesionRepository = sesion)
            advanceUntilIdle()
            sesion.estados = listOf(EstadoMesaResponse(mesaId = 10, estado = EstadoMesaOperativo.OCUPADA))
            viewModel.onRefreshEstados()
            advanceUntilIdle()
            assertEquals(EstadoMesaOperativo.OCUPADA, viewModel.state.value.estadosMesas[10])

            viewModel.onRecuperarSesionActiva(mesaId = 10)
            advanceUntilIdle()

            // El backend ya no tiene sesión: se elimina el indicador (sin POS no podemos
            // afirmar DISPONIBLE/OCUPADA; la UI mostrará "desconocido" hasta nuevo refresh).
            assertNull(viewModel.state.value.activeSesion)
            assertNull(viewModel.state.value.estadosMesas[10])
        }

    @Test
    fun `cerrar sesion hace POST y libera la mesa`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val sesion = FakeSesionMesaRepository()
            val viewModel = viewModel(repository, sesionRepository = sesion)
            advanceUntilIdle()

            viewModel.onCerrarSesion(mesaId = 10, sesionId = 99)
            advanceUntilIdle()

            assertEquals(listOf(Triple(CAJA_ID, 10, 99)), sesion.cerrarCalls)
            val state = viewModel.state.value
            assertEquals(EstadoMesaOperativo.DISPONIBLE, state.estadosMesas[10])
            assertNull(state.activeSesion)
            assertNull(state.sesionError)
        }

    @Test
    fun `cancelar sesion hace POST y libera la mesa`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val sesion = FakeSesionMesaRepository()
            val viewModel = viewModel(repository, sesionRepository = sesion)
            advanceUntilIdle()

            viewModel.onCancelarSesion(mesaId = 10, sesionId = 99)
            advanceUntilIdle()

            assertEquals(listOf(Triple(CAJA_ID, 10, 99)), sesion.cancelarCalls)
            val state = viewModel.state.value
            assertEquals(EstadoMesaOperativo.DISPONIBLE, state.estadosMesas[10])
            assertNull(state.activeSesion)
        }

    @Test
    fun `cerrar sesion con error del backend expone mensaje y no libera la mesa`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val sesion =
                FakeSesionMesaRepository(
                    cerrarError = IllegalStateException("La sesión tiene pedidos asociados"),
                )
            val viewModel = viewModel(repository, sesionRepository = sesion)
            advanceUntilIdle()
            sesion.estados = listOf(EstadoMesaResponse(mesaId = 10, estado = EstadoMesaOperativo.OCUPADA))
            viewModel.onRefreshEstados()
            advanceUntilIdle()

            viewModel.onCerrarSesion(mesaId = 10, sesionId = 99)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("La sesión tiene pedidos asociados", state.sesionError)
            assertEquals(EstadoMesaOperativo.OCUPADA, state.estadosMesas[10])
        }

    @Test
    fun `refrescar estados hidrata el mapa de operaciones tras cargar mesas`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            val sesion =
                FakeSesionMesaRepository(
                    estados =
                        listOf(
                            EstadoMesaResponse(mesaId = 10, estado = EstadoMesaOperativo.DISPONIBLE),
                            EstadoMesaResponse(mesaId = 11, estado = EstadoMesaOperativo.OCUPADA),
                        ),
                )
            val viewModel = viewModel(repository, sesionRepository = sesion)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(EstadoMesaOperativo.DISPONIBLE, state.estadosMesas[10])
            assertEquals(EstadoMesaOperativo.OCUPADA, state.estadosMesas[11])
            assertTrue(state.hasEstadosHidratados)
        }

    @Test
    fun `cambiar de area limpia los estados operativos anteriores`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAreaRepository()
            // El fake arranca con un estado para la mesa 10 del área 1; al cambiar al área 2 no
            // traerá nada para esa mesa (los estados son por área, simulamos snapshot vacío).
            val sesion =
                FakeSesionMesaRepository(
                    estados = listOf(EstadoMesaResponse(mesaId = 10, estado = EstadoMesaOperativo.OCUPADA)),
                )
            val viewModel = viewModel(repository, sesionRepository = sesion)
            advanceUntilIdle()
            assertEquals(EstadoMesaOperativo.OCUPADA, viewModel.state.value.estadosMesas[10])

            // Al seleccionar área 2 dejamos sin estados: el snapshot simulado ya no cubre mesa 10.
            sesion.estados = emptyList()
            viewModel.onAreaSelected(2)
            advanceUntilIdle()

            val state = viewModel.state.value
            // El mapa de estados se rehidrata en onRefreshEstados() tras loadMesas; al recibir
            // lista vacía para el área 2, ya no contiene la mesa 10 del área anterior.
            assertFalse(state.estadosMesas.containsKey(10))
            assertNull(state.activeSesion)
        }

    @Test
    fun `cambiar de caja limpia los estados operativos anteriores`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cajaFlow = MutableStateFlow(activeCaja())
            val repository = FakeAreaRepository()
            val sesion =
                FakeSesionMesaRepository(
                    estados = listOf(EstadoMesaResponse(mesaId = 10, estado = EstadoMesaOperativo.OCUPADA)),
                )
            val viewModel =
                AreasMesasViewModel(
                    areaRepository = repository,
                    activeCajaReader =
                        object : ActiveCajaReader {
                            override val activeCaja: StateFlow<Caja?> = cajaFlow
                        },
                    connectivity = ConnectivityStatus { true },
                    selectedTableHolder = RecordingSelectedTableHolder(),
                    sesionMesaRepository = sesion,
                )
            advanceUntilIdle()
            assertEquals(EstadoMesaOperativo.OCUPADA, viewModel.state.value.estadosMesas[10])

            cajaFlow.value = activeCaja().copy(idCaja = "caja-otra", idSucursal = 9)
            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.estadosMesas
                    .isEmpty(),
            )
        }

    @Test
    fun `dismiss sesion error limpia el mensaje`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = viewModel(FakeAreaRepository(), sesionRepository = FakeSesionMesaRepository())
            advanceUntilIdle()
            viewModel.onAbrirSesion(mesaId = 10, cantidadPersonas = 0)
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.sesionError)

            viewModel.onDismissSesionError()

            assertNull(viewModel.state.value.sesionError)
        }

    private fun sesionFixture(
        mesaId: Int,
        sesionId: Int,
    ) = SesionMesa(
        id = sesionId,
        sucursalId = 7,
        cajaId = CAJA_ID,
        areaId = 1,
        mesaId = mesaId,
        usuarioId = 42,
        cantidadPersonas = 4,
        estado = "ABIERTA",
        fechaApertura = "2026-01-01T12:00:00",
        activo = true,
    )

    /**
     * Fake de [SesionMesaRepository]. Cada llamada registra sus argumentos; los resultados se
     * controlan con `estados`, `activa`, y los flags de error.
     */
    private class FakeSesionMesaRepository(
        var estados: List<EstadoMesaResponse> = emptyList(),
        var activa: SesionMesa? = null,
        var ocupadas: Set<Int> = emptySet(),
        var abrirError: Throwable? = null,
        var cerrarError: Throwable? = null,
        var cancelarError: Throwable? = null,
    ) : SesionMesaRepository {
        val abrirCalls = mutableListOf<Triple<String, Int, Int>>() // caja, mesa, cantidad
        val activaCalls = mutableListOf<Pair<String, Int>>() // caja, mesa
        val cerrarCalls = mutableListOf<Triple<String, Int, Int>>() // caja, mesa, sesion
        val cancelarCalls = mutableListOf<Triple<String, Int, Int>>() // caja, mesa, sesion

        override suspend fun getEstados(
            cajaId: String,
            areaId: Int,
        ): Result<List<EstadoMesaResponse>> = Result.success(estados)

        override suspend fun abrir(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            cantidadPersonas: Int,
        ): Result<SesionMesa> {
            abrirCalls += Triple(cajaId, mesaId, cantidadPersonas)
            abrirError?.let { return Result.failure(it) }
            return Result.success(
                SesionMesa(
                    id = 1,
                    sucursalId = 7,
                    cajaId = cajaId,
                    areaId = areaId,
                    mesaId = mesaId,
                    usuarioId = 42,
                    cantidadPersonas = cantidadPersonas,
                    estado = "ABIERTA",
                    fechaApertura = "2026-01-01T12:00:00",
                    activo = true,
                ),
            )
        }

        override suspend fun getSesionActiva(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
        ): Result<SesionMesa?> {
            activaCalls += cajaId to mesaId
            return Result.success(activa)
        }

        override suspend fun cerrar(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
        ): Result<SesionMesa> {
            cerrarCalls += Triple(cajaId, mesaId, sesionId)
            cerrarError?.let { return Result.failure(it) }
            return Result.success(
                SesionMesa(
                    id = sesionId,
                    sucursalId = 7,
                    cajaId = cajaId,
                    areaId = areaId,
                    mesaId = mesaId,
                    estado = "CERRADA",
                    fechaApertura = "2026-01-01T12:00:00",
                    fechaCierre = "2026-01-01T14:00:00",
                    activo = false,
                ),
            )
        }

        override suspend fun cancelar(
            cajaId: String,
            areaId: Int,
            mesaId: Int,
            sesionId: Int,
        ): Result<SesionMesa> {
            cancelarCalls += Triple(cajaId, mesaId, sesionId)
            cancelarError?.let { return Result.failure(it) }
            return Result.success(
                SesionMesa(
                    id = sesionId,
                    sucursalId = 7,
                    cajaId = cajaId,
                    areaId = areaId,
                    mesaId = mesaId,
                    estado = "CANCELADA",
                    fechaApertura = "2026-01-01T12:00:00",
                    activo = false,
                ),
            )
        }
    }

    // ---------- helpers ----------

    private fun viewModel(
        repository: FakeAreaRepository,
        caja: Caja? = activeCaja(),
        online: Boolean = true,
        holder: SelectedTableHolder = RecordingSelectedTableHolder(),
        sesionRepository: SesionMesaRepository? = null,
    ) = AreasMesasViewModel(
        areaRepository = repository,
        activeCajaReader = FakeActiveCajaReader(caja),
        connectivity = ConnectivityStatus { online },
        selectedTableHolder = holder,
        sesionMesaRepository = sesionRepository,
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
                MesasResult(
                    areaId = areaId,
                    lienzo = LIENZO,
                    imagenUrl = IMAGEN_URL,
                    mesas = mesasByArea[areaId].orEmpty(),
                    fromCache = fromCache,
                ),
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

        /** Mesa con geometría completa; parámetros nombrados explicitan cada dimensión. */
        @Suppress("LongParameterList")
        fun mesaConPlano(
            id: Int,
            areaId: Int,
            posicionX: Double = 100.0,
            posicionY: Double = 100.0,
            ancho: Double = 120.0,
            alto: Double = 90.0,
            rotacion: Double = 0.0,
            forma: String = "rectangular",
        ) = Mesa(
            id = id,
            areaId = areaId,
            codigo = "M$id",
            nombre = "Mesa $id",
            capacidad = 4,
            forma = forma,
            posicionX = posicionX,
            posicionY = posicionY,
            ancho = ancho,
            alto = alto,
            rotacion = rotacion,
        )

        val LIENZO = Lienzo(2000, 1200)
        const val IMAGEN_URL = "https://cdn.amaxonia.com/areas/1.jpg"
    }
}
