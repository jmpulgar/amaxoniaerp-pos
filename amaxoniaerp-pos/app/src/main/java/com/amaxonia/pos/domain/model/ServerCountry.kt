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
    val schemaType: SchemaType
)

enum class SchemaType {
    TYPE_A,  // Panamá
    TYPE_B   // Venezuela
}

/** Solo Venezuela y Panamá. URL base única definida en ApiConfig. */
object ServerCountries {

    private fun baseUrl(): String = com.amaxonia.pos.data.remote.ApiConfig.baseUrl

    val AVAILABLE: List<ServerCountry> = listOf(
        ServerCountry(
            code = "VE",
            displayName = "Venezuela",
            baseUrl = baseUrl(),
            flagEmoji = "🇻🇪",
            schemaType = SchemaType.TYPE_B
        ),
        ServerCountry(
            code = "PA",
            displayName = "Panamá",
            baseUrl = baseUrl(),
            flagEmoji = "🇵🇦",
            schemaType = SchemaType.TYPE_A
        )
    )

    fun fromCode(code: String): ServerCountry? = AVAILABLE.find { it.code == code }

    fun getAvailable(): List<ServerCountry> = AVAILABLE
}
