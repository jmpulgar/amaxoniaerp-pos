package com.amaxonia.pos.domain.model

/**
 * País/región del backend al que se conecta la app.
 * Solo Venezuela y Panamá. La URL base es única (mismo servidor, tenant por header).
 */
data class ServerCountry(
    val code: String,
    val displayName: String,
    val baseUrl: String,
    val flagEmoji: String,
    val schemaType: SchemaType,
)

enum class SchemaType {
    TYPE_A, // Panamá
    TYPE_B, // Venezuela
}

/** Países soportados. La capa data aporta la URL del entorno en runtime. */
object ServerCountries {
    val AVAILABLE: List<ServerCountry> =
        listOf(
            ServerCountry(
                code = "VE",
                displayName = "Venezuela",
                baseUrl = "",
                flagEmoji = "🇻🇪",
                schemaType = SchemaType.TYPE_B,
            ),
            ServerCountry(
                code = "PA",
                displayName = "Panamá",
                baseUrl = "",
                flagEmoji = "🇵🇦",
                schemaType = SchemaType.TYPE_A,
            ),
        )

    fun fromCode(code: String): ServerCountry? = AVAILABLE.find { it.code == code }

    fun getAvailable(): List<ServerCountry> = AVAILABLE
}
