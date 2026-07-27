package com.amaxonia.pos.domain.model.mesas

/**
 * Forma visual de una mesa. El backend la transporta como `String` libre (lo que el
 * administrativo eligió), así que la conexión con la UI es tolerante: cualquier valor que no
 * se reconozca se trata como [RECTANGULAR] porque es el contenedor por defecto más común.
 *
 * Las claves en minúscula son las que el administrativo guarda hoy. Se normalizan por
 * `compareName` para no depender de mayúsculas ni acentos.
 */
enum class SalonForma {
    RECTANGULAR,
    CUADRADA,
    REDONDA,
    ;

    companion object {
        /**
         * Devuelve el [SalonForma] asociado al crudo del backend, o [RECTANGULAR] si no se
         * reconoce. Nunca lanza: un valor nuevo del administrativo nunca debe romper el plano.
         */
        fun fromRaw(raw: String?): SalonForma {
            if (raw.isNullOrBlank()) return RECTANGULAR
            val normalized = raw.trim().lowercase().replace("á", "a")
            return when (normalized) {
                "rectangular", "rectangulo", "rectángulo" -> RECTANGULAR
                "cuadrada", "cuadrado" -> CUADRADA
                "redonda", "redondo", "circular", "circulo", "círculo", "circ" -> REDONDA
                else -> RECTANGULAR
            }
        }

        /** Etiqueta legible para mostrar junto a la mesa o como tooltip. */
        fun labelOf(raw: String?): String {
            if (raw.isNullOrBlank()) return "Sin forma"
            return raw.trim().replaceFirstChar { it.uppercase() }
        }
    }

    /** Etiqueta corta para UI. */
    val displayName: String
        get() =
            when (this) {
                RECTANGULAR -> "Rectangular"
                CUADRADA -> "Cuadrada"
                REDONDA -> "Redonda"
            }
}
