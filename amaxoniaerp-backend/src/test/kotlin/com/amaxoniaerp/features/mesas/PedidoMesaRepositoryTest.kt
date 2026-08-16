package com.amaxoniaerp.features.mesas

import com.amaxoniaerp.features.auth.data.UsersTable
import com.amaxoniaerp.features.caja.data.CajaTable
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.mesas.data.AbrirSesionScope
import com.amaxoniaerp.features.mesas.data.MesasTable
import com.amaxoniaerp.features.mesas.data.PedidoMesaRepository
import com.amaxoniaerp.features.mesas.data.PedidoMesaTable
import com.amaxoniaerp.features.mesas.data.PlantasTable
import com.amaxoniaerp.features.mesas.data.SesionMesaRepository
import com.amaxoniaerp.features.mesas.data.SesionMesaTable
import com.amaxoniaerp.features.mesas.domain.CrearPedidoMesaItemRequest
import com.amaxoniaerp.features.mesas.domain.CrearPedidoMesaRequest
import com.amaxoniaerp.features.mesas.domain.EstadoPedidoMesa
import com.amaxoniaerp.features.mesas.domain.PedidoMesaResult
import com.amaxoniaerp.features.mesas.domain.SesionMesaResult
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cobertura de pedidos/comandas de mesa contra H2 en modo MySQL: crear líneas PENDIENTE,
 * enviar comanda, cambiar estado (avance y anulación), validación de sesión/mesa y bloqueo
 * de cierre/cancelación cuando la sesión tiene operaciones pendientes.
 *
 * El SUT se inyecta con el lookup real sobre `pedido_mesa`, así se valida el mismo contrato
 * que en producción (SesionMesaRepository блокa cuando PedidoMesaRepository.tieneOperaciones
 * devuelve `true`).
 */
class PedidoMesaRepositoryTest {
    private lateinit var database: Database
    private val pedidoRepository = PedidoMesaRepository()
    private val sesionRepository = SesionMesaRepository(pedidoRepository::tieneOperaciones)

    @BeforeTest
    fun setUp() {
        database =
            Database.connect(
                "jdbc:h2:mem:pedidos_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(
                SucursalTable,
                CajaTable,
                UsersTable,
                PlantasTable,
                MesasTable,
                SesionMesaTable,
                PedidoMesaTable,
            )
            seedSucursales()
            seedCajas()
            seedUsuarios()
            seedAreas()
            seedMesas()
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(
                PedidoMesaTable,
                SesionMesaTable,
                MesasTable,
                PlantasTable,
                UsersTable,
                CajaTable,
                SucursalTable,
            )
        }
    }

    // ---------- Crear ----------

    @Test
    fun `crear pendiente inserta lineas PENDIENTE y comanda_secuencia null`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val request = crearRequest(enviarInmediato = false, items = listOf(item(produtoId = 501, cantidad = 2.0)))

            val result = pedidoRepository.crear(database, sesionId, mesaId = 1001, request = request)
            assertTrue(result is PedidoMesaResult.Creado)
            assertNull(result.comandaSecuencia)

            val listado = pedidoRepository.listar(database, sesionId, mesaId = 1001) as PedidoMesaResult.Listado
            assertEquals(1, listado.pedidos.size)
            val linea = listado.pedidos.single()
            assertEquals(EstadoPedidoMesa.PENDIENTE.codigo, linea.estado)
            assertNull(linea.comandaSecuencia)
            assertEquals(501, linea.productoId)
            assertEquals(2.0, linea.itemCantidad)
        }

    @Test
    fun `crear enviar_inmediato marca ENVIADA y asigna comanda_secuencia 1`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val request = crearRequest(enviarInmediato = true, items = listOf(item(produtoId = 501, cantidad = 1.0)))

            val result = pedidoRepository.crear(database, sesionId, mesaId = 1001, request = request)
            assertTrue(result is PedidoMesaResult.Creado)
            assertEquals(1, result.comandaSecuencia)

            val linea = result.pedidos.single()
            assertEquals(EstadoPedidoMesa.ENVIADA.codigo, linea.estado)
            assertEquals(1, linea.comandaSecuencia)
            assertNotNull(linea.fechaEnvio)
            Unit
        }

    @Test
    fun `crear sin items devuelve SinItemsParaCrear`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val result =
                pedidoRepository.crear(
                    database,
                    sesionId = sesionId,
                    mesaId = 1001,
                    request = CrearPedidoMesaRequest(items = emptyList()),
                )
            assertEquals(PedidoMesaResult.SinItemsParaCrear, result)
        }

    @Test
    fun `crear en mesa equivocada devuelve SesionNoPerteneceMesa`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val result =
                pedidoRepository.crear(
                    database,
                    sesionId = sesionId,
                    mesaId = 1002, // Otra mesa.
                    request = crearRequest(items = listOf(item(produtoId = 501))),
                )
            assertEquals(PedidoMesaResult.SesionNoPerteneceMesa, result)
        }

    @Test
    fun `crear en sesion cerrada devuelve SesionNoActiva`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            assertTrue(sesionRepository.cerrar(database, sesionId) is SesionMesaResult.Closed)

            val result =
                pedidoRepository.crear(
                    database,
                    sesionId = sesionId,
                    mesaId = 1001,
                    request = crearRequest(items = listOf(item(produtoId = 501))),
                )
            assertEquals(PedidoMesaResult.SesionNoActiva, result)
        }

    // ---------- Enviar comanda ----------

    @Test
    fun `enviar pasa pendientes a ENVIADA y asigna secuencia 1`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            // Tres items: dos en una llamada, uno en otra.
            pedidoRepository.crear(
                database,
                sesionId,
                1001,
                crearRequest(items = listOf(item(produtoId = 501), item(produtoId = 502))),
            )
            pedidoRepository.crear(database, sesionId, 1001, crearRequest(items = listOf(item(produtoId = 503))))

            val result = pedidoRepository.enviarComanda(database, sesionId, 1001, pedidoIds = emptyList())
            assertTrue(result is PedidoMesaResult.Enviada)
            assertEquals(1, result.comandaSecuencia)
            assertEquals(3, result.pedidos.size)
            assertTrue(result.pedidos.all { it.estado == EstadoPedidoMesa.ENVIADA.codigo })
            assertTrue(result.pedidos.all { it.comandaSecuencia == 1 })
        }

    @Test
    fun `enviar segunda vez asigna secuencia 2 a las nuevas`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            // Comanda 1 enviada al crear.
            pedidoRepository.crear(
                database,
                sesionId,
                1001,
                crearRequest(enviarInmediato = true, items = listOf(item(produtoId = 501))),
            )
            // Pendiente posterior.
            pedidoRepository.crear(database, sesionId, 1001, crearRequest(items = listOf(item(produtoId = 502))))

            val result = pedidoRepository.enviarComanda(database, sesionId, 1001, pedidoIds = emptyList())
            assertTrue(result is PedidoMesaResult.Enviada)
            assertEquals(2, result.comandaSecuencia)
            assertEquals(1, result.pedidos.size)
            assertEquals(502, result.pedidos.single().productoId)
        }

    @Test
    fun `enviar sin pendientes devuelve SinPedidosPendientes`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val result = pedidoRepository.enviarComanda(database, sesionId, 1001, pedidoIds = emptyList())
            assertEquals(PedidoMesaResult.SinPedidosPendientes, result)
        }

    // ---------- Cambiar estado ----------

    @Test
    fun `cambiar estado avanza ENVIADA a EN_PREPARACION, LISTA y ENTREGADA`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val creada =
                pedidoRepository.crear(
                    database,
                    sesionId,
                    1001,
                    crearRequest(enviarInmediato = true, items = listOf(item(produtoId = 501))),
                ) as PedidoMesaResult.Creado
            val pedidoId = creada.pedidos.first().id

            listOf(
                EstadoPedidoMesa.EN_PREPARACION,
                EstadoPedidoMesa.LISTA,
                EstadoPedidoMesa.ENTREGADA,
            ).forEach { destino ->
                val r = pedidoRepository.cambiarEstado(database, sesionId, 1001, pedidoId, destino)
                assertTrue(r is PedidoMesaResult.EstadoActualizado, "Esperaba actualización a $destino, fue $r")
                assertEquals(destino.codigo, r.pedido.estado)
            }
        }

    @Test
    fun `cambiar estado a CANCELADA anula una linea activa`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val creada =
                pedidoRepository.crear(
                    database,
                    sesionId,
                    1001,
                    crearRequest(items = listOf(item(produtoId = 501))),
                ) as PedidoMesaResult.Creado
            val pedidoId = creada.pedidos.first().id

            val r = pedidoRepository.cambiarEstado(database, sesionId, 1001, pedidoId, EstadoPedidoMesa.CANCELADA)
            assertTrue(r is PedidoMesaResult.EstadoActualizado)
            assertEquals(EstadoPedidoMesa.CANCELADA.codigo, r.pedido.estado)
        }

    @Test
    fun `cambiar estado a PENDIENTE estando ENVIADA esta prohibido`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val creada =
                pedidoRepository.crear(
                    database,
                    sesionId,
                    1001,
                    crearRequest(enviarInmediato = true, items = listOf(item(produtoId = 501))),
                ) as PedidoMesaResult.Creado
            val pedidoId = creada.pedidos.first().id

            // No se puede retroceder a PENDIENTE una vez enviado.
            val r = pedidoRepository.cambiarEstado(database, sesionId, 1001, pedidoId, EstadoPedidoMesa.PENDIENTE)
            assertEquals(PedidoMesaResult.EstadoInvalido, r)
        }

    @Test
    fun `cambiar estado de una linea final entrega devuelve EstadoInvalido`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val creada =
                pedidoRepository.crear(
                    database,
                    sesionId,
                    1001,
                    crearRequest(items = listOf(item(produtoId = 501))),
                ) as PedidoMesaResult.Creado
            val pedidoId = creada.pedidos.first().id
            pedidoRepository.cambiarEstado(database, sesionId, 1001, pedidoId, EstadoPedidoMesa.ENTREGADA)

            assertEquals(
                PedidoMesaResult.EstadoInvalido,
                pedidoRepository.cambiarEstado(database, sesionId, 1001, pedidoId, EstadoPedidoMesa.LISTA),
            )
        }

    @Test
    fun `cambiar estado de pedido inexistente devuelve PedidoNoEncontrado`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            assertEquals(
                PedidoMesaResult.PedidoNoEncontrado,
                pedidoRepository.cambiarEstado(database, sesionId, 1001, pedidoId = 9999, EstadoPedidoMesa.CANCELADA),
            )
        }

    // ---------- tieneOperaciones + bloqueo de cierre ----------

    @Test
    fun `tieneOperaciones es false en sesion vacia y true tras crear un pedido pendiente`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            transaction(database) {
                assertFalse(pedidoRepository.tieneOperaciones(sesionId, 1001))
            }
            pedidoRepository.crear(database, sesionId, 1001, crearRequest(items = listOf(item(produtoId = 501))))
            transaction(database) {
                assertTrue(pedidoRepository.tieneOperaciones(sesionId, 1001))
            }
        }

    @Test
    fun `tieneOperaciones es false cuando solo quedan entregadas o canceladas`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val creada =
                pedidoRepository.crear(
                    database,
                    sesionId,
                    1001,
                    crearRequest(items = listOf(item(produtoId = 501))),
                ) as PedidoMesaResult.Creado
            val pedidoId = creada.pedidos.first().id
            pedidoRepository.cambiarEstado(database, sesionId, 1001, pedidoId, EstadoPedidoMesa.CANCELADA)

            transaction(database) {
                assertFalse(pedidoRepository.tieneOperaciones(sesionId, 1001))
            }
        }

    @Test
    fun `cerrar sesion con pedidos pendientes devuelve SesionConOperaciones`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            pedidoRepository.crear(database, sesionId, 1001, crearRequest(items = listOf(item(produtoId = 501))))

            assertEquals(SesionMesaResult.SesionConOperaciones, sesionRepository.cerrar(database, sesionId))
        }

    @Test
    fun `cerrar sesion se permite cuando todos los pedidos estan entregados o cancelados`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            val creada =
                pedidoRepository.crear(
                    database,
                    sesionId,
                    1001,
                    crearRequest(enviarInmediato = true, items = listOf(item(produtoId = 501))),
                ) as PedidoMesaResult.Creado
            val pedido1 = creada.pedidos.first().id
            pedidoRepository.cambiarEstado(database, sesionId, 1001, pedido1, EstadoPedidoMesa.ENTREGADA)

            assertTrue(sesionRepository.cerrar(database, sesionId) is SesionMesaResult.Closed)
        }

    @Test
    fun `cancelar sesion con operaciones tambien se bloquea`() =
        runBlocking {
            val sesionId = abrirSesion(mesaId = 1001)
            pedidoRepository.crear(database, sesionId, 1001, crearRequest(items = listOf(item(produtoId = 501))))

            assertEquals(SesionMesaResult.SesionConOperaciones, sesionRepository.cancelar(database, sesionId))
        }

    // ---------- helpers ----------

    private suspend fun abrirSesion(mesaId: Int): Int {
        val scope =
            AbrirSesionScope(
                cajaId = CAJA_A,
                areaId = 100,
                mesaId = mesaId,
                usuarioId = 10,
                cantidadPersonas = 4,
            )
        val opened = sesionRepository.abrir(database, scope) as SesionMesaResult.Opened
        return opened.sesion.id
    }

    private fun item(
        produtoId: Int,
        cantidad: Double = 1.0,
    ): CrearPedidoMesaItemRequest {
        val precioSinIva = 1.0
        val iva = 0.10
        val totalSinIva = precioSinIva * cantidad
        val totalConIva = totalSinIva + iva * cantidad
        return CrearPedidoMesaItemRequest(
            productoId = produtoId,
            itemAlmacen = 1,
            itemCodigo = "P$produtoId",
            itemDescripcion = "Producto $produtoId",
            itemCantidad = cantidad,
            itemPrecioSinIva = precioSinIva,
            itemDescuento = 0.0,
            itemMontoDescuento = 0.0,
            itemPIva = iva,
            itemTotalSinIva = totalSinIva,
            itemTotalConIva = totalConIva,
            cantidadBulto = 1,
            unidadEmpaque = "UNIDAD",
            notas = null,
            promocionId = null,
            promocionTipo = null,
            promocionDetalleId = null,
        )
    }

    private fun crearRequest(
        items: List<CrearPedidoMesaItemRequest> = listOf(item(produtoId = 1)),
        enviarInmediato: Boolean = false,
    ) = CrearPedidoMesaRequest(
        items = items,
        enviarInmediato = enviarInmediato,
    )

    private fun seedSucursales() {
        SucursalTable.insert {
            it[idSucursal] = 1
            it[codigo] = "S1"
            it[serie] = "1"
            it[sucursal] = "Sucursal A"
        }
    }

    private fun seedCajas() {
        CajaTable.insert {
            it[idCaja] = CAJA_A
            it[codCaja] = CAJA_A
            it[caja] = CAJA_A
            it[idSucursal] = 1
            it[serieCaja] = "1"
        }
    }

    private fun seedUsuarios() {
        UsersTable.insert {
            it[codUsuario] = 10
            it[usuario] = "u10"
            it[clave] = "x"
            it[status] = "1"
        }
    }

    private fun seedAreas() {
        PlantasTable.insert {
            it[PlantasTable.id] = 100
            it[sucursalId] = 1
            it[PlantasTable.nombre] = "Salón principal"
            it[PlantasTable.orden] = 1
            it[activo] = 1
        }
    }

    private fun seedMesas() {
        listOf(1001 to true, 1002 to true, 1003 to false).forEach { (id, activa) ->
            MesasTable.insert {
                it[MesasTable.id] = id
                it[plantaId] = 100
                it[MesasTable.codigo] = "M$id"
                it[MesasTable.nombre] = "Mesa $id"
                it[capacidad] = 4
                it[forma] = "rectangular"
                it[activo] = if (activa) 1 else 0
            }
        }
    }

    private companion object {
        const val CAJA_A = "caja-a"
    }
}
