package com.amaxonia.erp.domain.model

data class ServerCountry(
    val code: String,
    val displayName: String,
    val flagEmoji: String,
)

object ServerCountries {
    val PANAMA =
        ServerCountry(
            code = "PA",
            displayName = "Panamá",
            flagEmoji = "🇵🇦",
        )

    val VENEZUELA =
        ServerCountry(
            code = "VE",
            displayName = "Venezuela",
            flagEmoji = "🇻🇪",
        )

    val AVAILABLE: List<ServerCountry> = listOf(PANAMA, VENEZUELA)

    fun fromCode(code: String): ServerCountry? =
        AVAILABLE.find { it.code.equals(code, ignoreCase = true) }
}
