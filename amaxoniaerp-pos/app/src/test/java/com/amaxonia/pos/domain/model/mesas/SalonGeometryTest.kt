package com.amaxonia.pos.domain.model.mesas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests puramente numéricos del cálculo de geometría del plano. Sin Compose ni Android, así
 * cubren directamente la lógica de escalado, coordenadas y rotación.
 */
class SalonGeometryTest {
    @Test
    fun `escala proporcional usa el factor minimo para no deformar`() {
        // Lienzo 2000x1200 en un viewport 1000x600 (mitad en cada eje) -> escala 0.5.
        val escala = SalonGeometry.escalaProporcional(Lienzo(2000, 1200), ViewportCanvas(1000f, 600f))
        assertEquals(0.5f, escala, 1e-3f)
    }

    @Test
    fun `escala proporcional cabiendo en alto cuando es lo mas restrictivo`() {
        // Viewport 2000x300 sobre lienzo 2000x1200: el alto limita -> 300/1200 = 0.25.
        val escala = SalonGeometry.escalaProporcional(Lienzo(2000, 1200), ViewportCanvas(2000f, 300f))
        assertEquals(0.25f, escala, 1e-3f)
    }

    @Test
    fun `escala proporcional con lienzo invalido cae a los defaults y sigue escalando`() {
        // Lienzo(0,0) -> el getter anchoEfectivo/altoEfectivo sustituye 2000x1200 contractuales.
        // Eso significa que la escala se sigue calculando como 1000/2000 = 0.5, no 0.
        val escala = SalonGeometry.escalaProporcional(Lienzo(0, 0), ViewportCanvas(1000f, 600f))
        assertEquals(0.5f, escala, 1e-3f)
    }

    @Test
    fun `lienzo efectivo sustituye defaults cuando el backend envia cero`() {
        // Lienzo(0,0) debe caer a 2000x1200 contractuales vía anchoEfectivo/altoEfectivo.
        assertEquals(2000, Lienzo(0, 0).anchoEfectivo)
        assertEquals(1200, Lienzo(0, 0).altoEfectivo)
        // Y -1 también.
        assertEquals(2000, Lienzo(-1, -1).anchoEfectivo)
        assertEquals(1200, Lienzo(-1, -1).altoEfectivo)
    }

    @Test
    fun `toGraphics escala coordenadas y dimensiones con el factor proporcional`() {
        val mesa =
            Mesa(
                id = 1,
                areaId = 1,
                posicionX = 200.0,
                posicionY = 150.0,
                ancho = 100.0,
                alto = 80.0,
                rotacion = 0.0,
                forma = "rectangular",
            )
        val viewport = ViewportCanvas(1000f, 600f) // escala 0.5 sobre lienzo 2000x1200

        val g = SalonGeometry.toGraphics(mesa, Lienzo(2000, 1200), viewport)

        assertEquals(100f, g.left, 1e-3f) // 200 * 0.5
        assertEquals(75f, g.top, 1e-3f) // 150 * 0.5
        assertEquals(50f, g.width, 1e-3f) // 100 * 0.5
        assertEquals(40f, g.height, 1e-3f) // 80 * 0.5
        assertEquals(125f, g.centroX, 1e-3f) // 100 + 50/2
        assertEquals(95f, g.centroY, 1e-3f) // 75 + 40/2
        assertEquals(0f, g.rotacionGrados, 1e-3f)
        assertEquals(SalonForma.RECTANGULAR, g.forma)
    }

    @Test
    fun `toGraphics reconoce redonda y cuadrada sin distorsionar la geometria`() {
        val viewport = ViewportCanvas(1000f, 600f)
        val redonda =
            Mesa(
                id = 1,
                areaId = 1,
                posicionX = 0.0,
                posicionY = 0.0,
                ancho = 120.0,
                alto = 120.0,
                forma = "redonda",
            )
        val cuadrada = redonda.copy(id = 2, forma = "cuadrado")

        assertEquals(SalonForma.REDONDA, SalonGeometry.toGraphics(redonda, Lienzo(2000, 1200), viewport).forma)
        assertEquals(SalonForma.CUADRADA, SalonGeometry.toGraphics(cuadrada, Lienzo(2000, 1200), viewport).forma)
    }

    @Test
    fun `toGraphics trata forma desconocida como rectangular sin morir`() {
        val mesa = Mesa(id = 1, areaId = 1, ancho = 50.0, alto = 30.0, forma = "octagonal")
        val g = SalonGeometry.toGraphics(mesa, Lienzo(2000, 1200), ViewportCanvas(2000f, 1200f))
        assertEquals(SalonForma.RECTANGULAR, g.forma)
    }

    @Test
    fun `bounding box sin rotacion coincide con la caja de la mesa`() {
        val g =
            MesaGraphics(
                mesaId = 1,
                left = 100f,
                top = 50f,
                width = 80f,
                height = 40f,
                centroX = 140f,
                centroY = 70f,
                rotacionGrados = 0f,
                forma = SalonForma.RECTANGULAR,
            )
        val (min, max) = SalonGeometry.boundingBox(g)
        assertEquals(100f to 50f, min)
        assertEquals(180f to 90f, max)
    }

    @Test
    fun `bounding box con rotacion 90 grados intercambia ancho y alto`() {
        val g =
            MesaGraphics(
                mesaId = 1,
                left = 100f,
                top = 50f,
                width = 80f,
                height = 40f,
                centroX = 140f,
                centroY = 70f,
                rotacionGrados = 90f,
                forma = SalonForma.RECTANGULAR,
            )
        val (min, max) = SalonGeometry.boundingBox(g)
        // Tras 90 grados: ancho efectivo = alto original, alto efectivo = ancho original.
        assertEquals(40f, (max.first - min.first), 1e-3f)
        assertEquals(80f, (max.second - min.second), 1e-3f)
    }

    @Test
    fun `hit test resuelve la mesa bajo un punto cuando no hay rotacion`() {
        val mesa =
            Mesa(
                id = 7,
                areaId = 1,
                posicionX = 200.0,
                posicionY = 150.0,
                ancho = 100.0,
                alto = 80.0,
            )
        val viewport = ViewportCanvas(1000f, 600f) // escala 0.5
        val lienzo = Lienzo(2000, 1200)

        // Centro de la mesa escalado: (125, 95).
        assertEquals(7, SalonGeometry.hitTest(listOf(mesa), lienzo, viewport, 125f, 95f)?.id)
        // Esquina superior izquierda ya dentro.
        assertEquals(7, SalonGeometry.hitTest(listOf(mesa), lienzo, viewport, 101f, 76f)?.id)
        // Fuera un poco: por encima.
        assertNull(SalonGeometry.hitTest(listOf(mesa), lienzo, viewport, 101f, 50f))
    }

    @Test
    fun `hit test respeta la rotacion alrededor del centro`() {
        // Mesa 100x100 en (0,0) rotada 45 grados: centro en (50,50) y el punto (5,5) físico
        // sigue dentro del rombo rotado, mientras que en la versión no rotada estaría justo
        // en la esquina.
        val mesa =
            Mesa(
                id = 7,
                areaId = 1,
                posicionX = 0.0,
                posicionY = 0.0,
                ancho = 100.0,
                alto = 100.0,
                rotacion = 45.0,
            )
        val lienzo = Lienzo(2000, 1200)
        val viewport = ViewportCanvas(2000f, 1200f) // escala 1:1

        // Cerca del centro: dentro seguro.
        assertEquals(7, SalonGeometry.hitTest(listOf(mesa), lienzo, viewport, 50f, 50f)?.id)
        // Esquina física que con 45° ya quedaría FUERA del cuadrado rotado.
        assertNull(SalonGeometry.hitTest(listOf(mesa), lienzo, viewport, 95f, 95f))
    }

    @Test
    fun `hit test devuelve la primera mesa que contiene al punto`() {
        val a = Mesa(id = 1, areaId = 1, posicionX = 0.0, posicionY = 0.0, ancho = 50.0, alto = 50.0)
        val b = Mesa(id = 2, areaId = 1, posicionX = 0.0, posicionY = 0.0, ancho = 50.0, alto = 50.0)
        val lienzo = Lienzo(2000, 1200)
        val viewport = ViewportCanvas(2000f, 1200f)

        assertEquals(1, SalonGeometry.hitTest(listOf(a, b), lienzo, viewport, 10f, 10f)?.id)
    }

    @Test
    fun `hit test con zoom extra y pan deshace la transformacion del gesto`() {
        // Mesa en (0,0) de 100x100, lienzo 1:1, escala base 1. El usuario hace zoom x2 y
        // arrastra +50,-30. Un tap en (150,70) físicos corresponde a (50,50) en espacio base.
        val mesa = Mesa(id = 1, areaId = 1, ancho = 100.0, alto = 100.0)
        val lienzo = Lienzo(2000, 1200)
        val viewport = ViewportCanvas(2000f, 1200f) // escala base = 1

        val hit =
            SalonGeometry.run {
                hitTestTranslated(
                    mesas = listOf(mesa),
                    lienzo = lienzo,
                    viewport = viewport,
                    transform = ViewportTransform(2f, 50f, -30f),
                    point = ViewportPoint(150f, 70f),
                )
            }
        assertEquals(1, hit?.id)
    }

    @Test
    fun `escalado proporcional es estable en una relacion horizontal`() {
        // Lienzo cuadrado y viewport panorámico: la escala la limita el alto.
        val escala = SalonGeometry.escalaProporcional(Lienzo(2000, 2000), ViewportCanvas(4000f, 1000f))
        assertTrue(abs(escala - 0.5f) < 1e-3f)
    }
}
