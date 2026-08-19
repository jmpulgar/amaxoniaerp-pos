package com.amaxoniaerp.features.mesas.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Máquina de estados de la línea de pedido de mesa (TASK-044): congela las
 * transiciones permitidas que antes vivían inline en `PedidoMesaRepository`.
 */
class EstadoPedidoMesaTest {
    @Test
    fun `avance hacia adelante esta permitido entre estados no finales`() {
        assertTrue(EstadoPedidoMesa.PENDIENTE.puedeTransicionarA(EstadoPedidoMesa.ENVIADA))
        assertTrue(EstadoPedidoMesa.ENVIADA.puedeTransicionarA(EstadoPedidoMesa.EN_PREPARACION))
        assertTrue(EstadoPedidoMesa.EN_PREPARACION.puedeTransicionarA(EstadoPedidoMesa.LISTA))
        assertTrue(EstadoPedidoMesa.LISTA.puedeTransicionarA(EstadoPedidoMesa.ENTREGADA))
    }

    @Test
    fun `cancelar esta permitido desde cualquier estado no final`() {
        EstadoPedidoMesa.entries
            .filterNot { it.esFinal }
            .forEach { origen -> assertTrue(origen.puedeTransicionarA(EstadoPedidoMesa.CANCELADA), "$origen") }
    }

    @Test
    fun `no se retrocede ni se vuelve a PENDIENTE una vez enviada`() {
        assertFalse(EstadoPedidoMesa.ENVIADA.puedeTransicionarA(EstadoPedidoMesa.PENDIENTE))
        assertFalse(EstadoPedidoMesa.LISTA.puedeTransicionarA(EstadoPedidoMesa.ENVIADA))
        assertFalse(EstadoPedidoMesa.LISTA.puedeTransicionarA(EstadoPedidoMesa.PENDIENTE))
    }

    @Test
    fun `los estados finales no se mueven`() {
        assertFalse(EstadoPedidoMesa.ENTREGADA.puedeTransicionarA(EstadoPedidoMesa.CANCELADA))
        assertFalse(EstadoPedidoMesa.CANCELADA.puedeTransicionarA(EstadoPedidoMesa.LISTA))
        assertFalse(EstadoPedidoMesa.ENTREGADA.puedeTransicionarA(EstadoPedidoMesa.LISTA))
    }
}
