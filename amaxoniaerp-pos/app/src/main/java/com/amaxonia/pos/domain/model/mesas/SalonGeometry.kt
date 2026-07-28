package com.amaxonia.pos.domain.model.mesas

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Coordenadas y geometría ya escaladas de una mesa, listas para que el composable las dibuje.
 *
 * Todo está en píxeles del viewport y en el sistema de Compose (origen arriba-izquierda, `y`
 * crece hacia abajo, rotación en grados en sentido horario). El composable solo las lee.
 */
data class MesaGraphics(
    val mesaId: Int,
    /** Esquina superior-izquierda escalada, después de voltear `y` al sistema de Compose. */
    val left: Float,
    val top: Float,
    /** Ancho escalado. */
    val width: Float,
    /** Alto escalado. */
    val height: Float,
    val centroX: Float,
    val centroY: Float,
    /** Rotación en grados, tal y como la pinta Compose (sentido horario). */
    val rotacionGrados: Float,
    val forma: SalonForma,
)

/**
 * Lienzo de trabajo en píxeles del viewport. O sea: el espacio 2000x1200 del administrativo
 * proyectado al tamaño real que ocupa la zona de plano en la pantalla.
 */
data class ViewportCanvas(
    val width: Float,
    val height: Float,
) {
    init {
        require(width > 0f && height > 0f) { "El viewport debe ser positivo" }
    }
}

/**
 * Cálculo puramente numérico de la geometría del plano de mesas.
 *
 * Sin Android, sin Compose: solo matemática. Por eso se puede y se debe testear a fondo aisladamente.
 *
 * Convenios (acordados con el administrativo):
 * - El lienzo lógico mide 2000x1200. [ViewportCanvas] lo reescala usando el menor de los
 *   dos factores para no deformar.
 * - Coordenadas [Mesa.posicionX] / [Mesa.posicionY] son la esquina **superior izquierda**, en
 *   el sistema "pantalla tradicional" con `y` creciendo hacia abajo. Como los dos sistemas
 *   coinciden en dirección, no hay que voltear `y`; el código aplica el escalado directo.
 * - La rotación se aplica alrededor del **centro** de la mesa en el espacio lógico.
 */
object SalonGeometry {
    private const val HALF_TURN_DEGREES = 180f
    private const val ROTATION_EPSILON = 0.01f

    /**
     * Ancho lógico de referencia del administrativo: 2000.
     */
    const val LIENZO_ANCHO_REFERENCIA = 2000f

    /**
     * Alto lógico de referencia del administrativo: 1200.
     */
    const val LIENZO_ALTO_REFERENCIA = 1200f

    /**
     * Versión segura del lienzo lógico para cálculos: si el backend envía valores inválidos
     * se cae a los defaults contractuales.
     */
    fun lienzoLogico(lienzo: Lienzo): Pair<Float, Float> = lienzo.anchoEfectivo.toFloat() to lienzo.altoEfectivo.toFloat()

    /**
     * Factor de escala proporcional entre un lienzo lógico y un [ViewportCanvas].
     *
     * Se calcula como el mínimo de los dos factores para caber sin deformar. Si el lienzo
     * lógico difiere del referencia (p. ej. el backend publicara 4000x2400) el cálculo sigue
     * siendo correcto porque usa el lienzo publicado, no la constante.
     */
    fun escalaProporcional(
        lienzo: Lienzo,
        viewport: ViewportCanvas,
    ): Float {
        val (anchoL, altoL) = lienzoLogico(lienzo)
        if (anchoL <= 0f || altoL <= 0f) return 0f
        return min(viewport.width / anchoL, viewport.height / altoL)
    }

    /**
     * Convierte un [Mesa] a su [MesaGraphics] escalado al viewport.
     *
     * La rotación viaja sin signo (positiva = sentido horario), igual que la usa
     * `Modifier.rotate`. El composable solo necesita rotar alrededor de `centroX,centroY`.
     */
    fun toGraphics(
        mesa: Mesa,
        lienzo: Lienzo,
        viewport: ViewportCanvas,
    ): MesaGraphics {
        val escala = escalaProporcional(lienzo, viewport).coerceAtLeast(0f)
        val width = (mesa.ancho * escala).toFloat()
        val height = (mesa.alto * escala).toFloat()
        val left = (mesa.posicionX * escala).toFloat()
        val top = (mesa.posicionY * escala).toFloat()
        return MesaGraphics(
            mesaId = mesa.id,
            left = left,
            top = top,
            width = width,
            height = height,
            centroX = left + width / 2f,
            centroY = top + height / 2f,
            rotacionGrados = mesa.rotacion.toFloat(),
            forma = SalonForma.fromRaw(mesa.forma),
        )
    }

    /**
     * Resuelve el [Mesa] bajo un punto `(xPx, yPx)` del viewport, teniendo en cuenta la
     * rotación: gira el punto al sistema "no rotado" de la mesa y comprueba la caja axial.
     * Útil para el hit-testing táctil sin recurrir al sistema de Compose durante los tests.
     *
     * Devuelve el primer [Mesa] cuyo rectángulo (rotado) contiene al punto, o `null`.
     */
    fun hitTest(
        mesas: List<Mesa>,
        lienzo: Lienzo,
        viewport: ViewportCanvas,
        xPx: Float,
        yPx: Float,
    ): Mesa? {
        for (mesa in mesas) {
            val g = toGraphics(mesa, lienzo, viewport)
            if (contienePunto(g, xPx, yPx)) return mesa
        }
        return null
    }

    /**
     * Caja axial de la mesa **después** de aplicar la rotación: la mínima caja alineada con
     * los ejes que envuelve el rectángulo rotado. La usan los tests para verificar que la
     * rotación no empuja la mesa fuera del lienzo.
     */
    fun boundingBox(g: MesaGraphics): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        if (abs(g.rotacionGrados % HALF_TURN_DEGREES) < ROTATION_EPSILON) {
            // Sin rotación efectiva: la caja axial es la propia geometría.
            return (g.left to g.top) to (g.left + g.width to g.top + g.height)
        }
        val rad = Math.toRadians(g.rotacionGrados.toDouble())
        val cos = abs(cos(rad)).toFloat()
        val sin = abs(sin(rad)).toFloat()
        val newWidth = g.width * cos + g.height * sin
        val newHeight = g.width * sin + g.height * cos
        val cx = g.centroX
        val cy = g.centroY
        return (cx - newWidth / 2f to cy - newHeight / 2f) to
            (cx + newWidth / 2f to cy + newHeight / 2f)
    }

    private fun contienePunto(
        g: MesaGraphics,
        x: Float,
        y: Float,
    ): Boolean {
        // Trasladamos el punto al sistema centrado en la mesa y deshacemos la rotación.
        val dx = x - g.centroX
        val dy = y - g.centroY
        val rad = Math.toRadians((-g.rotacionGrados).toDouble())
        val cos = cos(rad).toFloat()
        val sin = sin(rad).toFloat()
        val localX = dx * cos - dy * sin
        val localY = dx * sin + dy * cos
        return abs(localX) <= g.width / 2f && abs(localY) <= g.height / 2f
    }
}

/**
 * Variante de [SalonGeometry.hitTest] que tiene en cuenta el zoom y desplazamiento extra
 * aplicados por el usuario al plano. Útil para hit-testing táctil sin acoplar el dominio a
 * Compose.
 *
 * El flujo es:
 * 1. Deshacer [offsetX]/[offsetY] (pan) del punto tocado.
 * 2. Deshacer [extraScale] (zoom) relativo al origen del canvas.
 * 3. El resultado está en espacio base, donde [SalonGeometry.toGraphics] ya aplica escala base.
 */
fun SalonGeometry.hitTestTranslated(
    mesas: List<Mesa>,
    lienzo: Lienzo,
    viewport: ViewportCanvas,
    transform: ViewportTransform,
    point: ViewportPoint,
): Mesa? {
    val safeExtraScale = if (transform.extraScale == 0f) 1f else transform.extraScale
    val localX = (point.x - transform.offsetX) / safeExtraScale
    val localY = (point.y - transform.offsetY) / safeExtraScale
    return SalonGeometry.hitTest(mesas, lienzo, viewport, localX, localY)
}

data class ViewportPoint(
    val x: Float,
    val y: Float,
)

data class ViewportTransform(
    val extraScale: Float,
    val offsetX: Float,
    val offsetY: Float,
)

/**
 * Detección de distribución inválida del plano de mesas de un área.
 *
 * "Inválido" significa que el plano gráfico no se puede presentar de forma útil y se debe caer
 * al modo lista. Casos detectados:
 * 1. No hay ninguna mesa en el área.
 * 2. Toda mesa tiene `ancho <= 0` y `alto <= 0` (no se configuró geometría).
 * 3. Todas las mesas con geometría están en `(0,0)` (apiladas) Y tienen el mismo tamaño,
 *    patrón típico de mesas creadas sin plano.
 * 4. Todas las mesas con geometría están completamente fuera del lienzo lógico.
 *
 * No se considera inválido:
 * - Que una sola mesa esté desbordada: el administrativo puede quererla así y el resto del
 *   plano sigue siendo válido.
 * - Que dos mesas se solapen: el POS es solo lectura y no resuelve conflictos de layout.
 */
object SalonDistribucion {
    private const val UMBRAL_GEOMETRIA = 0.01

    /**
     * `true` si el área tiene una distribución de mesas apta para pintar el plano.
     */
    fun esValida(
        mesas: List<Mesa>,
        lienzo: Lienzo,
    ): Boolean {
        val conGeometria = mesas.filter { it.ancho > UMBRAL_GEOMETRIA && it.alto > UMBRAL_GEOMETRIA }
        return mesas.isNotEmpty() &&
            conGeometria.isNotEmpty() &&
            !estanApiladasEnOrigen(conGeometria) &&
            !todasFueraDelLienzo(conGeometria, lienzo)
    }

    /**
     * Detecta el patrón de mesas nuevas todas en (0,0) con el mismo tamaño. Es el fallback
     * para áreas configuradas sin plano gráfico.
     */
    private fun estanApiladasEnOrigen(mesas: List<Mesa>): Boolean {
        val todasEnOrigen = mesas.all { abs(it.posicionX) < UMBRAL_GEOMETRIA && abs(it.posicionY) < UMBRAL_GEOMETRIA }
        val primera = mesas.first()
        val mismoTamano =
            mesas.all {
                abs(it.ancho - primera.ancho) < UMBRAL_GEOMETRIA &&
                    abs(it.alto - primera.alto) < UMBRAL_GEOMETRIA
            }
        return mesas.size >= 2 && todasEnOrigen && mismoTamano
    }

    /**
     * `true` si ninguna mesa cae siquiera parcialmente dentro del lienzo lógico.
     */
    private fun todasFueraDelLienzo(
        mesas: List<Mesa>,
        lienzo: Lienzo,
    ): Boolean {
        val anchoL = max(lienzo.anchoEfectivo, 0).toFloat()
        val altoL = max(lienzo.altoEfectivo, 0).toFloat()
        if (anchoL <= 0f || altoL <= 0f) return true
        return mesas.none { mesa ->
            val dentroX = mesa.posicionX + mesa.ancho > 0f && mesa.posicionX < anchoL
            val dentroY = mesa.posicionY + mesa.alto > 0f && mesa.posicionY < altoL
            dentroX && dentroY
        }
    }
}
