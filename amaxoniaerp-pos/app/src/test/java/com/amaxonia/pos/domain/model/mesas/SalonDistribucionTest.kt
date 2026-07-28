package com.amaxonia.pos.domain.model.mesas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura de detección de distribución inválida del plano. Cuando [SalonDistribucion.esValida]
 * devuelve `false`, la UI debe caer al modo lista y nunca intentar dibujar un plano engañoso.
 */
class SalonDistribucionTest {
    private val lienzo = Lienzo(2000, 1200)

    private fun mesa(
        id: Int,
        posicionX: Double = 0.0,
        posicionY: Double = 0.0,
        ancho: Double = 0.0,
        alto: Double = 0.0,
        rotacion: Double = 0.0,
        forma: String = "rectangular",
    ) = Mesa(
        id = id,
        areaId = 1,
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

    @Test
    fun `lista vacia no es valida`() {
        assertFalse(SalonDistribucion.esValida(emptyList(), lienzo))
    }

    @Test
    fun `mesas sin geometria no son validas`() {
        // Ancho/alto a 0: no se configuró geometría administrativamente.
        val mesas =
            listOf(
                mesa(id = 1),
                mesa(id = 2),
            )
        assertFalse(SalonDistribucion.esValida(mesas, lienzo))
    }

    @Test
    fun `todas las mesas apiladas en origen con el mismo tamano no son validas`() {
        val mesas =
            listOf(
                mesa(id = 1, posicionX = 0.0, posicionY = 0.0, ancho = 100.0, alto = 80.0),
                mesa(id = 2, posicionX = 0.0, posicionY = 0.0, ancho = 100.0, alto = 80.0),
                mesa(id = 3, posicionX = 0.0, posicionY = 0.0, ancho = 100.0, alto = 80.0),
            )
        assertFalse(SalonDistribucion.esValida(mesas, lienzo))
    }

    @Test
    fun `mesas en el origen pero de diferente tamano si son validas`() {
        // Aunque estén en origen, los tamaños distintos sugieren un plano real o configurado a mano.
        val mesas =
            listOf(
                mesa(id = 1, posicionX = 0.0, posicionY = 0.0, ancho = 100.0, alto = 80.0),
                mesa(id = 2, posicionX = 0.0, posicionY = 0.0, ancho = 50.0, alto = 40.0),
            )
        assertTrue(SalonDistribucion.esValida(mesas, lienzo))
    }

    @Test
    fun `una sola mesa en origen es valida`() {
        // No hay patrón de apilamiento sospechoso con una mesa sola.
        val mesas = listOf(mesa(id = 1, posicionX = 0.0, posicionY = 0.0, ancho = 100.0, alto = 80.0))
        assertTrue(SalonDistribucion.esValida(mesas, lienzo))
    }

    @Test
    fun `todas las mesas fuera del lienzo no son validas`() {
        val mesas =
            listOf(
                mesa(id = 1, posicionX = 5000.0, posicionY = 5000.0, ancho = 100.0, alto = 80.0),
                mesa(id = 2, posicionX = 6000.0, posicionY = 6000.0, ancho = 100.0, alto = 80.0),
            )
        assertFalse(SalonDistribucion.esValida(mesas, lienzo))
    }

    @Test
    fun `al menos una mesa dentro del lienzo es valida`() {
        // Aunque una se desborde, el plano sigue siendo útil.
        val mesas =
            listOf(
                mesa(id = 1, posicionX = 5000.0, posicionY = 5000.0, ancho = 100.0, alto = 80.0),
                mesa(id = 2, posicionX = 200.0, posicionY = 150.0, ancho = 100.0, alto = 80.0),
            )
        assertTrue(SalonDistribucion.esValida(mesas, lienzo))
    }

    @Test
    fun `distribucion correctamente desparramada es valida`() {
        val mesas =
            listOf(
                mesa(id = 1, posicionX = 100.0, posicionY = 100.0, ancho = 100.0, alto = 80.0),
                mesa(id = 2, posicionX = 400.0, posicionY = 200.0, ancho = 100.0, alto = 80.0),
                mesa(id = 3, posicionX = 700.0, posicionY = 100.0, ancho = 120.0, alto = 120.0, forma = "redonda"),
            )
        assertTrue(SalonDistribucion.esValida(mesas, lienzo))
    }

    @Test
    fun `mezcla de mesas con y sin geometria respeta las que si tienen`() {
        val mesas =
            listOf(
                mesa(id = 1), // sin geometría
                mesa(id = 2, posicionX = 100.0, posicionY = 100.0, ancho = 100.0, alto = 80.0),
            )
        assertTrue(SalonDistribucion.esValida(mesas, lienzo))
    }

    @Test
    fun `lienzo invalido con mesas dentro del default sigue siendo valido`() {
        // Lienzo(0,0) -> cae a 2000x1200. Las mesas dentro de ese default están "dentro".
        val mesas = listOf(mesa(id = 1, posicionX = 100.0, posicionY = 100.0, ancho = 100.0, alto = 80.0))
        assertTrue(SalonDistribucion.esValida(mesas, Lienzo(0, 0)))
    }
}
