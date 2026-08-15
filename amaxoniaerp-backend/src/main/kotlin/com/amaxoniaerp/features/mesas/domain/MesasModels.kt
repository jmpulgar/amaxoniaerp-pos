package com.amaxoniaerp.features.mesas.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Área tal como la consume el POS. `planta_id` nunca se expone: la tabla física conserva su
 * nombre, pero el contrato público habla de áreas.
 *
 * Los campos obligatorios del contrato se declaran **sin valor por defecto** a propósito: la
 * aplicación serializa con `encodeDefaults = false`, así que un default haría que el campo
 * desapareciera del JSON justo cuando vale 0 o `true`.
 */
@Serializable
data class AreaResponse(
    val id: Int,
    val nombre: String,
    val descripcion: String? = null,
    val imagen: String? = null,
    val orden: Int,
    val activo: Boolean,
    @SerialName("cantidad_mesas_activas")
    val cantidadMesasActivas: Int,
)

@Serializable
data class MesaResponse(
    val id: Int,
    @SerialName("area_id")
    val areaId: Int,
    val codigo: String? = null,
    val nombre: String,
    val capacidad: Int,
    val forma: String? = null,
    @SerialName("posicion_x")
    val posicionX: Double,
    @SerialName("posicion_y")
    val posicionY: Double,
    val ancho: Double,
    val alto: Double,
    val rotacion: Double,
    val activo: Boolean,
)

/**
 * Dimensiones del lienzo lógico del plano de mesas.
 *
 * Coordenadas de cada mesa se interpretan en este espacio: el origen (0,0) es la esquina
 * superior izquierda, [anchoLienzo] crece hacia la derecha y [altoLienzo] hacia abajo. La
 * rotación de cada mesa se aplica alrededor de su propio centro.
 *
 * Convenio acordado con el administrativo: 2000 x 1200. Se publica en la respuesta para que
 * el POS no tenga que hardcodearlo y para que un cambio de resolución contractual no requiera
 * tocar la app.
 */
@Serializable
data class Lienzo(
    @SerialName("ancho_lienzo")
    val anchoLienzo: Int,
    @SerialName("alto_lienzo")
    val altoLienzo: Int,
)

@Serializable
data class AreasListResponse(
    val success: Boolean,
    @SerialName("sucursal_id")
    val sucursalId: Int,
    val data: List<AreaResponse>,
)

/**
 * Respuesta de `GET /api/pos/areas/{areaId}/mesas`.
 *
 * - [lienzo] describe el espacio lógico del plano (2000 x 1200 por convenio). Va como objeto
 *   anidado para añadirlo sin romper el `AreaResponse` existente y porque geométricamente
 *   pertenece al área, no a cada mesa.
 * - [imagenUrl] es la URL pública del dibujo de fondo del salón si el administrativo la
 *   configuró; `null` si no existe o es blank. El POS la muestra como fondo del plano y, si
 *   falla la carga, pinta únicamente las mesas sobre un fondo neutro.
 * - [data] es la geometría completa de cada mesa activa, ya con posicion/tamaño/rotación.
 */
@Serializable
data class MesasListResponse(
    val success: Boolean,
    @SerialName("area_id")
    val areaId: Int,
    val lienzo: Lienzo,
    @SerialName("imagen_url")
    val imagenUrl: String? = null,
    val data: List<MesaResponse>,
)

/** Sucursal derivada en servidor a partir de la caja; nunca se acepta desde el cliente. */
data class CajaSucursalScope(
    val idCaja: String,
    val sucursalId: Int,
)

/**
 * Plano completo de un área: dimensiones acordadas, imagen de fondo opcional (lo que el
 * administrativo registró en `plantas.imagen`) y la geometría de las mesas activas.
 *
 * Es `null` si el área no existe, está inactiva o no pertenece a la sucursal, igual que el
 * `List<MesaResponse>?` que devolvía antes `listMesas`: responde con 404 idéntico en los tres
 * casos para no filtrar por sondeo qué ids existen en otras sucursales.
 */
data class MesasPlan(
    val lienzo: Lienzo,
    val imagenUrl: String?,
    val mesas: List<MesaResponse>,
)

/**
 * Dimensiones acordadas con el administrativo para el plano de mesas. Se exponen al POS vía
 * [MesasListResponse.lienzo] en lugar de hardcodearlas en el cliente.
 */
object LienzoDefaults {
    const val ANCHO_LIENZO = 2000
    const val ALTO_LIENZO = 1200
}

/** Resultado de validar el acceso a una caja y derivar su sucursal. */
sealed interface CajaScopeResult {
    data class Allowed(
        val scope: CajaSucursalScope,
    ) : CajaScopeResult

    data object CajaNotFound : CajaScopeResult

    data object AccessDenied : CajaScopeResult

    data object SucursalNotAssigned : CajaScopeResult
}

// ============================================================================
// Sesión operativa de mesa
// ============================================================================

/**
 * Estado operativo de una mesa tal como se deriva de la existencia o no de una sesión
 * activa en [SesionMesaTable].
 *
 * - [DISPONIBLE]: no existe sesión activa para la mesa.
 * - [OCUPADA]: existe una sesión activa para la mesa.
 *
 * Estados como CUENTA_SOLICITADA, EN_PREPARACIÓN, RESERVADA o PAGADA quedan fuera de
 * esta fase: la ocupación se modela con un único flag derivado (activo = 1 en la sesión
 * vigente). La columna `estado` de `sesion_mesa` permite añadirlos después sin migrar.
 */
enum class EstadoMesaOperativo {
    DISPONIBLE,
    OCUPADA,
}

/** Códigos administrativos que se persisten en `sesion_mesa.estado`. */
enum class EstadoSesionMesa(
    val codigo: String,
) {
    /** Sesión abierta: la mesa está siendo atendida. */
    ABIERTA("ABIERTA"),

    /** Cuenta solicitada por el operario; offset intermedio OCUPADA -> pago.
     *  Reversible a [ABIERTA] con "cancelar solicitud de cuenta". */
    CUENTA_SOLICITADA("CUENTA_SOLICITADA"),

    /** Sesión cerrada normalmente. */
    CERRADA("CERRADA"),

    /** Sesión cerrada por liquidación total de la cuenta (cierre automático). */
    CERRADA_PAGADA("CERRADA_PAGADA"),

    /** Sesión anulada sin operaciones. */
    CANCELADA("CANCELADA"),
    ;

    val esFinal: Boolean
        get() = this == CERRADA || this == CERRADA_PAGADA || this == CANCELADA

    /** Pedidos/comandas se permiten en ABIERTA o CUENTA_SOLICITADA. */
    val admitePedidos: Boolean
        get() = this == ABIERTA || this == CUENTA_SOLICITADA

    companion object {
        fun fromCodigo(codigo: String): EstadoSesionMesa? = entries.firstOrNull { it.codigo == codigo }
    }
}

/**
 * Vista pública de una sesión de mesa, tal como viaja en el JSON del POS.
 * No expone `usuario_id` tal cual: el nickname del usuario se incluye por separado en la
 * capa de aplicación para que el cliente nunca tenga que resolverlo.
 */
@Serializable
data class SesionMesaResponse(
    val id: Int,
    @SerialName("sucursal_id") val sucursalId: Int,
    @SerialName("caja_id") val cajaId: String,
    @SerialName("area_id") val areaId: Int,
    @SerialName("mesa_id") val mesaId: Int,
    @SerialName("usuario_id") val usuarioId: Int,
    val usuario: String? = null,
    @SerialName("cantidad_personas") val cantidadPersonas: Int,
    val estado: String,
    @SerialName("fecha_apertura") val fechaApertura: String,
    @SerialName("fecha_cierre") val fechaCierre: String? = null,
    val activo: Boolean,
)

/**
 * Estado derivado de una mesa: si hay sesión activa, viene `OCUPADA` con la sesión; si no,
 * viene `DISPONIBLE` sin datos de sesión. [sesion] es `null` exactamente en el segundo caso.
 */
@Serializable
data class MesaEstadoResponse(
    @SerialName("mesa_id") val mesaId: Int,
    val estado: String,
    val sesion: SesionMesaResponse? = null,
)

@Serializable
data class MesasEstadosListResponse(
    val success: Boolean,
    @SerialName("area_id") val areaId: Int,
    val data: List<MesaEstadoResponse>,
)

/** Cuerpo de `POST /api/pos/areas/{areaId}/mesas/{mesaId}/sesiones`. */
@Serializable
data class AbrirSesionRequest(
    @SerialName("cantidad_personas") val cantidadPersonas: Int = 1,
)

@Serializable
data class AbrirSesionResponse(
    val success: Boolean,
    val sesion: SesionMesaResponse,
)

@Serializable
data class SesionActivaResponse(
    val success: Boolean,
    val sesion: SesionMesaResponse? = null,
)

/** Resultado de `POST .../sesiones/{sesionId}/cerrar` y `/cancelar`. */
@Serializable
data class SesionMutacionResponse(
    val success: Boolean,
    val sesion: SesionMesaResponse,
)

/** Resultado interno de las operaciones de sesión que pueden fallar por reglas de negocio. */
sealed interface SesionMesaResult {
    data class Opened(
        val sesion: SesionMesaResponse,
    ) : SesionMesaResult

    data class Closed(
        val sesion: SesionMesaResponse,
    ) : SesionMesaResult

    data class Cancelled(
        val sesion: SesionMesaResponse,
    ) : SesionMesaResult

    data class Found(
        val sesion: SesionMesaResponse?,
    ) : SesionMesaResult

    data class States(
        val estados: List<MesaEstadoResponse>,
    ) : SesionMesaResult

    data object SesionYaAbierta : SesionMesaResult

    /** La sesión tiene operaciones asociadas y no se puede cerrar ni cancelar. */
    data object SesionConOperaciones : SesionMesaResult

    data object SesionNoEncontrada : SesionMesaResult

    /** La sesión ya está cerrada/cancelada. */
    data object SesionYaFinalizada : SesionMesaResult

    data object CantidadPersonasInvalida : SesionMesaResult

    data object AreaNoPerteneceSucursal : SesionMesaResult

    data object MesaNoPerteneceArea : SesionMesaResult

    data object MesaInactiva : SesionMesaResult
}
